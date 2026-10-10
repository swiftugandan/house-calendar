package dev.housecalendar.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import dev.housecalendar.contracts.DeviceId
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.sync.LinkPeer
import dev.housecalendar.sync.PeerLink
import dev.housecalendar.sync.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Finds the family's phones on the same Wi-Fi with Network Service Discovery (mDNS) and connects to them
 * over TCP (SAD ADR-9). Each phone listens on an ephemeral port while [peers] is collected and advertises
 * `_housecal._tcp` with the TXT attributes of [LanRecord]: the family tag (a hash, never the id), its device
 * id, its name and its role. Only peers with a home-network address are connected to or accepted.
 */
public class LanLink(
    context: Context,
    private val family: FamilyId,
    private val self: DeviceId,
    private val selfName: String,
    private val selfRole: DeviceRole,
) : PeerLink {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val found = MutableStateFlow<Map<String, LinkPeer>>(emptyMap())
    private val incoming = Channel<Transport>(Channel.UNLIMITED)
    private val resolveLock = Mutex()

    @Volatile private var server: ServerSocket? = null

    override fun peers(): Flow<List<LinkPeer>> = callbackFlow {
        val listening = ServerSocket(0).also { server = it }
        scope.launch { acceptLoop(listening) }
        val multicast = appContext.getSystemService(WifiManager::class.java)?.createMulticastLock(MULTICAST_TAG)?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val registration = registrationListener()
        nsd.registerService(serviceInfo(listening.localPort), NsdManager.PROTOCOL_DNS_SD, registration)
        val discovery = discoveryListener()
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        launch { found.collectLatest { send(it.values.sortedBy(LinkPeer::name)) } }
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(discovery) }
            runCatching { nsd.unregisterService(registration) }
            multicast?.release()
            listening.close()
            found.value = emptyMap()
        }
    }

    override suspend fun connect(peer: LinkPeer): Transport = withContext(Dispatchers.IO) {
        val host = peer.address.substringBeforeLast(':')
        val port = peer.address.substringAfterLast(':').toIntOrNull() ?: throw IOException("bad peer address ${peer.address}")
        val address = InetAddress.getByName(host)
        if (!LanAddresses.isLocal(address)) throw IOException("refusing a non-local address")
        val socket = Socket()
        socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
        SocketTransport(socket)
    }

    override suspend fun awaitIncoming(): Transport? = incoming.receiveCatching().getOrNull()

    override fun stop() {
        server?.close()
        scope.cancel()
        incoming.close()
    }

    private fun acceptLoop(listening: ServerSocket) {
        while (!listening.isClosed) {
            val socket = try {
                listening.accept()
            } catch (e: SocketException) {
                return
            }
            if (LanAddresses.isLocal(socket.inetAddress)) incoming.trySend(SocketTransport(socket)) else socket.close()
        }
    }

    private fun serviceInfo(port: Int) = NsdServiceInfo().apply {
        serviceName = SERVICE_PREFIX + self.toString().take(SERVICE_ID_CHARS)
        serviceType = SERVICE_TYPE
        setPort(port)
        LanRecord.attributes(family, self, selfName, selfRole).forEach { (key, value) -> setAttribute(key, value) }
    }

    private fun registrationListener() = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) = Unit
        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
    }

    private fun discoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onServiceFound(info: NsdServiceInfo) {
            if (!info.serviceName.startsWith(SERVICE_PREFIX)) return
            scope.launch { resolve(info)?.let { peer -> found.update { it + (info.serviceName to peer) } } }
        }

        /**
         * A device that restarts re-registers under the same name, and "found" for the new registration can
         * arrive before "lost" for the old one. So a loss is checked by resolving the name again: if it still
         * resolves, the device is there (perhaps on a new port) and stays.
         */
        override fun onServiceLost(info: NsdServiceInfo) {
            scope.launch {
                val again = resolve(info)
                found.update { if (again == null) it - info.serviceName else it + (info.serviceName to again) }
            }
        }
    }

    /** NSD resolves one service at a time on older Android versions, so resolves are queued. */
    @Suppress("DEPRECATION") // resolveService and host are the only APIs below 34; minSdk is 28.
    private suspend fun resolve(info: NsdServiceInfo): LinkPeer? = resolveLock.withLock {
        val resolved = suspendCoroutine<NsdServiceInfo?> { continuation ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(service: NsdServiceInfo) = continuation.resume(service)
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) = continuation.resume(null)
            })
        } ?: return@withLock null
        val record = LanRecord.parse(resolved.attributes, family, self) ?: return@withLock null
        val host = resolved.host
        if (host == null || !LanAddresses.isLocal(host)) return@withLock null
        LinkPeer("${host.hostAddress}:${resolved.port}", record.device, record.name, record.role)
    }

    private companion object {
        const val SERVICE_TYPE = "_housecal._tcp."
        const val SERVICE_PREFIX = "hc-"
        const val SERVICE_ID_CHARS = 13
        const val CONNECT_TIMEOUT_MS = 5_000
        const val MULTICAST_TAG = "house-calendar-nsd"
    }
}
