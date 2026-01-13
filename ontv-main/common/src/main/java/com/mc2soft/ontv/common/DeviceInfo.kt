package com.mc2soft.ontv.common

import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import java.io.File
import java.lang.reflect.Method
import java.math.BigInteger
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteOrder
import java.util.*


object DeviceInfo {
    val macAddress: String?
        get() {
            try {
                return File("/sys/class/net/eth0/address").readText().trim().toUpperCase()
                //return (OnTVSharedSettings.application.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.macAddress
            } catch (ex: Exception) { return null }
        }

    val firmwareVersion: String?
        get() {
            return try {
                val cls = Class.forName("android.os.SystemProperties")
                val method: Method = cls.getMethod("get", String::class.java)
                method.setAccessible(true)
                method.invoke(cls, "ro.product.version") as? String
            } catch (e: java.lang.Exception) {
                null
            }
        }

    val localWifiIpAddress: String?
        get() {
            try {
                val wifiManager =
                    BaseApp.instance.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                var ipAddress: Int = wifiManager.getConnectionInfo().getIpAddress()

                if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                    ipAddress = Integer.reverseBytes(ipAddress)
                }

                val ipByteArray: ByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
                return InetAddress.getByAddress(ipByteArray).hostAddress
            } catch (ex: java.lang.Exception) {
                return null
            }
        }


    val localIpAddress: String?
        get() {
            try {
                val useIPv4 = true
                val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs: List<InetAddress> = Collections.list(intf.getInetAddresses())
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress
                            //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (useIPv4) {
                                if (isIPv4) return sAddr
                            } else {
                                if (!isIPv4) {
                                    val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                    return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(
                                        0,
                                        delim
                                    ).uppercase(
                                        Locale.getDefault()
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (ex: java.lang.Exception) {
            }

            return null
        }
}