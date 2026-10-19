package fr.lc4918.trailog.watch.vector

/**
 * Une tuile vectorielle au format MVT (Mapbox Vector Tile, protobuf), lue sans bibliotheque : les tuiles PLAN.IGN de la
 * Geoplateforme arrivent ainsi, et le telephone les dessine lui-meme dans les couleurs de la montre.
 *
 * Seul ce que le dessin utilise est lu : couches, entites (point, ligne, polygone), attributs, geometrie en coordonnees de
 * tuile (de 0 a [Layer.extent], 4096 le plus souvent ; l'axe y descend).
 */
class VectorTile(val layers: Map<String, Layer>) {

    class Layer(val name: String, val extent: Int, val features: List<Feature>)

    enum class GeometryType { UNKNOWN, POINT, LINE, POLYGON }

    /**
     * Une entite. [parts] : un point par partie pour un point, la suite des sommets pour une ligne, un anneau (ferme) par
     * partie pour un polygone (les trous sont des anneaux de plus : remplir en pair/impair les ecarte).
     */
    class Feature(val type: GeometryType, val properties: Map<String, Any?>, val parts: List<List<Point>>) {
        fun text(key: String): String? = properties[key] as? String
    }

    data class Point(val x: Int, val y: Int)

    companion object {
        private const val LAYER_FIELD = 3
        private const val DEFAULT_EXTENT = 4096

        fun decode(bytes: ByteArray): VectorTile {
            val layers = LinkedHashMap<String, Layer>()
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                val tag = reader.readTag()
                if (tag.field == LAYER_FIELD && tag.wire == ProtoReader.LENGTH_DELIMITED) {
                    val layer = decodeLayer(reader.readBytes())
                    layers[layer.name] = layer
                } else reader.skip(tag.wire)
            }
            return VectorTile(layers)
        }

        private fun decodeLayer(bytes: ByteArray): Layer {
            var name = ""
            var extent = DEFAULT_EXTENT
            val keys = ArrayList<String>()
            val values = ArrayList<Any?>()
            val rawFeatures = ArrayList<ByteArray>()
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                val tag = reader.readTag()
                when (tag.field) {
                    1 -> name = String(reader.readBytes(), Charsets.UTF_8)
                    2 -> rawFeatures.add(reader.readBytes())
                    3 -> keys.add(String(reader.readBytes(), Charsets.UTF_8))
                    4 -> values.add(decodeValue(reader.readBytes()))
                    5 -> extent = reader.readVarint().toInt()
                    else -> reader.skip(tag.wire)
                }
            }
            return Layer(name, extent, rawFeatures.map { decodeFeature(it, keys, values) })
        }

        private fun decodeValue(bytes: ByteArray): Any? {
            var value: Any? = null
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                val tag = reader.readTag()
                value = when (tag.field) {
                    1 -> String(reader.readBytes(), Charsets.UTF_8)
                    2 -> java.lang.Float.intBitsToFloat(reader.readFixed32())
                    3 -> java.lang.Double.longBitsToDouble(reader.readFixed64())
                    4, 5 -> reader.readVarint()
                    6 -> ProtoReader.zigZag(reader.readVarint())
                    7 -> reader.readVarint() != 0L
                    else -> { reader.skip(tag.wire); value }
                }
            }
            return value
        }

        private fun decodeFeature(bytes: ByteArray, keys: List<String>, values: List<Any?>): Feature {
            var geometryType = GeometryType.UNKNOWN
            var tags = LongArray(0)
            var commands = LongArray(0)
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                val tag = reader.readTag()
                when (tag.field) {
                    2 -> tags = readPackedVarints(reader.readBytes())
                    3 -> geometryType = GeometryType.entries.getOrElse(reader.readVarint().toInt()) { GeometryType.UNKNOWN }
                    4 -> commands = readPackedVarints(reader.readBytes())
                    else -> reader.skip(tag.wire)
                }
            }
            val properties = LinkedHashMap<String, Any?>()
            for (index in 0 until tags.size / 2) {
                val key = keys.getOrNull(tags[index * 2].toInt()) ?: continue
                properties[key] = values.getOrNull(tags[index * 2 + 1].toInt())
            }
            return Feature(geometryType, properties, decodeGeometry(geometryType, commands))
        }

        private fun readPackedVarints(bytes: ByteArray): LongArray {
            val reader = ProtoReader(bytes)
            val out = ArrayList<Long>()
            while (reader.hasMore()) out.add(reader.readVarint())
            return out.toLongArray()
        }

        /** Les commandes MoveTo (1), LineTo (2), ClosePath (7) en parties de points. */
        private fun decodeGeometry(type: GeometryType, commands: LongArray): List<List<Point>> {
            val parts = ArrayList<List<Point>>()
            var current: MutableList<Point>? = null
            var x = 0
            var y = 0
            var index = 0
            while (index < commands.size) {
                val header = commands[index++]
                val command = (header and 7).toInt()
                val count = (header shr 3).toInt()
                when (command) {
                    1, 2 -> repeat(count) {
                        x += ProtoReader.zigZag(commands[index++]).toInt()
                        y += ProtoReader.zigZag(commands[index++]).toInt()
                        if (type == GeometryType.POINT) parts.add(listOf(Point(x, y)))
                        else if (command == 1) { current = mutableListOf(Point(x, y)); parts.add(current!!) }
                        else current?.add(Point(x, y))
                    }
                    7 -> current?.let { ring -> ring.add(ring.first()) }
                }
            }
            return parts
        }
    }
}

/** Lecture minimale du format protobuf : varints, champs a longueur prefixee, entiers fixes. */
internal class ProtoReader(private val bytes: ByteArray) {
    private var position = 0

    class Tag(val field: Int, val wire: Int)

    fun hasMore() = position < bytes.size

    fun readTag(): Tag { val raw = readVarint().toInt(); return Tag(raw shr 3, raw and 7) }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = bytes[position++].toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        return bytes.copyOfRange(position, position + length).also { position += length }
    }

    fun readFixed32(): Int {
        var value = 0
        for (shift in 0 until 4) value = value or ((bytes[position++].toInt() and 0xFF) shl (8 * shift))
        return value
    }

    fun readFixed64(): Long {
        var value = 0L
        for (shift in 0 until 8) value = value or ((bytes[position++].toLong() and 0xFF) shl (8 * shift))
        return value
    }

    fun skip(wire: Int) {
        when (wire) {
            VARINT -> readVarint()
            FIXED64 -> position += 8
            LENGTH_DELIMITED -> readBytes()
            FIXED32 -> position += 4
            else -> throw IllegalArgumentException("type de champ protobuf inconnu : $wire")
        }
    }

    companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LENGTH_DELIMITED = 2
        const val FIXED32 = 5

        fun zigZag(value: Long): Long = (value ushr 1) xor -(value and 1)
    }
}
