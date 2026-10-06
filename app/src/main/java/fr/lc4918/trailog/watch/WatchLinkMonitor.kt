package fr.lc4918.trailog.watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Suit l'etat de la montre par le Bluetooth Android, le temps que la fenetre d'envoi est ouverte.
 *
 * Sans le SDK Connect IQ de Garmin, dont la licence interdit de le redistribuer et ne se concilie pas avec
 * la GPLv3 de l'application. Le Bluetooth ne dit donc que ce qu'il sait : la montre est-elle appairee,
 * est-elle jointe au telephone (cf. [WatchLink]).
 *
 * Rien n'est emis ni recherche : on lit les appareils appaires, puis ceux qu'une liaison GATT relie au
 * telephone - Garmin Connect tient la sienne ainsi -, et on ecoute les connexions et deconnexions.
 */
class WatchLinkMonitor(private val context: Context) {

    companion object {
        const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
    }

    private val mutableLink = MutableStateFlow<WatchLink>(WatchLink.Checking)
    val link: StateFlow<WatchLink> = mutableLink.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    /** Les adresses jointes d'apres les diffusions Android : la liaison GATT ne les couvre pas toutes. */
    private val connectedByBroadcast = mutableSetOf<String>()

    /** Android 12+ demande « Appareils a proximite » ; avant, la permission ordinaire est accordee d'office. */
    fun permissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                val address = deviceOf(intent)?.address
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> address?.let { connectedByBroadcast += it }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> address?.let { connectedByBroadcast -= it }
                }
                refresh()
            }
        }
        receiver = listener
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, listener, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        connectedByBroadcast.clear()
    }

    /** Relit l'etat : a l'ouverture, a chaque diffusion, et apres que l'autorisation vient d'etre accordee. */
    @SuppressLint("MissingPermission")
    fun refresh() {
        val garminConnectInstalled = runCatching {
            context.packageManager.getPackageInfo(GARMIN_CONNECT_PACKAGE, 0)
        }.isSuccess
        val permission = permissionGranted()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val devices = if (permission && adapter?.isEnabled == true) {
            val connectedAddresses = connectedAddresses(manager) + connectedByBroadcast
            runCatching {
                adapter.bondedDevices.orEmpty().map { PairedDevice(it.name.orEmpty(), it.address in connectedAddresses) }
            }.getOrDefault(emptyList())
        } else emptyList()
        mutableLink.value = watchLinkOf(
            garminConnectInstalled = garminConnectInstalled,
            bluetoothEnabled = adapter?.isEnabled == true,
            permissionGranted = permission,
            devices = devices,
        )
    }

    @SuppressLint("MissingPermission")
    private fun connectedAddresses(manager: BluetoothManager): Set<String> = runCatching {
        (manager.getConnectedDevices(BluetoothProfile.GATT) + manager.getConnectedDevices(BluetoothProfile.GATT_SERVER))
            .map { it.address }.toSet()
    }.getOrDefault(emptySet())

    @Suppress("DEPRECATION")
    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
