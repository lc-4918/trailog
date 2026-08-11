package fr.lc4918.trailog.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Un dossier de l'application dans l'archive : le prefixe sous lequel il y figure, et ou il vit. */
class BackupDir(val entry: String, val dir: File)

/**
 * Nom propose pour une sauvegarde : `trailog-2026-08-11.zip`.
 *
 * Date en tete a l'envers (annee, mois, jour) pour que l'ordre alphabetique du gestionnaire de fichiers
 * soit l'ordre chronologique - c'est la seule facon de retrouver la derniere sans lire les dates.
 */
object BackupFileName {
    fun of(millis: Long): String {
        val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return "trailog-%04d-%02d-%02d.zip".format(d.year, d.monthValue, d.dayOfMonth)
    }
}

/**
 * Sauvegarde et restauration de tout ce que l'application garde localement.
 *
 * C'est la contrepartie du parti pris de l'application : aucun compte, aucune synchronisation, donc aucun
 * filet. Un telephone perdu ou change emportait jusqu'ici la base, les geometries et les photos de
 * waypoints - c'est-a-dire tout. Une archive unique, que l'utilisateur range ou il veut, ferme ce trou
 * sans rien concede au fonctionnement local.
 *
 * **Les fonds hors-ligne n'y sont pas.** Un seul departement telecharge pese plus que toutes les traces
 * d'une vie, ils se retelechargent en un geste, et ils vivent souvent dans un dossier choisi par
 * l'utilisateur, hors de l'application. Les y mettre ferait des archives de plusieurs gigaoctets que rien
 * ne saurait plus deplacer.
 *
 * **La restauration se fait en deux temps** : tout est d'abord depose a cote, et seule une archive lue
 * jusqu'au bout remplace ce qui est en place. Une archive tronquee ou etrangere laisse donc l'application
 * exactement dans l'etat ou elle etait - c'est la seule facon d'eviter qu'une restauration ratee ne
 * detruise ce qu'elle devait sauver.
 *
 * Sans dependance Android : verifiable sans emulateur, ce qui est la moindre des choses pour du code dont
 * la faute se paie en donnees perdues.
 */
object BackupArchive {

    /** Entete de l'archive. Sa presence distingue une sauvegarde de l'application de n'importe quel zip :
     *  sans elle, on deverserait le contenu d'une archive quelconque par-dessus les donnees. */
    const val MANIFEST = "trailog-backup"

    /** Version du format. Une archive d'une version inconnue - donc future - est refusee plutot que lue de
     *  travers : mieux vaut dire "cette sauvegarde vient d'une version plus recente" que restaurer a moitie. */
    const val FORMAT = 1

    /** La base, sous un nom fixe : c'est le seul fichier de l'archive qui ne soit pas dans un dossier. */
    const val DB_ENTRY = "base.db"

    /** Ecrit l'archive : l'entete, la base, puis chaque dossier declare. */
    fun create(out: OutputStream, db: File, dirs: List<BackupDir>, createdAt: Long) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write("""{"format":$FORMAT,"createdAt":$createdAt}""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (db.exists()) {
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                db.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            dirs.forEach { d ->
                d.dir.listFiles()?.filter { it.isFile }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("${d.entry}/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Ce qu'une lecture d'archive peut donner. */
    enum class Result { OK, NOT_A_BACKUP, UNSUPPORTED_FORMAT }

    /**
     * Restaure l'archive : [staging] recoit tout, puis remplace la base et les dossiers.
     *
     * [staging] est vide au debut comme a la fin - c'est un espace de travail, pas un cache.
     */
    fun restore(input: InputStream, db: File, dirs: List<BackupDir>, staging: File): Result {
        staging.deleteRecursively(); staging.mkdirs()
        try {
            var manifest = false
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (!e.isDirectory) {
                        if (name == MANIFEST) {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            val version = Regex(""""format"\s*:\s*(\d+)""").find(text)
                                ?.groupValues?.get(1)?.toIntOrNull()
                            if (version == null) return Result.NOT_A_BACKUP
                            if (version > FORMAT) return Result.UNSUPPORTED_FORMAT
                            manifest = true
                        } else {
                            val target = safeTarget(staging, name)
                            if (target != null) {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    e = zip.nextEntry
                }
            }
            if (!manifest) return Result.NOT_A_BACKUP

            // A partir d'ici seulement, on touche aux donnees en place.
            val stagedDb = File(staging, DB_ENTRY)
            if (stagedDb.exists()) {
                // Les journaux de la base d'AVANT decrivent des pages qui n'existent plus dans celle qu'on
                // vient de poser : les laisser corromprait la base restauree des la premiere ouverture.
                File(db.parentFile, db.name + "-wal").delete()
                File(db.parentFile, db.name + "-shm").delete()
                db.delete()
                stagedDb.copyTo(db, overwrite = true)
            }
            dirs.forEach { d ->
                val staged = File(staging, d.entry)
                // Un dossier absent de l'archive VIDE son homologue : la sauvegarde decrit un etat complet,
                // pas un ajout. Sans cela, une couche supprimee avant la sauvegarde reviendrait apres.
                d.dir.deleteRecursively(); d.dir.mkdirs()
                staged.listFiles()?.filter { it.isFile }?.forEach { f ->
                    f.copyTo(File(d.dir, f.name), overwrite = true)
                }
            }
            return Result.OK
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Le fichier ou deposer une entree, ou null si son nom sort du dossier de travail.
     *
     * Une entree nommee `../../databases/autre.db` ferait ecrire une archive n'importe ou dans l'espace de
     * l'application. C'est une faiblesse connue des lecteurs de zip, et une archive est un fichier qui
     * vient de l'exterieur : on verifie que la destination reste bien SOUS le dossier de travail.
     */
    internal fun safeTarget(root: File, entryName: String): File? {
        if (entryName.startsWith("/") || entryName.contains("..")) return null
        val target = File(root, entryName)
        val rootPath = root.canonicalPath + File.separator
        return if (target.canonicalPath.startsWith(rootPath)) target else null
    }
}
