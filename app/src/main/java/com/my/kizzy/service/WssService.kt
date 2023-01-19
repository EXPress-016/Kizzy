/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * WebSocketServerService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.my.kizzy.App
import com.my.kizzy.utils.HandleSSL
import com.my.kizzy.utils.Log.vlog
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import java.net.InetSocketAddress

class WssService : Service() {
    private var port: Int = 0
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        port = intent?.getIntExtra("port", 0) ?: 0
        if (port == 0) return super.onStartCommand(intent, flags, startId)
        server = WebSocketServer(InetSocketAddress(port))
        val ssl = HandleSSL.getSSLContextFromAndroidKeystore(App.getContext())
        server?.setWebSocketFactory(DefaultSSLWebSocketServerFactory(ssl))
        server?.start()
        vlog.d(TAG, "start() called")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    inner class WebSocketServer(address: InetSocketAddress) :
        org.java_websocket.server.WebSocketServer(address) {
        private var handler: WebSocket? = null
        private var client: WebSocket? = null
        var isOpen: Boolean = false
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            conn.send("[CONNECTION] You are now connected to WebSocketServer!")
            vlog.d(
                TAG,
                "[CONNECTION] ${conn.remoteSocketAddress} is now connected to WebSocketServer!"
            )
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            if (conn == handler) handler = null
            if (conn == client) client = null
            vlog.d(
                TAG,
                "[CONNECTION] Closed ${conn.remoteSocketAddress} with exit code $code, additional info: $reason"
            )
        }

        override fun onMessage(conn: WebSocket, message: String) {
            val assigned = assign(conn, message)
            if (assigned) return
            if (handler == null) {
                conn.send("[ERROR] No handler found!")
                return
            }
            if (client == null) {
                conn.send("[ERROR] No client found!")
                return
            }
            if (conn == handler) {
                (client as WebSocket).send(message)
            }
            if (conn == client) {
                (handler as WebSocket).send(message)
            }
            vlog.d(TAG, "[MESSAGE] New message from ${conn.remoteSocketAddress}, message: $message")
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            if (conn == handler) handler = null
            if (conn == client) client = null
            vlog.e(
                TAG,
                "[ERROR] An error occurred on connection ${conn?.remoteSocketAddress}, message: ${ex.message}"
            )
        }

        override fun onStart() {
            vlog.d(TAG, "Started server on port: $port")
            isOpen = true
        }

        override fun stop() {
            super.stop()
            isOpen = false
        }

        private fun assign(conn: WebSocket, message: String): Boolean {
            val pattern = Regex("I am the \\w+!")
            if (pattern.matches(message)) {
                val name = message.replace("I am the", "").trim().replace("!", "")
                if (name == "handler") {
                    if (handler != null) {
                        conn.send("[ERROR] Someone is already $name!")
                        return true
                    }
                    if (client == conn) {
                        conn.send("[ERROR] Handler and Client can't be the same connection!")
                        return true
                    }
                    handler = conn
                    (handler as WebSocket).send("[INFO] Assigned you as $name!")
                    vlog.d(TAG, "[INFO] Assigned ${handler?.remoteSocketAddress} as $name!")
                    return true
                }
                if (name == "client") {
                    if (client != null) {
                        conn.send("[ERROR] Someone is already $name!")
                        return true
                    }
                    if (handler == conn) {
                        conn.send("[ERROR] Client and Handler can't be the same connection!")
                        return true
                    }
                    client = conn
                    (client as WebSocket).send("[INFO] Assigned you as $name!")
                    vlog.d(TAG, "[INFO] Assigned ${client?.remoteSocketAddress} as $name!")
                    return true
                }
            }
            return false
        }
    }

    companion object {
        const val TAG = "WebsocketServer"
        var server: WssService.WebSocketServer? = null
    }

}
