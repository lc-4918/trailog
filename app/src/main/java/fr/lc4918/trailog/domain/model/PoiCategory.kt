package fr.lc4918.trailog.domain.model

/**
 * Les quatre groupes de points d'intérêt, tels que les affiche le planificateur de France Vélo Tourisme.
 *
 * Quatre et non les 384 classes que publie DATAtourisme : le groupe est ce qui se lit d'un coup d'oeil sur
 * la carte - une couleur - et ce qui se replie dans les réglages. Les clés sont celles de FVT, gardées
 * telles quelles pour que la correspondance avec la source reste vérifiable à l'oeil.
 */
enum class PoiGroup(val key: String) {
    LODGING("hebergements"),
    FOOD("restos-bars"),
    LEISURE("loisirs"),
    PRACTICAL("pratique"),
}

/**
 * Une catégorie de point d'intérêt : ce que l'utilisateur coche dans les réglages, et ce que porte le
 * marqueur sur la carte.
 *
 * **Le fossé que cette table franchit.** DATAtourisme ne connaît pas ces catégories-là : son thésaurus
 * `PointOfInterestClass` compte **384 classes**, du `Dolmen` au `Cybercafé`, et un même lieu en porte
 * plusieurs à la fois - un hôtel-restaurant est `Hotel`, `Restaurant`, `Accommodation` et
 * `LodgingBusiness`. Les 27 catégories ci-dessous sont celles de France Vélo Tourisme, et chacune désigne
 * le jeu de classes qui la remplit ([classes]).
 *
 * Ce jeu sert deux fois, et c'est pourquoi il vit ici plutôt que dans le client : il **compose la requête**
 * (`filters=type[in]=...`, pour ne pas rapatrier le catalogue entier et le trier ensuite) et il **relit la
 * réponse**, chaque POI arrivant avec sa liste de classes qu'il faut ramener à une catégorie.
 *
 * Une classe peut nourrir deux catégories - `Hotel` et `Restaurant` pour l'hôtel-restaurant ci-dessus - et
 * c'est voulu : le POI apparaît alors sous la première catégorie cochée qui le reconnaît (cf. [of]).
 *
 * **Deux sources, deux tables.** [classes] désigne DATAtourisme ; [osm] désigne les étiquettes
 * OpenStreetMap qui remplissent la même catégorie, pour ce que la base française ne couvre pas - le monde
 * hors de France, et en France les services du terrain qu'elle ignore (cf. `Overpass`). Chaque sélecteur
 * s'écrit `clé=valeur`, et les rares conditions conjointes séparent leurs paires par une virgule :
 * `tourism=information,information=office` est un office de tourisme, quand `tourism=information` seul
 * désigne aussi bien un panneau au bord d'un chemin.
 *
 * Une catégorie peut n'avoir aucune étiquette OSM, et c'est un choix : "hébergements insolites" ou
 * "villages de caractère" sont des jugements touristiques, qu'OSM ne porte nulle part. Mieux vaut une
 * catégorie vide hors de France qu'un marqueur qui promet autre chose que ce qu'il montre.
 */
enum class PoiCategory(
    val key: String,
    val group: PoiGroup,
    val classes: Set<String>,
    val osm: Set<String> = emptySet(),
) {

    // ---------- Hébergements ----------

    CAMPINGS("campings-et-aires-de-camping-car", PoiGroup.LODGING,
        setOf("CampingAndCaravanning", "Camping", "CamperVanArea", "FarmCamping", "NaturalCampingArea"),
        setOf("tourism=camp_site", "tourism=caravan_site")),
    GUESTHOUSES("chambres-d-hotes", PoiGroup.LODGING,
        setOf("Guesthouse", "TableHoteGuesthouse"),
        setOf("tourism=guest_house")),
    HOTELS("hotels", PoiGroup.LODGING,
        setOf("Hotel", "HotelRestaurant", "HotelTrade"),
        setOf("tourism=hotel", "tourism=motel")),
    RENTALS("gites-et-locations-de-meubles", PoiGroup.LODGING,
        setOf("SelfCateringAccommodation", "RentalAccommodation", "LeisureChalet"),
        setOf("tourism=apartment", "tourism=chalet")),
    STOPOVER("gites-etape", PoiGroup.LODGING,
        setOf("StopOverOrGroupLodge"),
        setOf("tourism=alpine_hut", "tourism=wilderness_hut")),
    UNUSUAL("hebergements-insolites", PoiGroup.LODGING,
        setOf("Hut", "TreeHouse", "Yurt", "Tipi", "Bubble", "HouseBoat", "Bungatoile")),
    COLLECTIVE("hebergements-collectifs", PoiGroup.LODGING,
        setOf("CollectiveAccommodation", "CollectiveHostel", "YouthHostelAndInternationalCenter",
            "GroupLodging", "HolidayCentre"),
        setOf("tourism=hostel")),
    RESORTS("residences-de-tourisme", PoiGroup.LODGING,
        setOf("HolidayResort", "ResidentialLeisurePark")),
    HOLIDAY_VILLAGES("villages-vacances", PoiGroup.LODGING,
        setOf("ClubOrHolidayVillage")),

    // ---------- Restaurants et bars ----------

    BARS("bars", PoiGroup.FOOD,
        setOf("BarOrPub", "BistroOrWineBar", "CafeOrTeahouse"),
        setOf("amenity=bar", "amenity=pub", "amenity=cafe")),
    RESTAURANTS("restaurants", PoiGroup.FOOD,
        setOf("Restaurant", "GourmetRestaurant", "BrasserieOrTavern", "FarmhouseInn",
            "MountainRestaurant", "SelfServiceCafeteria", "FastFoodRestaurant", "StreetFood",
            "BoatRestaurant"),
        setOf("amenity=restaurant", "amenity=fast_food")),

    // ---------- Loisirs ----------

    CULTURAL_SITES("sites-culturels-touristiques", PoiGroup.LEISURE,
        setOf("CulturalSite", "Museum", "Castle", "CastleAndPrestigeMansion", "ReligiousSite",
            "ArcheologicalSite", "ParkAndGarden", "RemarkableBuilding", "InterpretationCentre"),
        setOf("tourism=museum", "historic=castle", "historic=monument", "historic=archaeological_site",
            "historic=ruins", "leisure=garden")),
    ACTIVITIES("activites", PoiGroup.LEISURE,
        setOf("SportsAndLeisurePlace", "LeisureSportActivityProvider", "ActivityProvider",
            "LeisureComplex", "ThemePark", "AdventurePark", "ZooAnimalPark"),
        setOf("tourism=theme_park", "tourism=zoo", "leisure=sports_centre", "leisure=water_park")),
    SWIMMING("lieu-baignade", PoiGroup.LEISURE,
        setOf("Beach", "SwimmingPool", "BeachClub"),
        setOf("natural=beach", "leisure=swimming_area", "leisure=swimming_pool")),
    HERITAGE_VILLAGES("village-caractere", PoiGroup.LEISURE,
        setOf("CityHeritage")),
    MARKETS("marches", PoiGroup.LEISURE,
        setOf("Market", "CoveredMarket"),
        setOf("amenity=marketplace")),
    TASTING("degustation", PoiGroup.LEISURE,
        setOf("Tasting", "TastingProvider", "Cellar", "Producer", "LocalProductsShop"),
        setOf("shop=wine", "craft=winery", "shop=farm")),
    WELLNESS("bien-etre", PoiGroup.LEISURE,
        setOf("BalneotherapyCentre", "ThalassotherapyCentre", "Spa", "SpaResort", "Hammam",
            "FitnessCenter"),
        setOf("leisure=fitness_centre", "leisure=sauna", "amenity=public_bath")),

    // ---------- Pratique ----------

    BIKE_SHOPS("loueurs-reparateurs-velos", PoiGroup.PRACTICAL,
        setOf("BikeStationOrDepot", "EquipmentRentalShop", "EquipmentRepairShop", "GarageOrAirPump"),
        setOf("shop=bicycle", "amenity=bicycle_repair_station", "amenity=bicycle_rental")),
    STATIONS("gares", PoiGroup.PRACTICAL,
        setOf("TrainStation", "BusStation"),
        setOf("railway=station", "amenity=bus_station")),
    TOURIST_OFFICES("office-de-tourisme", PoiGroup.PRACTICAL,
        setOf("LocalTouristOffice", "TourismCentre", "TouristInformationCenter"),
        setOf("tourism=information,information=office")),
    PICNIC("aires-de-pique-nique", PoiGroup.PRACTICAL,
        setOf("PicnicArea", "RestStop"),
        setOf("tourism=picnic_site", "leisure=picnic_table")),
    SERVICE_AREAS("aire_de_servies", PoiGroup.PRACTICAL,
        setOf("ServiceArea", "RVServiceArea"),
        setOf("amenity=sanitary_dump_station", "highway=rest_area")),
    CHARGING("borne-de-recharge", PoiGroup.PRACTICAL,
        setOf("ElectricBycicleChargingPoint", "ElectricVehicleChargingPoint"),
        setOf("amenity=charging_station")),
    WATER("point-eau", PoiGroup.PRACTICAL,
        setOf("WaterSource", "Fountain"),
        setOf("amenity=drinking_water", "man_made=water_tap", "amenity=water_point")),
    TOILETS("toilet", PoiGroup.PRACTICAL,
        setOf("PublicLavatories"),
        setOf("amenity=toilets")),
    CANOE("location-canoe", PoiGroup.PRACTICAL,
        setOf("NauticalCentre", "LaunchingRamp"),
        setOf("leisure=slipway"));

    companion object {
        /** Les catégories d'un groupe, dans l'ordre de déclaration - celui des réglages. */
        fun of(group: PoiGroup): List<PoiCategory> = entries.filter { it.group == group }

        /**
         * L'ordre dans lequel on cherche la catégorie d'un lieu qui en porte plusieurs : le groupe
         * **pratique** d'abord, le reste dans l'ordre de déclaration.
         *
         * **Pourquoi la priorité, et pourquoi celle-là.** Un lieu de DATAtourisme porte couramment huit
         * classes, et son ontologie attache les classes d'hébergement avec une générosité déroutante : des
         * toilettes publiques y sont `PublicLavatories`, mais aussi `CampingAndCaravanning`, `CamperVanArea`,
         * `Accommodation` et `LodgingBusiness`. Prise dans l'ordre de déclaration, la recherche rendait
         * "Campings et aires de camping-car" pour un WC - **57 des 250 toilettes** relevées en France
         * entière, soit près d'une sur quatre.
         *
         * La priorité est bornée au seul groupe pratique parce que c'est ce que les mesures autorisent :
         * sur 250 lieux de chaque sorte, **aucun** musée, camping ou restaurant ne porte de classe pratique,
         * si bien que ce groupe ne peut voler personne. Une priorité plus large, elle, ferait des dégâts :
         * **117 hôtels sur 250** portent une classe de restauration, et faire passer la restauration devant
         * l'hébergement afficherait la moitié des hôtels en restaurants.
         *
         * L'hôtel-restaurant garde donc sa résolution d'avant - `Hotel` l'emporte, première de
         * l'énumération - et c'est bien ce qu'on veut d'un lieu qui est vraiment les deux.
         */
        private val ORDRE_DE_RESOLUTION: List<PoiCategory> by lazy {
            entries.filter { it.group == PoiGroup.PRACTICAL } +
                entries.filterNot { it.group == PoiGroup.PRACTICAL }
        }

        /**
         * Rang d'une catégorie dans [ORDRE_DE_RESOLUTION].
         *
         * Sert là où deux réponses désignent le même lieu sous deux catégories - un hôtel-restaurant rendu
         * par la requête des hébergements et par celle de la restauration : on tranche par le même ordre
         * que partout ailleurs, plutôt que par l'ordre d'arrivée, qui ne se reproduit jamais deux fois.
         */
        fun resolutionRank(category: PoiCategory): Int = ORDRE_DE_RESOLUTION.indexOf(category)

        /**
         * La catégorie d'un POI d'après les classes qu'il porte, ou null si aucune ne nous parle.
         *
         * [retenues] borne la recherche aux catégories cochées : un hôtel-restaurant porte `Hotel` ET
         * `Restaurant`, et doit s'afficher sous celle que l'utilisateur a demandée. À défaut d'indice, c'est
         * [ORDRE_DE_RESOLUTION] qui tranche - un ordre arbitraire mais stable et documenté, préférable à un
         * marqueur qui changerait de pictogramme d'un chargement à l'autre.
         *
         * Null plutôt qu'une catégorie fourre-tout : le service rend parfois des classes que personne n'a
         * demandées, et un marqueur sans catégorie n'a rien à faire sur la carte.
         */
        fun of(classes: Collection<String>, retenues: Set<PoiCategory> = entries.toSet()): PoiCategory? =
            ORDRE_DE_RESOLUTION.firstOrNull { it in retenues && it.classes.any { c -> c in classes } }

        /**
         * La catégorie d'un objet OpenStreetMap d'après ses étiquettes, ou null si aucune ne nous parle.
         *
         * Même règle que pour DATAtourisme ([of]), [ORDRE_DE_RESOLUTION] compris : un lieu peut être à la
         * fois `tourism=hotel` et `amenity=restaurant`, et doit s'afficher sous celle qu'on a demandée.
         *
         * Un sélecteur à plusieurs paires exige que TOUTES soient portées : c'est ce qui distingue l'office
         * de tourisme du panneau d'information.
         */
        fun ofOsm(tags: Map<String, String>, retenues: Set<PoiCategory> = entries.toSet()): PoiCategory? =
            ORDRE_DE_RESOLUTION.firstOrNull { cat ->
                cat in retenues && cat.osm.any { selecteur ->
                    selecteur.split(',').all { paire ->
                        tags[paire.substringBefore('=')] == paire.substringAfter('=')
                    }
                }
            }

        /** Relit une clé enregistrée. Null si elle est inconnue - catégorie retirée depuis, ou faute. */
        fun byKey(key: String?): PoiCategory? = entries.firstOrNull { it.key == key }
    }
}
