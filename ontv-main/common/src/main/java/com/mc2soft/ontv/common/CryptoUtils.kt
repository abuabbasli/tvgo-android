package com.mc2soft.ontv.common

import java.math.BigInteger
import java.security.MessageDigest

object CryptoUtils {
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }
}