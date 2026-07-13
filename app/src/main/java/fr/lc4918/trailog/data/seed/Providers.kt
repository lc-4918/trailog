package fr.lc4918.trailog.data.seed

import fr.lc4918.trailog.data.db.ProviderEntity

/**
 * Fonds de carte par défaut. Repris de tefeciste/2024 js/map.js + OSM (défaut),
 * OpenFreeMap, MapTiler, overlays Waymarked Trails et DEM pour le relief.
 * Tous éditables ensuite (URL + clé) dans les réglages.
 * {KEY} est remplacé par apiKey ; {s} est étendu selon subdomains.
 */
object Providers {
    fun defaults(): List<ProviderEntity> {
        var o = 0
        fun n() = o++
        return listOf(
            // --- Monde ---
            ProviderEntity("osm", "OpenStreetMap", "Monde", "XYZ",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                maxZoom = 19, attribution = "© OpenStreetMap contributors", sortOrder = n()),
            ProviderEntity("mapbox_outdoors", "Mapbox Outdoors", "Monde", "XYZ",
                "https://api.mapbox.com/styles/v1/mapbox/outdoors-v10/tiles/256/{z}/{x}/{y}@2x?access_token={KEY}",
                apiKey = "", maxZoom = 21, sortOrder = n()),
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
                apiKey = "", maxZoom = 18, sortOrder = n()),
            ProviderEntity("thunder_outdoors", "Thunderforest Outdoors", "Monde", "XYZ",
                "https://tile.thunderforest.com/outdoors/{z}/{x}/{y}.png?apikey={KEY}",
                apiKey = "", maxZoom = 20, sortOrder = n()),
            ProviderEntity("openfreemap", "OpenFreeMap Liberty", "Monde", "VECTOR",
                "https://tiles.openfreemap.org/styles/liberty", sortOrder = n()),
            ProviderEntity("maptiler_outdoor", "MapTiler Outdoor", "Monde", "VECTOR",
                "https://api.maptiler.com/maps/outdoor-v2/style.json?key={KEY}",
                apiKey = "", sortOrder = n()),

            // --- Overlays (transparents) ---
            ProviderEntity("way_mtb", "Waymarked Trails VTT", "Overlays", "XYZ",
                "https://tile.waymarkedtrails.org/mtb/{z}/{x}/{y}.png",
                transparent = true, maxZoom = 18, sortOrder = n()),
            ProviderEntity("way_cycle", "Waymarked Trails Cycle", "Overlays", "XYZ",
                "https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png",
                transparent = true, maxZoom = 18, sortOrder = n()),

            // --- Relief (DEM -> hillshade) ---
            ProviderEntity("dem_terrarium", "Relief (DEM terrarium)", "Relief", "DEM",
                "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png",
                tileSize = 256, maxZoom = 15, enabled = false, sortOrder = n()),

            // --- Pays ---
            ProviderEntity("ign_fr", "France - IGN Scan", "Pays", "WMTS",
                "https://data.geopf.fr/private/wmts?LAYER=GEOGRAPHICALGRIDSYSTEMS.MAPS&apikey={KEY}&EXCEPTIONS=text/xml&FORMAT=image/jpeg&SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&STYLE=normal&TILEMATRIXSET=PM&TILEMATRIX={z}&TILECOL={x}&TILEROW={y}",
                apiKey = "ign_scan_ws", maxZoom = 17, sortOrder = n()),
            ProviderEntity("ign_es", "Espagne - IGN MTN", "Pays", "WMS",
                "https://www.ign.es/wms-inspire/mapa-raster?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=mtn_rasterizado&STYLES=&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                maxZoom = 20, sortOrder = n()),
            ProviderEntity("hu", "Hongrie - Turistautak", "Pays", "XYZ",
                "https://terkep.turistautak.hu/tiles/turistautak-domborzattal/{z}/{x}/{y}.png",
                maxZoom = 17, sortOrder = n()),
            ProviderEntity("sk", "Slovaquie - Freemap", "Pays", "XYZ",
                "https://tiles.freemap.sk/T/{z}/{x}/{y}.png", subdomains = "1,2,3,4", maxZoom = 16, sortOrder = n()),
            ProviderEntity("at", "Autriche - basemap.at", "Pays", "XYZ",
                "https://mapsneu.wien.gv.at/basemap/geolandbasemap/normal/google3857/{z}/{y}/{x}.png",
                subdomains = "1,2,3,4", maxZoom = 19, sortOrder = n()),
            ProviderEntity("no", "Norvège - Statkart", "Pays", "XYZ",
                "https://opencache.statkart.no/gatekeeper/gk/gk.open_gmaps?layers=toporaster3&zoom={z}&x={x}&y={y}",
                maxZoom = 17, sortOrder = n()),
            ProviderEntity("be", "Belgique - NGI", "Pays", "XYZ",
                "https://cartoweb.wmts.ngi.be/1.0.0/topo/default/3857/{z}/{y}/{x}.png", maxZoom = 18, sortOrder = n()),
            ProviderEntity("se", "Suède - Lantmäteriet", "Pays", "XYZ",
                "https://maps.lantmateriet.se/open/topowebb-ccby/v1/wmts/token/{KEY}/1.0.0/topowebb/default/3857/{z}/{y}/{x}.png",
                apiKey = "", maxZoom = 17, sortOrder = n()),
            ProviderEntity("hr", "Croatie - DGU", "Pays", "WMS",
                "https://geoportal.dgu.hr/services/tk/wms?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=TK25&STYLES=&FORMAT=image/jpeg&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}",
                maxZoom = 18, sortOrder = n()),
        )
    }
}
