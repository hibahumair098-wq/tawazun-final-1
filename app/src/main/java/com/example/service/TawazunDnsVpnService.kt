package com.example.service

import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.domain.DomainFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * 🌐 TAWAZUN SAFE WEB VPN — system-wide website blocking (Chrome tak!).
 *
 * Kaam kaise karta hai (DNS-only VPN — FREE, koi server nahi):
 *   1. VPN sirf DNS servers ke IPs ko apne andar route karta hai
 *      (baaki traffic — WhatsApp, games, sab — bilkul normal rehta hai)
 *   2. Jab bhi koi app kisi website ka naam resolve karta hai, wo hum ke paas aata hai
 *   3. Parent ki blocked list (jaise *.youtube.com) mein ho → "site nahi mili" (NXDOMAIN)
 *   4. Allowed ho → asli jawab 8.8.8.8 (Google DNS) se la kar wapas
 *
 * Tawazun ka apna traffic (Firebase) VPN se bahar hai (addDisallowedApplication) —
 * is liye app ka data hamesha chalta rahega.
 *
 * Privacy: hum sirf domain NAAM parhte hain — koi browsing content nahi.
 * IPv4 networks (Pakistani carriers/WiFi) ke liye.
 */
class TawazunDnsVpnService : VpnService() {

    companion object {
        @Volatile
        var isRunning = false
            private set

        const val EXTRA_BLOCKED = "blocked_domains"
        const val ACTION_STOP = "com.tawzun.pksafe.ACTION_STOP_DNS_VPN"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val TAG = "TawazunDnsVpn"
    }

    @Volatile private var blocklist: List<String> = emptyList()
    @Volatile private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var stopped = false
    private var pumpThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        // Nayi list aaye to running VPN par bhi apply ho jaye
        (intent?.getStringArrayListExtra(EXTRA_BLOCKED))?.let { blocklist = it }
        if (blocklist.isEmpty()) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (vpnInterface == null) startVpn()
        return START_STICKY
    }

    /** VPN setup — sirf system DNS servers ke IPs route karo. */
    private fun startVpn() {
        val servers = systemDnsServers()
        if (servers.isEmpty()) {
            Log.w(TAG, "Koi system DNS server nahi mila — VPN start nahi hua")
            stopSelf()
            return
        }
        try {
            val builder = Builder()
                .setSession("Tawazun Safe Web")
                .setMtu(1500)
                .addAddress("10.111.111.1", 32)
            for (dns in servers) {
                try {
                    builder.addRoute(dns, 32)
                } catch (_: Exception) {
                    // IPv6 ya ajeeb address — skip (IPv4 focus)
                }
            }
            // Tawazun ka apna traffic VPN se bahar — Firebase hamesha kaam karega
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
            }
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.w(TAG, "VPN establish fail (permission revoked?)")
                stopSelf()
                return
            }
            isRunning = true
            stopped = false
            pumpThread = Thread { pumpPackets() }.apply { start() }
            Log.i(TAG, "Safe Web VPN ON — ${blocklist.size} blocked rules, DNS: $servers")
        } catch (e: Exception) {
            Log.e(TAG, "VPN start error: ${e.message}")
            stopSelf()
        }
    }

    /** Phone ke current network ke DNS servers (jinki traffic hum intercept karenge). */
    private fun systemDnsServers(): List<String> {
        val servers = linkedSetOf<String>()
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            for (network in cm.allNetworks) {
                val lp = cm.getLinkProperties(network) ?: continue
                for (dns in lp.dnsServers) {
                    val host = dns.hostAddress ?: continue
                    if (!host.contains(':')) servers.add(host) // sirf IPv4
                }
            }
        } catch (_: Exception) {
        }
        return servers.toList()
    }

    /** TUN se packets parho → DNS ho to filter kar ke jawab likho. */
    private fun pumpPackets() {
        val pfd = vpnInterface ?: return
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        while (!stopped) {
            val n = try {
                input.read(buf)
            } catch (_: Exception) {
                break
            }
            if (n <= 0) continue
            handlePacket(buf, n, output)
        }
    }

    private fun handlePacket(b: ByteArray, n: Int, out: FileOutputStream) {
        try {
            if (((b[0].toInt() shr 4) and 0xF) != 4) return // sirf IPv4
            val ihl = (b[0].toInt() and 0xF) * 4
            if (ihl < 20 || n < ihl + 8) return
            if (b[9].toInt() != 17) return // sirf UDP
            val srcPort = ((b[ihl].toInt() and 0xFF) shl 8) or (b[ihl + 1].toInt() and 0xFF)
            val dstPort = ((b[ihl + 2].toInt() and 0xFF) shl 8) or (b[ihl + 3].toInt() and 0xFF)
            if (dstPort != 53) return // sirf DNS
            val totalLen = ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
            if (totalLen <= ihl || totalLen > n) return
            val srcIp = b.copyOfRange(12, 16)
            val dstIp = b.copyOfRange(16, 20)
            val dnsOff = ihl + 8
            val dnsLen = totalLen - dnsOff
            if (dnsLen < 17) return

            val qname = extractQueryName(b, dnsOff, dnsLen) ?: return
            val blocked = DomainFilter.isBlocked(qname, blocklist).first
            val response: ByteArray = if (blocked) {
                nxdomainResponse(b, dnsOff, dnsLen)
            } else {
                forwardUpstream(b, dnsOff, dnsLen) ?: return
            }
            respondV4(response, dstIp, srcIp, srcPort, out)
        } catch (_: Exception) {
        }
    }

    /** DNS query se domain naam nikalo (jaise "www.youtube.com"). */
    private fun extractQueryName(p: ByteArray, off: Int, len: Int): String? {
        val end = off + len
        var i = off + 12 // DNS header = 12 bytes
        val sb = StringBuilder()
        while (i < end) {
            val l = p[i].toInt() and 0xFF
            if (l == 0) {
                i++
                break
            }
            if (l > 63 || i + 1 + l >= end) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append(((p[i + k].toInt() and 0xFF).toChar()))
            i += 1 + l
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** Block ka jawab: "ye domain nahi milta" (NXDOMAIN) — query se hi banaya. */
    private fun nxdomainResponse(query: ByteArray, off: Int, len: Int): ByteArray {
        var qEnd = off + len
        var i = off + 12
        while (i < off + len) {
            val l = query[i].toInt() and 0xFF
            if (l == 0) {
                qEnd = i + 1 + 4 // null label + QTYPE + QCLASS
                break
            }
            i += 1 + l
        }
        if (qEnd > off + len) qEnd = off + len
        val out = query.copyOfRange(off, qEnd)
        out[2] = (out[2].toInt() or 0x80).toByte() // QR = 1 (response)
        out[3] = (((out[3].toInt() or 0x80) and 0xF0) or 0x03).toByte() // RA=1, RCODE=3
        for (k in 6..11) out[k] = 0 // answer counts = 0
        return out
    }

    /** Allowed domain ka asli jawab 8.8.8.8 se lao. */
    private fun forwardUpstream(query: ByteArray, off: Int, len: Int): ByteArray? {
        return try {
            val channel = DatagramChannel.open()
            protect(channel.socket()) // VPN ke bahar jao (warna loop ho jata)
            channel.configureBlocking(true)
            channel.socket().soTimeout = 4000
            channel.send(ByteBuffer.wrap(query, off, len), InetSocketAddress(InetAddress.getByName(UPSTREAM_DNS), 53))
            val buf = ByteBuffer.allocate(4096)
            channel.receive(buf)
            channel.close()
            buf.flip()
            val out = ByteArray(buf.remaining())
            buf.get(out)
            out
        } catch (_: Exception) {
            null
        }
    }

    /** App ko jawab wapas likho — poora IPv4 + UDP packet bana kar. */
    private fun respondV4(payload: ByteArray, srcIp: ByteArray, dstIp: ByteArray, dstPort: Int, out: FileOutputStream) {
        try {
            val udpLen = 8 + payload.size
            val total = 20 + udpLen
            val pkt = ByteArray(total)
            pkt[0] = 0x45
            pkt[2] = (total shr 8).toByte()
            pkt[3] = (total and 0xFF).toByte()
            val id = (System.currentTimeMillis() and 0xFFFF).toInt()
            pkt[4] = (id shr 8).toByte()
            pkt[5] = (id and 0xFF).toByte()
            pkt[8] = 64 // TTL
            pkt[9] = 17 // UDP
            System.arraycopy(srcIp, 0, pkt, 12, 4)
            System.arraycopy(dstIp, 0, pkt, 16, 4)
            // IP checksum
            var sum = 0
            var k = 0
            while (k < 20) {
                if (k != 10) {
                    sum += ((pkt[k].toInt() and 0xFF) shl 8) or (pkt[k + 1].toInt() and 0xFF)
                }
                k += 2
            }
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            val c = sum.inv() and 0xFFFF
            pkt[10] = (c shr 8).toByte()
            pkt[11] = (c and 0xFF).toByte()
            // UDP header (checksum 0 — IPv4 mein allowed)
            pkt[20] = 0
            pkt[21] = 53 // src port = DNS
            pkt[22] = (dstPort shr 8).toByte()
            pkt[23] = (dstPort and 0xFF).toByte()
            pkt[24] = (udpLen shr 8).toByte()
            pkt[25] = (udpLen and 0xFF).toByte()
            System.arraycopy(payload, 0, pkt, 28, payload.size)
            out.write(pkt)
            out.flush()
        } catch (_: Exception) {
        }
    }

    private fun stopVpn() {
        stopped = true
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        isRunning = false
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        // User ne Android settings se VPN disconnect kiya
        stopVpn()
        super.onRevoke()
    }
}
