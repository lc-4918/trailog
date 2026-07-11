package fr.lc4918.trailog.map

/** Espace de nommage des ids de fonds composites dans les champs qui référencent normalement un ProviderEntity.id
 *  (SettingsEntity.defaultBasemapId, sélection du Basemap Control), pour les distinguer des providers réels. */
private const val PREFIX = "composite_"

fun compositeBasemapId(compositeId: Long): String = "$PREFIX$compositeId"

fun compositeIdFromBasemapId(basemapId: String): Long? =
    if (basemapId.startsWith(PREFIX)) basemapId.removePrefix(PREFIX).toLongOrNull() else null
