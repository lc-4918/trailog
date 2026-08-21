package fr.lc4918.trailog.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Un point d'interet garde en base, tel qu'il a ete rendu par le service.
 *
 * Ce cache sert deux choses, et la seconde est la vraie raison de son existence :
 * - **epargner le quota** de l'API, en ne redemandant pas une zone revue le lendemain ;
 * - **montrer quelque chose sans reseau**. Une carte hors ligne reste la promesse de l'application, et
 *   une couche allumee qui ne rend rien en montagne, faute de signal, serait une regression pour la seule
 *   fonction qu'on emporte justement la ou il n'y a pas de reseau.
 *
 * En base et non en memoire : c'est le sort d'un cache qu'on veut retrouver au lancement suivant, sur le
 * bord d'un chemin.
 */
@Entity(tableName = "poi_cache")
data class PoiCacheEntity(
    @PrimaryKey val uuid: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val categoryKey: String,
    val city: String?,
    val imageUrl: String?,
    val webUrl: String?,
    val bikeTheme: Boolean,
    /** Horodatage du chargement, pour la peremption (cf. [PoiDao.deleteOlderThan]). */
    val fetchedAt: Long,
    /**
     * Emporte volontairement avec une zone hors ligne, et non croise au fil d'un deplacement de carte.
     *
     * Ce qu'on a demande ne se perime pas au bout d'une semaine comme le reste : une zone emportee pour un
     * voyage de quinze jours se viderait au milieu du sejour, et precisement la ou il n'y a pas de reseau
     * pour la refaire. Le menage epargne donc ces lignes-la (cf. [PoiDao.deleteOlderThan]).
     */
    val pinned: Boolean = false,
)

@Dao
interface PoiDao {
    /**
     * Les points d'interet connus dans une emprise.
     *
     * Un simple encadrement de latitude et de longitude : a l'echelle d'un ecran de carte, il n'y a rien
     * a gagner a un index spatial, et Room n'en propose pas.
     */
    @Query("SELECT * FROM poi_cache WHERE lat BETWEEN :south AND :north AND lon BETWEEN :west AND :east")
    suspend fun inBounds(north: Double, west: Double, south: Double, east: Double): List<PoiCacheEntity>

    /** Remplace ce qu'on connaissait de ces lieux : le service fait foi, et un lieu ferme depuis doit
     *  disparaitre plutot que de survivre dans le cache. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pois: List<PoiCacheEntity>)

    /** Menage des lignes croisees au fil des deplacements. Ce qu'on a emporte exprès y echappe : c'est la
     *  difference entre un cache et une provision (cf. [PoiCacheEntity.pinned]). */
    @Query("DELETE FROM poi_cache WHERE fetchedAt < :before AND pinned = 0")
    suspend fun deleteOlderThan(before: Long)

    /** Les lignes emportees avec une zone hors ligne, comptees pour le dire a l'utilisateur. */
    @Query("SELECT COUNT(*) FROM poi_cache WHERE pinned = 1")
    suspend fun countPinned(): Int

    /**
     * Ce que le cache retient, et ce qu'on a emporte, comptes pour l'ecran des reglages.
     *
     * En flux et non en fonction suspendue : le compte se lit dans un ecran ouvert, et doit tomber a zero
     * sous les yeux de qui vient d'appuyer sur le bouton, sans qu'on ait a le rafraichir a la main.
     */
    @Query("SELECT COUNT(*) FROM poi_cache WHERE pinned = 0")
    fun countCachedFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM poi_cache WHERE pinned = 1")
    fun countPinnedFlow(): Flow<Int>

    /**
     * Vide le cache **sans toucher aux lieux emportes**.
     *
     * La meme frontiere que le menage hebdomadaire (cf. [deleteOlderThan]) : un cache se refait tout seul
     * a la premiere zone survolee avec du reseau, une provision non. Effacer les deux d'un meme bouton
     * viderait une zone hors ligne preparee pour partir, sans moyen de la refaire sur place.
     */
    @Query("DELETE FROM poi_cache WHERE pinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM poi_cache")
    suspend fun clear()
}
