package com.skallahaze.irbloasster.data

import okhttp3.OkHttpClient
import okhttp3.Response
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object LocalTls {
    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun localSelfSignedClient(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun sha256Fingerprint(response: Response): String? {
        val certificate = response.handshake?.peerCertificates?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString(":") { byte -> "%02X".format(byte) }
    }
}
