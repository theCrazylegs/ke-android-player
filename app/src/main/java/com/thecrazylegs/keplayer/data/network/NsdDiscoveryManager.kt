package com.thecrazylegs.keplayer.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

private const val TAG = "NsdDiscovery"
private const val SERVICE_TYPE = "_karaoke-eternal._tcp.local."

data class ServerInfo(
    val name: String,
    val host: String,
    val port: Int,
    val urlPath: String
)

class NsdDiscoveryManager(private val context: Context) {

    private val _discoveredServers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val discoveredServers: StateFlow<List<ServerInfo>> = _discoveredServers.asStateFlow()

    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            Log.i(TAG, "Service found: ${event.name} (requesting info...)")
            // Request full service info (triggers serviceResolved)
            jmdns?.requestServiceInfo(event.type, event.name, 5000)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            Log.i(TAG, "Service lost: ${event.name}")
            _discoveredServers.value = _discoveredServers.value.filter {
                it.name != event.name
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info
            val addresses = info.inetAddresses
            if (addresses.isEmpty()) {
                Log.w(TAG, "Resolved ${event.name} but no addresses found")
                return
            }

            // Skip if we already have this server resolved
            if (_discoveredServers.value.any { it.name == info.name }) {
                Log.d(TAG, "Already resolved ${info.name}, ignoring duplicate")
                return
            }

            val host = addresses[0].hostAddress ?: return
            val port = info.port
            val urlPath = info.getPropertyString("path") ?: "/"

            val server = ServerInfo(
                name = info.name,
                host = host,
                port = port,
                urlPath = urlPath
            )

            Log.i(TAG, "Resolved: ${server.host}:${server.port} (path=${server.urlPath})")

            val current = _discoveredServers.value.toMutableList()
            current.add(server)
            _discoveredServers.value = current
        }
    }

    fun startDiscovery() {
        if (jmdns != null) return

        _discoveredServers.value = emptyList()

        discoveryJob = scope.launch {
            try {
                // Acquire multicast lock (required on some Android devices)
                val wifi = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifi.createMulticastLock("keplayer_mdns").apply {
                    setReferenceCounted(true)
                    acquire()
                }
                Log.i(TAG, "Multicast lock acquired")

                // Create jmDNS instance bound to the device's wifi address
                val wifiInfo = wifi.connectionInfo
                val ipInt = wifiInfo.ipAddress
                val ipBytes = byteArrayOf(
                    (ipInt and 0xff).toByte(),
                    (ipInt shr 8 and 0xff).toByte(),
                    (ipInt shr 16 and 0xff).toByte(),
                    (ipInt shr 24 and 0xff).toByte()
                )
                val addr = InetAddress.getByAddress(ipBytes)

                Log.i(TAG, "Starting jmDNS on ${addr.hostAddress}")
                jmdns = JmDNS.create(addr, "keplayer").apply {
                    addServiceListener(SERVICE_TYPE, serviceListener)
                }
                Log.i(TAG, "Discovery started for $SERVICE_TYPE")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start discovery", e)
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()

        try {
            jmdns?.removeServiceListener(SERVICE_TYPE, serviceListener)
            jmdns?.close()
            jmdns = null
            Log.i(TAG, "Discovery stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop jmDNS", e)
        }

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            multicastLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release multicast lock", e)
        }
    }
}
