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
 */
enum class PoiCategory(val key: String, val group: PoiGroup, val classes: Set<String>) {

    // ---------- Hébergements ----------

    CAMPINGS("campings-et-aires-de-camping-car", PoiGroup.LODGING,
        setOf("CampingAndCaravanning", "Camping", "CamperVanArea", "FarmCamping", "NaturalCampingArea")),
    GUESTHOUSES("chambres-d-hotes", PoiGroup.LODGING,
        setOf("Guesthouse", "TableHoteGuesthouse")),
    HOTELS("hotels", PoiGroup.LODGING,
        setOf("Hotel", "HotelRestaurant", "HotelTrade")),
    RENTALS("gites-et-locations-de-meubles", PoiGroup.LODGING,
        setOf("SelfCateringAccommodation", "RentalAccommodation", "LeisureChalet")),
    STOPOVER("gites-etape", PoiGroup.LODGING,
        setOf("StopOverOrGroupLodge")),
    UNUSUAL("hebergements-insolites", PoiGroup.LODGING,
        setOf("Hut", "TreeHouse", "Yurt", "Tipi", "Bubble", "HouseBoat", "Bungatoile")),
    COLLECTIVE("hebergements-collectifs", PoiGroup.LODGING,
        setOf("CollectiveAccommodation", "CollectiveHostel", "YouthHostelAndInternationalCenter",
            "GroupLodging", "HolidayCentre")),
    RESORTS("residences-de-tourisme", PoiGroup.LODGING,
        setOf("HolidayResort", "ResidentialLeisurePark")),
    HOLIDAY_VILLAGES("villages-vacances", PoiGroup.LODGING,
        setOf("ClubOrHolidayVillage")),

    // ---------- Restaurants et bars ----------

    BARS("bars", PoiGroup.FOOD,
        setOf("BarOrPub", "BistroOrWineBar", "CafeOrTeahouse")),
    RESTAURANTS("restaurants", PoiGroup.FOOD,
        setOf("Restaurant", "GourmetRestaurant", "BrasserieOrTavern", "FarmhouseInn",
            "MountainRestaurant", "SelfServiceCafeteria", "FastFoodRestaurant", "StreetFood",
            "BoatRestaurant")),

    // ---------- Loisirs ----------

    CULTURAL_SITES("sites-culturels-touristiques", PoiGroup.LEISURE,
        setOf("CulturalSite", "Museum", "Castle", "CastleAndPrestigeMansion", "ReligiousSite",
            "ArcheologicalSite", "ParkAndGarden", "RemarkableBuilding", "InterpretationCentre")),
    ACTIVITIES("activites", PoiGroup.LEISURE,
        setOf("SportsAndLeisurePlace", "LeisureSportActivityProvider", "ActivityProvider",
            "LeisureComplex", "ThemePark", "AdventurePark", "ZooAnimalPark")),
    SWIMMING("lieu-baignade", PoiGroup.LEISURE,
        setOf("Beach", "SwimmingPool", "BeachClub")),
    HERITAGE_VILLAGES("village-caractere", PoiGroup.LEISURE,
        setOf("CityHeritage")),
    MARKETS("marches", PoiGroup.LEISURE,
        setOf("Market", "CoveredMarket")),
    TASTING("degustation", PoiGroup.LEISURE,
        setOf("Tasting", "TastingProvider", "Cellar", "Producer", "LocalProductsShop")),
    WELLNESS("bien-etre", PoiGroup.LEISURE,
        setOf("BalneotherapyCentre", "ThalassotherapyCentre", "Spa", "SpaResort", "Hammam",
            "FitnessCenter")),

    // ---------- Pratique ----------

    BIKE_SHOPS("loueurs-reparateurs-velos", PoiGroup.PRACTICAL,
        setOf("BikeStationOrDepot", "EquipmentRentalShop", "EquipmentRepairShop", "GarageOrAirPump")),
    STATIONS("gares", PoiGroup.PRACTICAL,
        setOf("TrainStation", "BusStation")),
    TOURIST_OFFICES("office-de-tourisme", PoiGroup.PRACTICAL,
        setOf("LocalTouristOffice", "TourismCentre", "TouristInformationCenter")),
    PICNIC("aires-de-pique-nique", PoiGroup.PRACTICAL,
        setOf("PicnicArea", "RestStop")),
    SERVICE_AREAS("aire_de_servies", PoiGroup.PRACTICAL,
        setOf("ServiceArea", "RVServiceArea")),
    CHARGING("borne-de-recharge", PoiGroup.PRACTICAL,
        setOf("ElectricBycicleChargingPoint", "ElectricVehicleChargingPoint")),
    WATER("point-eau", PoiGroup.PRACTICAL,
        setOf("WaterSource", "Fountain")),
    TOILETS("toilet", PoiGroup.PRACTICAL,
        setOf("PublicLavatories")),
    CANOE("location-canoe", PoiGroup.PRACTICAL,
        setOf("NauticalCentre", "LaunchingRamp"));

    companion object {
        /** Les catégories d'un groupe, dans l'ordre de déclaration - celui des réglages. */
        fun of(group: PoiGroup): List<PoiCategory> = entries.filter { it.group == group }

        /**
         * La catégorie d'un POI d'après les classes qu'il porte, ou null si aucune ne nous parle.
         *
         * [retenues] borne la recherche aux catégories cochées : un hôtel-restaurant porte `Hotel` ET
         * `Restaurant`, et doit s'afficher sous celle que l'utilisateur a demandée. À défaut d'indice, la
         * première de l'énumération l'emporte - un ordre arbitraire mais stable, préférable à un marqueur
         * qui changerait de pictogramme d'un chargement à l'autre.
         *
         * Null plutôt qu'une catégorie fourre-tout : le service rend parfois des classes que personne n'a
         * demandées, et un marqueur sans catégorie n'a rien à faire sur la carte.
         */
        fun of(classes: Collection<String>, retenues: Set<PoiCategory> = entries.toSet()): PoiCategory? =
            entries.firstOrNull { it in retenues && it.classes.any { c -> c in classes } }

        /** Relit une clé enregistrée. Null si elle est inconnue - catégorie retirée depuis, ou faute. */
        fun byKey(key: String?): PoiCategory? = entries.firstOrNull { it.key == key }
    }
}
