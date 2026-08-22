package fr.lc4918.trailog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A quelle categorie revient un lieu qui en porte plusieurs.
 *
 * La question n'a rien de theorique : DATAtourisme attache huit classes a un lieu ordinaire, et son
 * ontologie est genereuse au point de declarer des toilettes publiques `LodgingBusiness`. Ce qui se decide
 * ici est le pictogramme, la couleur et le libelle du marqueur - tout ce que l'utilisateur lit.
 */
class PoiCategoryTest {

    /**
     * Le cas releve sur le terrain (Souillac, 46) : des toilettes publiques s'affichaient en "Campings et
     * aires de camping-car". Leurs huit classes reelles, telles que le service les rend.
     */
    private val toilettesDeSouillac = listOf(
        "LodgingBusiness", "PublicLavatories", "Accommodation", "CampingAndCaravanning",
        "PointOfInterest", "CamperVanArea", "PlaceOfInterest", "ConvenientService",
    )

    @Test fun `des toilettes portant des classes d'hebergement restent des toilettes`() {
        assertEquals(PoiCategory.TOILETS, PoiCategory.of(toilettesDeSouillac))
    }

    /** Meme cas pour une aire de pique-nique, qui porte les memes classes de camping-car. */
    @Test fun `une aire de pique-nique aussi`() {
        val classes = listOf("PointOfInterest", "Accommodation", "CamperVanArea", "PicnicArea",
            "CampingAndCaravanning", "LodgingBusiness")
        assertEquals(PoiCategory.PICNIC, PoiCategory.of(classes))
    }

    /**
     * L'autre moitie de la regle, et la raison pour laquelle la priorite ne va pas plus loin : un hotel
     * reste un hotel. 117 hotels sur 250 portent une classe de restauration ; les faire passer en
     * restaurants viderait la categorie que l'on cherche quand on cherche un lit.
     */
    @Test fun `un hotel-restaurant reste un hotel`() {
        assertEquals(PoiCategory.HOTELS, PoiCategory.of(listOf("Hotel", "Restaurant", "HotelRestaurant")))
    }

    /** Un vrai camping n'est pas emporte par la priorite : il ne porte aucune classe pratique. */
    @Test fun `un camping reste un camping`() {
        assertEquals(PoiCategory.CAMPINGS,
            PoiCategory.of(listOf("Camping", "CampingAndCaravanning", "Accommodation")))
    }

    /**
     * Ce qu'un lieu EST ne depend pas de ce qu'on a coche : decocher les toilettes ne les ramene pas en
     * campings, elle les fait disparaitre.
     *
     * La regle etait l'inverse, et c'etait une promesse fausse : sous le seul filtre "Restaurants", le
     * centre d'Albi rendait six marqueurs qui etaient six hotels.
     */
    @Test fun `un lieu garde sa categorie quel que soit le filtre`() {
        val sansToilettes = PoiCategory.entries.toSet() - PoiCategory.TOILETS
        assertEquals(PoiCategory.TOILETS, PoiCategory.of(toilettesDeSouillac))
        assertNull(PoiCategory.visibleDans(PoiCategory.of(toilettesDeSouillac), sansToilettes))
    }

    /** Le cas d'Albi : sous le seul filtre "Restaurants", un hotel-restaurant ne s'affiche pas. */
    @Test fun `un hotel-restaurant ne passe pas sous le filtre restauration`() {
        val hotelRestaurant = listOf("Hotel", "Restaurant", "HotelRestaurant")
        assertNull(PoiCategory.visibleDans(PoiCategory.of(hotelRestaurant), setOf(PoiCategory.RESTAURANTS)))
        assertEquals(PoiCategory.HOTELS,
            PoiCategory.visibleDans(PoiCategory.of(hotelRestaurant), setOf(PoiCategory.HOTELS)))
    }

    /** Et un vrai restaurant de quartier, lui, passe : il ne porte aucune classe d'hebergement. */
    @Test fun `un restaurant de quartier passe sous le filtre restauration`() {
        assertEquals(PoiCategory.RESTAURANTS,
            PoiCategory.visibleDans(PoiCategory.of(listOf("Restaurant", "FoodEstablishment")),
                setOf(PoiCategory.RESTAURANTS)))
        assertEquals(PoiCategory.BARS,
            PoiCategory.visibleDans(PoiCategory.ofOsm(mapOf("amenity" to "cafe")), setOf(PoiCategory.BARS)))
    }

    /** Meme regle cote OpenStreetMap : l'hotel-restaurant y est un hotel, et sort du filtre restauration. */
    @Test fun `un hotel-restaurant OSM ne passe pas non plus`() {
        val tags = mapOf("tourism" to "hotel", "amenity" to "restaurant")
        assertNull(PoiCategory.visibleDans(PoiCategory.ofOsm(tags), setOf(PoiCategory.RESTAURANTS)))
    }

    @Test fun `une classe inconnue ne donne aucune categorie`() {
        assertNull(PoiCategory.of(listOf("Dolmen", "PointOfInterest")))
    }

    // ---------- OpenStreetMap ----------

    /** Meme ordre de resolution des deux cotes : un objet OSM qui serait a la fois toilettes et aire de
     *  camping-car se lit comme des toilettes. */
    @Test fun `l'ordre vaut aussi pour OpenStreetMap`() {
        val tags = mapOf("amenity" to "toilets", "tourism" to "caravan_site")
        assertEquals(PoiCategory.TOILETS, PoiCategory.ofOsm(tags))
    }

    @Test fun `un hotel OSM avec restaurant reste un hotel`() {
        assertEquals(PoiCategory.HOTELS,
            PoiCategory.ofOsm(mapOf("tourism" to "hotel", "amenity" to "restaurant")))
    }

    /** Un selecteur a deux paires les exige toutes les deux : un panneau d'information au bord d'un chemin
     *  n'est pas un office de tourisme. */
    @Test fun `un panneau d'information n'est pas un office de tourisme`() {
        assertNull(PoiCategory.ofOsm(mapOf("tourism" to "information", "information" to "board")))
        assertEquals(PoiCategory.TOURIST_OFFICES,
            PoiCategory.ofOsm(mapOf("tourism" to "information", "information" to "office")))
    }
}
