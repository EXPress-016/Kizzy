package com.my.kizzy.rpc

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.ArrayMap
import com.android.girish.vlog.Vlog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.my.kizzy.App
import com.my.kizzy.common.Constants
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.domain.use_case.get_current_data.SharedRpc
import com.my.kizzy.rpc.model.*
import com.my.kizzy.service.WssService
import com.my.kizzy.utils.Prefs
import com.my.kizzy.utils.Prefs.CUSTOM_ACTIVITY_TYPE
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.net.ssl.SSLParameters

class KizzyRPC @Inject constructor(
    private val kizzyRepository: KizzyRepository,
    private val vlog: Vlog
) {
    lateinit var rpc: RichPresence
    private var activityName: String? = null
    private var details: String? = null
    private var state: String? = null
    private var largeImage: RpcImage? = null
    private var smallImage: RpcImage? = null
    private var largeText: String? = null
    private var smallText: String? = null
    private var startTimestamps: Long? = null
    private var stopTimestamps: Long? = null
    private var type: Int = 0
    var webSocketClient: WebSocketClient? = null
    var webSocketServer: WssService.WebSocketServer? = null
    var gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var buttons = ArrayList<String>()
    private var buttonUrl = ArrayList<String>()
    private var port = 6463

    fun closeRPC() {
        if (webSocketClient != null) webSocketClient!!.close(1000)
    }

    fun closeWSS() {
        val context = App.getContext()
        if (webSocketServer != null) context.stopService(Intent(context, WssService::class.java))
    }

    fun isRpcRunning(): Boolean {
        if (webSocketClient?.isOpen == true) return true
        return false
    }

    fun isWssRunning(): Boolean {
        if (webSocketServer?.isOpen == true) return true
        return false
    }

    /**
     * Activity Name of Rpc
     *
     * @param activity_name
     * @return
     */
    fun setName(activity_name: String?): KizzyRPC {
        this.activityName = activity_name
        return this
    }

    /**
     * Details of Rpc
     *
     * @param details
     * @return
     */
    fun setDetails(details: String?): KizzyRPC {
        this.details = details
        return this
    }

    /**
     * Rpc State
     *
     * @param state
     * @return
     */
    fun setState(state: String?): KizzyRPC {
        this.state = state
        return this
    }

    /**
     * Large image on rpc
     * How to get Image ?
     * Upload image to any discord chat and copy its media link it should look like "https://media.discordapp.net/attachments/90202992002/xyz.png" now just use the image link from attachments part
     * so it would look like: .setLargeImage("attachments/90202992002/xyz.png")
     * @param large_image
     * @return
     */
    fun setLargeImage(large_image: RpcImage?, large_text: String? = null): KizzyRPC {
        this.largeText = large_text.takeIf { !it.isNullOrEmpty() }
        this.largeImage = large_image
        return this
    }

    /**
     * Small image on Rpc
     *
     * @param small_image
     * @return
     */
    fun setSmallImage(small_image: RpcImage?, small_text: String? = null): KizzyRPC {
        this.smallText = small_text.takeIf { !it.isNullOrEmpty() }
        this.smallImage = small_image
        return this
    }

    /**
     * start timestamps
     *
     * @param start_timestamps
     * @return
     */
    fun setStartTimestamps(start_timestamps: Long?): KizzyRPC {
        this.startTimestamps = start_timestamps
        return this
    }

    /**
     * stop timestamps
     *
     * @param stop_timestamps
     * @return
     */
    fun setStopTimestamps(stop_timestamps: Long?): KizzyRPC {
        this.stopTimestamps = stop_timestamps
        return this
    }

    /**
     * Activity Types
     * 0: Playing
     * 1: Streaming
     * 2: Listening
     * 3: Watching
     * 5: Competing
     *
     * @param type
     * @return
     */

    fun setType(type: Int): KizzyRPC {
        if (type in 0..5)
            this.type = type
        else this.type = 0
        return this
    }

    /**
     * Button1 text
     * @param button1_Text
     * @return
     */
    fun setButton1(button1_Text: String?): KizzyRPC {
        button1_Text?.let { buttons.add(it) }
        return this
    }

    /**
     * Button2 text
     * @param button2_text
     * @return
     */
    fun setButton2(button2_text: String?): KizzyRPC {
        button2_text?.let { buttons.add(it) }
        return this
    }

    /**
     * Button1 url
     * @param url
     * @return
     */
    fun setButton1URL(url: String?): KizzyRPC {
        url?.let { buttonUrl.add(it) }
        return this
    }

    /**
     * Button2 url
     * @param url
     * @return
     */
    fun setButton2URL(url: String?): KizzyRPC {
        url?.let { buttonUrl.add(it) }
        return this
    }

    suspend fun build() {
        rpc = RichPresence(
            op = 3,
            d = RichPresenceData(
                activities = listOf(
                    Activity(
                        name = activityName,
                        state = state,
                        details = details,
                        type = type,
                        timestamps = if (startTimestamps != null || stopTimestamps != null) Timestamps(
                            start = startTimestamps,
                            end = stopTimestamps
                        ) else null,
                        assets = if (largeImage != null || smallImage != null) Assets(
                            largeImage = largeImage?.resolveImage(kizzyRepository),
                            smallImage = smallImage?.resolveImage(kizzyRepository),
                            largeText = largeText,
                            smallText = smallText
                        ) else null,
                        buttons = if (buttons.size > 0) buttons else null,
                        metadata = if (buttonUrl.size > 0) Metadata(buttonUrls = buttonUrl) else null,
                        applicationId = if (buttons.size > 0) Constants.APPLICATION_ID else null
                    )
                ),
                afk = true,
                since = if (startTimestamps != null) startTimestamps else System.currentTimeMillis()
            )
        )
        createWebsocket(port)
    }

    private fun createWebsocket(port: Int) {
        if (!isWssRunning()) {
            vlog.d(TAG, "createWebsocketServer()")
            createWebsocketServer(port)
            return
        }
        vlog.d(TAG, "createWebsocketClient()")
        createWebsocketClient(port)
    }

    private fun createWebsocketClient(port: Int) {
        webSocketServer = WssService.server
        if (webSocketServer == null) return
        val uri: URI = try {
            URI("wss://localhost.direct:${port}")
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            return
        }
        val headerMap = ArrayMap<String, String>()
        webSocketClient = Websocket(uri, headerMap)
        (webSocketClient as Websocket).connect()
    }
    private fun createWebsocketServer(port: Int) {
        val context = App.getContext()
        val serverIntent = Intent(context, WssService::class.java)
        serverIntent.putExtra("port", port)
        context.startService(serverIntent)
            Handler(Looper.getMainLooper()).postDelayed({
                createWebsocketClient(port)
            }, 3000)
    }

    suspend fun updateRPC(
        name: String,
        details: String?,
        state: String?,
        large_image: RpcImage?,
        small_image: RpcImage?,
        enableTimestamps: Boolean,
        time: Long
    ) {
        if (!isRpcRunning()) return
        webSocketClient!!.send(
            RichPresence(
                op = 3,
                d = RichPresenceData(
                    activities = listOf(
                        Activity(
                            name = name,
                            details = details,
                            state = state,
                            type = Prefs[CUSTOM_ACTIVITY_TYPE, 0],
                            timestamps = if (enableTimestamps) Timestamps(start = time) else null,
                            assets =
                            if (large_image != null || small_image != null)
                                Assets(
                                    largeImage = large_image?.resolveImage(kizzyRepository),
                                    smallImage = small_image?.resolveImage(kizzyRepository)
                                )
                            else null,
                            buttons = if (buttons.size > 0) buttons else null,
                            metadata = if (buttonUrl.size > 0) Metadata(buttonUrls = buttonUrl) else null,
                            applicationId = if (buttons.size > 0) Constants.APPLICATION_ID else null
                        )
                    ),
                    afk = true,
                    since = time
                )
            )
        )
    }

    suspend fun updateRPC(sharedRpc: SharedRpc) {
        if (!isRpcRunning()) return
        var time = Timestamps(start = startTimestamps)
        if (sharedRpc.time != null)
            Timestamps(end = sharedRpc.time.end, start = sharedRpc.time.start).also { time = it }
        webSocketClient!!.send(
            RichPresence(
                op = 3,
                d = RichPresenceData(
                    activities = listOf(
                        Activity(
                            name = sharedRpc.name,
                            details = sharedRpc.details?.takeIf { it.isNotEmpty() },
                            state = sharedRpc.state?.takeIf { it.isNotEmpty() },
                            type = Prefs[CUSTOM_ACTIVITY_TYPE, 0],
                            timestamps = time,
                            assets = if (sharedRpc.large_image != null || sharedRpc.small_image != null)
                                Assets(
                                    largeImage = sharedRpc.large_image?.resolveImage(kizzyRepository),
                                    smallImage = sharedRpc.small_image?.resolveImage(kizzyRepository)
                                )
                            else null,
                            buttons = if (buttons.size > 0) buttons else null,
                            metadata = if (buttonUrl.size > 0) Metadata(buttonUrls = buttonUrl) else null,
                            applicationId = if (buttons.size > 0) Constants.APPLICATION_ID else null
                        )
                    ),
                    afk = true,
                    since = startTimestamps
                )
            )
        )
    }

    companion object {
        const val TAG = "Websocket"
    }

    inner class Websocket(uri: URI, map: ArrayMap<String, String>) : WebSocketClient(uri, map) {
        override fun send(text: String?) {
            if (text != null) {
                vlog.d(TAG, text)
            }
            super.send(text)
        }

        override fun connect() {
            vlog.d(TAG, "connect() called on port: $port")
            super.connect()
        }

        override fun onOpen(handshakedata: ServerHandshake?) {
            vlog.i(TAG, "onOpen() called with: handshake-data: $handshakedata")
            send("I am the handler!")
            send("[READY] Websocket is ready to send rpc")
        }

        override fun onMessage(message: String) {
            vlog.d(TAG, "onMessage() called with: message: $message")
            if (message.contains("[READY] Websocket is ready to receive rpc")) {
                send(rpc)
            }
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            vlog.d(TAG, "onClose() called with: code: $code, reason: $reason, remote: $remote")
        }

        override fun onError(e: Exception) {
            vlog.e(TAG, "onError() called with: e: $e")
            if (e.message != "Interrupt") {
                closeRPC()
                return
            }
            if (port > 6472) return
            port++
            createWebsocket(port)
        }

        override fun onSetSSLParameters(p: SSLParameters) {
            try {
                super.onSetSSLParameters(p)
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
    }

    private fun WebSocketClient?.send(src: Any) {
        this?.let {
            if (it.isOpen)
                it.send(gson.toJson(src))
        }
    }
}
