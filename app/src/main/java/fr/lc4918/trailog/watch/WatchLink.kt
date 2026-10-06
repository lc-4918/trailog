package fr.lc4918.trailog.watch

/**
 * Ce que le Bluetooth du telephone dit de la montre, tel que la fenetre d'envoi l'affiche.
 *
 * Du plus grave au plus favorable. Le Bluetooth ne dit que si la montre est jointe au telephone : il ne dit
 * pas si l'application Trailog y est installee, ni si Garmin Connect relaie bien ses requetes.
 */
sealed interface WatchLink {
    /** Pas encore lu. */
    data object Checking : WatchLink
    /** Garmin Connect n'est pas installe : c'est lui qui relaie les requetes de la montre vers Trailog. */
    data object GarminConnectMissing : WatchLink
    /** Android 12+ : l'autorisation « Appareils a proximite » n'est pas accordee. */
    data object PermissionNeeded : WatchLink
    /** Le Bluetooth du telephone est eteint, ou absent. */
    data object BluetoothOff : WatchLink
    /** Aucune montre Garmin n'est appairee au telephone. */
    data object NoWatch : WatchLink
    /** La montre est appairee, mais pas jointe en ce moment. */
    data class Disconnected(val watchName: String) : WatchLink
    /** La montre est jointe. */
    data class Connected(val watchName: String) : WatchLink
}

/** Un appareil Bluetooth appaire, reduit a ce que la fenetre en lit. */
data class PairedDevice(val name: String, val connected: Boolean)

/**
 * Les noms que portent les montres Garmin en Bluetooth : « fenix 6X Pro », « Forerunner 245 »,
 * « vivoactive 4 »... Le Bluetooth n'a pas de notion de marque, et seul le nom distingue la montre des
 * autres appareils appaires.
 */
private val GarminWatchNamePrefixes = listOf(
    "garmin", "fenix", "fēnix", "forerunner", "vivoactive", "vívoactive", "venu", "instinct", "epix", "enduro",
    "descent", "marq", "tactix", "quatix", "approach", "edge", "lily", "vivomove", "vívomove", "vivosmart",
    "vívosmart",
)

fun isGarminWatchName(name: String?): Boolean {
    val lower = name?.trim()?.lowercase() ?: return false
    return GarminWatchNamePrefixes.any { lower.startsWith(it) }
}

/**
 * L'etat a afficher.
 *
 * Une montre jointe l'emporte sur les autres : on peut en avoir appaire plusieurs, et c'est celle qu'on
 * porte qui recevra les tuiles. Sans montre jointe, on nomme la premiere appairee.
 *
 * @param permissionGranted faux seulement quand Android l'exige (12+) et qu'elle manque.
 */
fun watchLinkOf(
    garminConnectInstalled: Boolean,
    bluetoothEnabled: Boolean,
    permissionGranted: Boolean,
    devices: List<PairedDevice>,
): WatchLink {
    if (!garminConnectInstalled) return WatchLink.GarminConnectMissing
    if (!permissionGranted) return WatchLink.PermissionNeeded
    if (!bluetoothEnabled) return WatchLink.BluetoothOff
    val watches = devices.filter { isGarminWatchName(it.name) }
    val connected = watches.firstOrNull { it.connected }
    return when {
        connected != null -> WatchLink.Connected(connected.name)
        watches.isNotEmpty() -> WatchLink.Disconnected(watches.first().name)
        else -> WatchLink.NoWatch
    }
}
