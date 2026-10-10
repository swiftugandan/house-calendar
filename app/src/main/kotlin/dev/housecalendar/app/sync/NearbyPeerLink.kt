package dev.housecalendar.app.sync

import dev.housecalendar.nearby.NearbyLink
import dev.housecalendar.nearby.Peer
import dev.housecalendar.sync.LinkPeer
import dev.housecalendar.sync.PeerLink
import dev.housecalendar.sync.Transport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Nearby Connections as a [PeerLink]: the Bluetooth fallback when the devices don't share Wi-Fi. */
class NearbyPeerLink(private val nearby: NearbyLink) : PeerLink {
    override fun peers(): Flow<List<LinkPeer>> =
        nearby.peers().map { peers -> peers.map { LinkPeer(it.endpointId, it.deviceId, it.name, it.role) } }

    override suspend fun connect(peer: LinkPeer): Transport = nearby.connect(Peer(peer.address, peer.device, peer.name, peer.role))

    override suspend fun awaitIncoming(): Transport? = nearby.awaitIncoming()

    override fun stop() = nearby.stop()
}
