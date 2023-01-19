/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * handleSSL.java is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */
package com.my.kizzy.utils

import android.content.Context
import com.my.kizzy.R
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object HandleSSL {
    @Throws(Exception::class)
    fun getSSLContextFromAndroidKeystore(app: Context): SSLContext? {
        // load up the key store
        val storePassword = "SSLKEY"
        val keyPassword = "SSLKEY"
        val keystore = KeyStore.getInstance("BKS")
        val `in` = app.resources.openRawResource(R.raw.ssl)
        `in`.use { `in` ->
            keystore.load(`in`, storePassword.toCharArray())
        }
        val keyManagerFactory = KeyManagerFactory.getInstance("X509")
        keyManagerFactory.init(keystore, keyPassword.toCharArray())
        val tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(keystore)
        val sslContext: SSLContext? = SSLContext.getInstance("TLS")
        sslContext?.init(keyManagerFactory.keyManagers, tmf.trustManagers, null)
        return sslContext
    }
}