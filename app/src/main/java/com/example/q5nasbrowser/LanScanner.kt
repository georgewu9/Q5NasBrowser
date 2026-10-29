package com.example.q5nasbrowser

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * 局域网 SMB 主机扫描：取本机 IPv4 地址推导出 /24 网段，
 * 对 1..254 逐个试探 445 / 139 端口（多线程），开放者即为 SMB 设备。
 * 不依赖 NetBIOS 广播/浏览，因此比播放器自带"自动搜索"可靠得多。
 */
data class Host(val ip: String, val name: String?)

object LanScanner {

    /** 取得本机首个非回环的 IPv4 地址（兼容 Wi-Fi 与有线以太网）。 */
    fun getLocalIp(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (ni.isLoopback || !ni.isUp) continue
            val addrs = ni.inetAddresses
            while (addrs.hasMoreElements()) {
                val a = addrs.nextElement()
                if (a is Inet4Address && !a.isLoopbackAddress) {
                    return a.hostAddress
                }
            }
        }
        return null
    }

    fun scan(timeoutMs: Int = 400, ports: List<Int> = listOf(445, 139)): List<Host> {
        val local = getLocalIp() ?: return emptyList()
        val parts = local.split(".")
        if (parts.size != 4) return emptyList()
        val base = "${parts[0]}.${parts[1]}.${parts[2]}."

        val found = mutableListOf<Host>()
        val lock = Any()
        val pool = Executors.newFixedThreadPool(64)
        val tasks = (1..254).map { i ->
            val ip = base + i
            Callable {
                for (p in ports) {
                    try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(ip, p), timeoutMs)
                            synchronized(lock) {
                                if (found.none { it.ip == ip }) found.add(Host(ip, null))
                            }
                            break
                        }
                    } catch (_: Exception) {
                        // 端口不通，忽略
                    }
                }
            }
        }
        try {
            pool.invokeAll(tasks)
        } finally {
            pool.shutdown()
        }
        found.sortBy { it.ip }
        return found
    }
}
