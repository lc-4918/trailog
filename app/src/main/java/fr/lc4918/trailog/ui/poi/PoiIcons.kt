package fr.lc4918.trailog.ui.poi

import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.PoiCategory

/**
 * Le pictogramme d'une catégorie de point d'intérêt : une tente pour un camping, un panier pour un marché.
 *
 * Des **Material Symbols** repris tels quels du dépôt de Google (licence Apache 2.0), et non des dessins
 * maison : ce sont les pictogrammes que l'utilisateur voit déjà partout ailleurs sur son téléphone, et un
 * lit dessiné ici ne serait ni plus lisible ni plus juste. Seul leur repère change à la conversion - le
 * `viewBox` de Material part de -960 en Y, d'où la translation dans chaque fichier.
 *
 * Une catégorie, un pictogramme, sans exception : c'est ce qui permet de lire un marqueur sans l'ouvrir.
 * Deux catégories peuvent partager un dessin quand rien ne les distingue à cette taille - résidences de
 * tourisme et hébergements collectifs sont deux immeubles - mais aucune n'est laissée sans.
 */
fun poiIcon(category: PoiCategory): Int = when (category) {
    PoiCategory.CAMPINGS -> R.drawable.ic_poi_camping
    PoiCategory.GUESTHOUSES -> R.drawable.ic_poi_bed
    PoiCategory.HOTELS -> R.drawable.ic_poi_hotel
    PoiCategory.RENTALS -> R.drawable.ic_poi_cottage
    PoiCategory.STOPOVER -> R.drawable.ic_poi_night_shelter
    PoiCategory.UNUSUAL -> R.drawable.ic_poi_cabin
    PoiCategory.COLLECTIVE -> R.drawable.ic_poi_apartment
    PoiCategory.RESORTS -> R.drawable.ic_poi_apartment
    PoiCategory.HOLIDAY_VILLAGES -> R.drawable.ic_poi_holiday_village
    PoiCategory.BARS -> R.drawable.ic_poi_local_bar
    PoiCategory.RESTAURANTS -> R.drawable.ic_poi_restaurant
    PoiCategory.CULTURAL_SITES -> R.drawable.ic_poi_museum
    PoiCategory.ACTIVITIES -> R.drawable.ic_poi_attractions
    PoiCategory.SWIMMING -> R.drawable.ic_poi_pool
    PoiCategory.HERITAGE_VILLAGES -> R.drawable.ic_poi_location_city
    PoiCategory.MARKETS -> R.drawable.ic_poi_shopping_basket
    PoiCategory.TASTING -> R.drawable.ic_poi_wine_bar
    PoiCategory.WELLNESS -> R.drawable.ic_poi_spa
    PoiCategory.BIKE_SHOPS -> R.drawable.ic_poi_pedal_bike
    PoiCategory.STATIONS -> R.drawable.ic_poi_train
    PoiCategory.TOURIST_OFFICES -> R.drawable.ic_poi_info
    PoiCategory.PICNIC -> R.drawable.ic_poi_table_restaurant
    PoiCategory.SERVICE_AREAS -> R.drawable.ic_poi_local_gas_station
    PoiCategory.CHARGING -> R.drawable.ic_poi_ev_station
    PoiCategory.WATER -> R.drawable.ic_poi_water_drop
    PoiCategory.TOILETS -> R.drawable.ic_poi_wc
    PoiCategory.CANOE -> R.drawable.ic_poi_kayaking
}

/**
 * Libellé traduit d'une catégorie, tel qu'il s'affiche dans l'infobulle et dans les réglages.
 *
 * Dans les ressources et non dans l'énumération, comme les disciplines d'itinéraire : le domaine porte des
 * clés stables, enregistrées en base ; l'écran porte des mots, qui changent avec la langue.
 */
@androidx.compose.runtime.Composable
fun poiCategoryLabel(category: PoiCategory): String =
    androidx.compose.ui.res.stringResource(poiCategoryLabelRes(category))

/** La ressource elle-meme, pour qui n'est pas un composable : un lieu sans nom prend celui de sa
 *  categorie, et ce repli sert aussi hors de l'affichage (historique du planificateur). */
fun poiCategoryLabelRes(category: PoiCategory): Int =
    when (category) {
        PoiCategory.CAMPINGS -> R.string.poi_cat_campings
        PoiCategory.GUESTHOUSES -> R.string.poi_cat_guesthouses
        PoiCategory.HOTELS -> R.string.poi_cat_hotels
        PoiCategory.RENTALS -> R.string.poi_cat_rentals
        PoiCategory.STOPOVER -> R.string.poi_cat_stopover
        PoiCategory.UNUSUAL -> R.string.poi_cat_unusual
        PoiCategory.COLLECTIVE -> R.string.poi_cat_collective
        PoiCategory.RESORTS -> R.string.poi_cat_resorts
        PoiCategory.HOLIDAY_VILLAGES -> R.string.poi_cat_holiday_villages
        PoiCategory.BARS -> R.string.poi_cat_bars
        PoiCategory.RESTAURANTS -> R.string.poi_cat_restaurants
        PoiCategory.CULTURAL_SITES -> R.string.poi_cat_cultural_sites
        PoiCategory.ACTIVITIES -> R.string.poi_cat_activities
        PoiCategory.SWIMMING -> R.string.poi_cat_swimming
        PoiCategory.HERITAGE_VILLAGES -> R.string.poi_cat_heritage_villages
        PoiCategory.MARKETS -> R.string.poi_cat_markets
        PoiCategory.TASTING -> R.string.poi_cat_tasting
        PoiCategory.WELLNESS -> R.string.poi_cat_wellness
        PoiCategory.BIKE_SHOPS -> R.string.poi_cat_bike_shops
        PoiCategory.STATIONS -> R.string.poi_cat_stations
        PoiCategory.TOURIST_OFFICES -> R.string.poi_cat_tourist_offices
        PoiCategory.PICNIC -> R.string.poi_cat_picnic
        PoiCategory.SERVICE_AREAS -> R.string.poi_cat_service_areas
        PoiCategory.CHARGING -> R.string.poi_cat_charging
        PoiCategory.WATER -> R.string.poi_cat_water
        PoiCategory.TOILETS -> R.string.poi_cat_toilets
        PoiCategory.CANOE -> R.string.poi_cat_canoe
    }
