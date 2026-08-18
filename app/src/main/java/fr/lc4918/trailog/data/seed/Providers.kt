package fr.lc4918.trailog.data.seed

import fr.lc4918.trailog.data.db.ProviderEntity

/**
 * Cle de l'API DATAtourisme, en clair et assume, comme les cles de fonds de carte de ce fichier.
 *
 * Ici et non dans le client : c'est le seul endroit du depot ou vivent les cles en clair, et les regrouper
 * vaut mieux que d'en semer une par service. Le depot etant public, elles sont lisibles. C'est un choix,
 * pas un oubli : ce sont des cles personnelles à quota, revocables, et rien de ce qu'elles ouvrent n'est
 * privé. Une cle cachee derriere local.properties avec un defaut de secours committe ne cacherait rien du
 * tout, elle donnerait seulement l'illusion de le faire.
 */
const val DATATOURISME_API_KEY = "e9db2b7f-a884-4c5d-9d84-eb7b1cc0bbe8"

/**
 * Fonds de carte par défaut. Repris de tefeciste/2024 js/map.js + OSM (défaut),
 * OpenFreeMap, MapTiler, overlays Waymarked Trails et DEM pour le relief.
 * Tous éditables ensuite (URL + clé) dans les réglages.
 * {KEY} est remplacé par apiKey ; {s} est étendu selon subdomains.
 *
 * Les fonds d'un pays portent le nom de leur couche d'origine, sans le pays devant : le drapeau le dit
 * déjà, et le repeter volait la moitie de la ligne du gestionnaire ("France - IGN Scan").
 *
 * Seuls huit sont cochés d'entrée (OSM, Mapbox Outdoors, les trois Google, IGN Scan, IGN MTN, relief) : le Basemap Control
 * ne montre que les fonds activés, une liste de trente entrées y serait illisible. Les autres restent
 * semés, donc activables d'un toggle dans les réglages, sans avoir à ressaisir leur URL. Ce choix pilote
 * aussi la remise à zéro des réglages (cf. SettingsViewModel.resetAllSettings).
 * L'AF3V fait exception à la règle "décoché = invisible" : il est la couche de premier plan du composite
 * semé par [Composites], et un composite affiche ses deux couches sans regarder leur "enabled"
 * (cf. MainViewModel.buildStyle).
 */
object Providers {
    fun defaults(): List<ProviderEntity> {
        var o = 0
        fun n() = o++
        return listOf(
            // --- Monde ---
            ProviderEntity("osm", "OpenStreetMap", "Monde", "XYZ",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                maxZoom = 19, attribution = "", sortOrder = n()),
            ProviderEntity("mapbox_outdoors", "Mapbox Outdoors", "Monde", "XYZ",
                "https://api.mapbox.com/styles/v1/mapbox/outdoors-v10/tiles/256/{z}/{x}/{y}@2x?access_token={KEY}",
                apiKey = "pk.eyJ1IjoiZHVuY2FuZ3JhaGFtIiwiYSI6IlJJcWdFczQifQ.9HUpTV1es8IjaGAf_s64VQ", maxZoom = 21, sortOrder = n()),
            ProviderEntity("google_street", "Google Street", "Monde", "XYZ",
                "https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}",
                subdomains = "0,1,2,3", maxZoom = 20, sortOrder = n()),
            ProviderEntity("google_sat", "Google Satellite", "Monde", "XYZ",
                "https://mt{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}",
                subdomains = "0,1,2,3", maxZoom = 20, sortOrder = n()),
            ProviderEntity("google_relief", "Google Relief", "Monde", "XYZ",
                "https://mt{s}.google.com/vt/lyrs=p&x={x}&y={y}&z={z}",
                subdomains = "0,1,2,3", maxZoom = 20, sortOrder = n()),
            ProviderEntity("thunder_cycle", "OSM Cycle (Thunderforest)", "Monde", "XYZ",
                "https://tile.thunderforest.com/cycle/{z}/{x}/{y}.png?apikey={KEY}",
                apiKey = "", maxZoom = 18, enabled = false, sortOrder = n()),
            ProviderEntity("thunder_outdoors", "Thunderforest Outdoors", "Monde", "XYZ",
                "https://tile.thunderforest.com/outdoors/{z}/{x}/{y}.png?apikey={KEY}",
                apiKey = "", maxZoom = 20, enabled = false, sortOrder = n()),
            ProviderEntity("openfreemap", "OpenFreeMap Liberty", "Monde", "VECTOR",
                "https://tiles.openfreemap.org/styles/liberty", enabled = false, sortOrder = n()),
            ProviderEntity("maptiler_outdoor", "MapTiler Outdoor", "Monde", "VECTOR",
                "https://api.maptiler.com/maps/outdoor-v2/style.json?key={KEY}",
                apiKey = "", enabled = false, sortOrder = n()),
            ProviderEntity("freemap_outdoor", "Freemap Outdoor", "Monde", "XYZ",
                "https://outdoor.tiles.freemap.sk/{z}/{x}/{y}", enabled = false, sortOrder = n()),

            // --- Overlays (transparents) ---
            ProviderEntity("way_mtb", "Waymarked Trails VTT", "Overlays", "XYZ",
                "https://tile.waymarkedtrails.org/mtb/{z}/{x}/{y}.png",
                transparent = true, maxZoom = 18, enabled = false, sortOrder = n()),
            ProviderEntity("way_cycle", "Waymarked Trails Cycle", "Overlays", "XYZ",
                "https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png",
                transparent = true, maxZoom = 18, enabled = false, sortOrder = n()),
            // Trois couches en une requete : le serveur les compose et renvoie une seule image. Elles se
            // relaient par seuil d'echelle, que QGIS deduit de BBOX et WIDTH : voie_cyclable (synthetique)
            // ne rend qu'au-dela du 1:2 000 000, soit jusqu'au zoom 8 ; segment_cyclable (detaille) et
            // poi_travaux prennent le relais en deca, a partir du zoom 9. Demander la seule voie_cyclable,
            // comme le fait l'URL publique de l'AF3V, donnerait donc un fond vide des qu'on zoome.
            // STYLES vide = style par defaut de chaque couche (WMS exige sinon autant d'entrees que LAYERS).
            ProviderEntity("af3v", "Af3v Voies cyclables", "Overlays", "WMS",
                "https://sig.af3v.org/index.php/lizmap/service/?repository=rep1&project=veloroutes" +
                    "&LAYERS=voie_cyclable,segment_cyclable,poi_travaux&STYLES=&VERSION=1.3.0" +
                    "&EXCEPTIONS=application/vnd.ogc.se_inimage&FORMAT=image/png&DPI=96&TRANSPARENT=TRUE" +
                    "&SERVICE=WMS&REQUEST=GetMap&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                transparent = true, minZoom = 0, maxZoom = 20, legendAsset = "legends/af3v.png",
                enabled = false, sortOrder = n()),

            // --- Relief (DEM -> hillshade) ---
            // Coche, contrairement aux autres fonds decoches : son "enabled" ne dit que sa presence dans le
            // gestionnaire, l'ombrage lui-meme s'allume d'un tap (cf. SettingsEntity.hillshadeOn). Decoche,
            // le relief serait introuvable pour qui ne pense pas a le chercher dans les reglages.
            //
            // Declare entre les fonds mondiaux et les fonds nationaux, et pose la dans le gestionnaire :
            // il couvre le monde comme les premiers, sans etre un fond de plan de plus. Viennent ensuite
            // les .mbtiles (cf. [MbtilesSortOrder]) puis les composites (cf. [CompositeSortOrder]), semes
            // au-dela de tout ce que ce fichier numerote.
            ProviderEntity("dem_terrarium", "Relief (DEM)", "Relief", "DEM",
                "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png",
                tileSize = 256, maxZoom = 15, sortOrder = n()),

            // --- Pays ---
            ProviderEntity("ign_fr", "Scan 25", "Pays", "WMTS",
                "https://data.geopf.fr/private/wmts?LAYER=GEOGRAPHICALGRIDSYSTEMS.MAPS&apikey={KEY}&EXCEPTIONS=text/xml&FORMAT=image/jpeg&SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&STYLE=normal&TILEMATRIXSET=PM&TILEMATRIX={z}&TILECOL={x}&TILEROW={y}",
                apiKey = "ign_scan_ws", maxZoom = 17, sortOrder = n()),
            ProviderEntity("ign_es", "MTN Raster", "Pays", "WMS",
                "https://www.ign.es/wms-inspire/mapa-raster?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=mtn_rasterizado&STYLES=&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                maxZoom = 20, sortOrder = n()),
            ProviderEntity("hu", "Turistautak", "Pays", "XYZ",
                "https://terkep.turistautak.hu/tiles/turistautak-domborzattal/{z}/{x}/{y}.png",
                maxZoom = 17, enabled = false, sortOrder = n()),
            ProviderEntity("sk", "Freemap", "Pays", "XYZ",
                "https://tiles.freemap.sk/T/{z}/{x}/{y}.png", maxZoom = 20, enabled = false, sortOrder = n()),
            ProviderEntity("at", "geolandbasemap", "Pays", "XYZ",
                "https://mapsneu.wien.gv.at/basemap/geolandbasemap/normal/google3857/{z}/{y}/{x}.png",
                subdomains = "1,2,3,4", maxZoom = 19, enabled = false, sortOrder = n()),
            ProviderEntity("no", "Toporaster", "Pays", "XYZ",
                //"https://opencache.statkart.no/gatekeeper/gk/gk.open_gmaps?layers=toporaster3&zoom={z}&x={x}&y={y}",
                "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png",
                maxZoom = 17, enabled = false, sortOrder = n()),
            ProviderEntity("be", "Topo", "Pays", "XYZ",
                "https://cartoweb.wmts.ngi.be/1.0.0/topo/default/3857/{z}/{y}/{x}.png", maxZoom = 18, enabled = false, sortOrder = n()),
            // Le seul point d'entrée qui serve topowebb sans clé est le cache de minkarta, en WMTS KVP.
            // Les deux autres sont morts : /open/topowebb-ccby/.../token/{KEY}/ répond 401 (jeton exigé,
            // l'offre ccby ayant migré derrière le portail développeur), et l'URL RESTful annoncée par le
            // GetCapabilities (maps.lantmateriet.se/topowebb/v1.1/wmts/...) répond 401 elle aussi.
            // Variante atténuée disponible au même endroit : LAYER=topowebb_nedtonad.
            ProviderEntity("se", "Topowebb", "Pays", "WMTS",
                "https://minkarta.lantmateriet.se/map/topowebbcache?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3857&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=image/png",
                maxZoom = 18, enabled = false, sortOrder = n()),
            ProviderEntity("hr", "TK25", "Pays", "WMS",
                "https://geoportal.dgu.hr/services/tk/wms?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=TK25&STYLES=&FORMAT=image/jpeg&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                maxZoom = 18, enabled = false, sortOrder = n()),
            ProviderEntity("ch", "Pixelkarte-farbe", "Pays", "WMTS",
                "https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/3857/{z}/{x}/{y}.jpeg", maxZoom = 20, enabled = false, sortOrder = n()),
            // GLOBAL_WEBMERCATOR et non DE_EPSG_3857_ADV : la matrice ADV n'a que 14 niveaux, à des échelles
            // qui ne suivent pas la numérotation de zoom XYZ, d'où des tuiles blanches à toute autre échelle
            // que la sienne. Variante en gris disponible : de_basemapde_web_raster_grau.
            ProviderEntity("de", "Raster farbe", "Pays", "WMTS",
                "https://sgx.geodatenzentrum.de/wmts_basemapde/tile/1.0.0/de_basemapde_web_raster_farbe/default/GLOBAL_WEBMERCATOR/{z}/{y}/{x}.png",
                maxZoom = 19, enabled = false, sortOrder = n()),
            // Jeu de matrices WGS84_Pseudo-Mercator (niveaux 0 à 18) et non ETRS-TM35FIN : ce dernier est
            // en EPSG:3067, projection que MapLibre ne sait pas afficher. La clé est obligatoire, le service
            // répond 401 sans elle. Autres couches au même endroit : taustakartta (fond atténué),
            // selkokartta (simplifiée), ortokuva (orthophoto, en .jpg).
            ProviderEntity("fi", "Maastokartta", "Pays", "WMTS",
                "https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts/1.0.0/maastokartta/default/WGS84_Pseudo-Mercator/{z}/{y}/{x}.png?api-key={KEY}",
                apiKey = "5af3ad22-4ac1-4f7d-a6ef-a63baa971bc5", maxZoom = 18, enabled = false, sortOrder = n()),
            // En WMS et non en WMTS : le WMTS de GURS (gwc-si-gurs-dts) ne publie ses matrices qu'en
            // EPSG:3794 (D96/TM slovène), que MapLibre ne sait pas afficher. Le WMS, lui, annonce
            // EPSG:3857 et reprojette à la volée. maxZoom 16 : au-delà, cette carte au 1:50 000 n'a plus
            // de détail à donner, autant laisser MapLibre agrandir la dernière tuile.
            ProviderEntity("si", "DTK50", "Pays", "WMS",
                "https://ipi.eprostor.gov.si/wms-si-gurs-dts/wms?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=SI.GURS.DK:DTK50&STYLES=&FORMAT=image/png&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                maxZoom = 16, enabled = false, sortOrder = n()),
            // Cache de tuiles ArcGIS deja en 3857 (LOD 0 a 19, origine web mercator standard, 256 px) : rien
            // a reprojeter, contrairement aux services ZTM/ZTM25 et ZTM/ZTM50 dont le cache est en EPSG:5514
            // (S-JTSK Krovak) et qui ne sortent du 3857 que via leur facade WMS, plus lente et qui renvoie
            // une image blanche environ une fois sur sept. ZTM_WM couvre toute la serie ZTM, l'echelle
            // suivant le zoom ; a zoom egal il rend exactement la meme image que ZTM50 en WMS.
            ProviderEntity("cz", "ZTM", "Pays", "XYZ",
                "https://ags.cuzk.cz/arcgis1/rest/services/ZTM_WM/MapServer/tile/{z}/{y}/{x}",
                maxZoom = 19, enabled = false, sortOrder = n()),
            // Outdoor est le seul style topographique d'OS publie en 3857. Le style Leisure, celui qui
            // porte le Landranger 1:50 000 et l'Explorer 1:25 000, n'existe qu'en Leisure_27700 : matrice
            // British National Grid, que MapLibre ne sait pas afficher, et sans facade WMS pour reprojeter.
            // minZoom 7 : la matrice 3857 d'OS commence la, en dessous le service repond 400.
            // maxZoom 16 : au-dela les tuiles sont en donnees Premium et le plan gratuit repond 403.
            ProviderEntity("gb", "Outdoor", "Pays", "XYZ",
                "https://api.os.uk/maps/raster/v1/zxy/Outdoor_3857/{z}/{x}/{y}.png?key={KEY}",
                apiKey = "ZNN49wJ1i2aDTmjZzzNys65O2OF5hWHW", minZoom = 7, maxZoom = 16, enabled = false, sortOrder = n()),
            // Ce WMS ne declare que les CRS polonais et le 4326 dans son GetCapabilities, mais son moteur
            // ArcGIS accepte et reprojette quand meme le 3857 : verifie sur le Palais de la Culture, qui
            // tombe bien au centre d'une bbox centree dessus. Les WMTS du geoportail (TOPO, BDOT10k) sont
            // eux inutilisables ici, leurs matrices n'existent qu'en EPSG:2180 (et 4326 pour TOPO).
            // maxZoom 15 : au-dela le raster 1:10 000 est deja tres agrandi et n'ajoute plus rien.
            ProviderEntity("pl", "Raster", "Pays", "WMS",
                "https://mapy.geoportal.gov.pl/wss/service/img/guest/TOPO/MapServer/WMSServer?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=Raster&STYLES=&FORMAT=image/png&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                maxZoom = 15, enabled = false, sortOrder = n()),
            // La DGT ne publie aucun service pour son SCN50k : sa page ne propose que des telechargements,
            // et ni son MapServer (seuls sc500k et hipsometria repondent), ni son GeoServer, ni son OGC API
            // n'en portent le raster. Le CIGeoE, qui produit le 25k et le 50k, ne libere que son 1:500 000,
            // le reste est "sob consulta". Ce raster de la Carta Militar M888 1:25 000 passe donc par la
            // facade WMS d'un MapServer ArcGIS de l'APAmbiente, publique et sans cle. LAYERS=0 et non 5 :
            // la facade renumerote les couches, la 5 du REST y est le decoupage des freguesias.
            // minZoom 13 : en dessous le 25k est tassé et illisible. maxZoom 16 : au-dela c'est du flou.
            // Continent seulement, Madere et les Acores renvoient une image blanche.
            ProviderEntity("pt", "M888", "Pays", "WMS",
                "https://sniambgeoogc.apambiente.pt/getogc/services/Visualizador/CartBase/MapServer/WMSServer?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=0&STYLES=&FORMAT=image/png&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                minZoom = 13, maxZoom = 16, enabled = false, sortOrder = n()),
        )
    }
}
