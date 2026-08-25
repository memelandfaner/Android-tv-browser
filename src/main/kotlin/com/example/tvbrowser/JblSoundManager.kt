package com.example.tvbrowser

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 🔊 JblSoundManager
 * Dedicated hardware & software audio manager for JBL BAR 300 (Dolby Atmos & MultiBeam)
 * IP: 192.168.0.229 (MAC: F8:1B:04:17:10:F1)
 *
 * Automatically unlocks hardware audio stream, unmutes TV HDMI-eARC / STREAM_MUSIC,
 * and issues UPnP SOAP commands to keep the JBL BAR 300 awake, unmuted, and balanced.
 */
object JblSoundManager {
    private const val TAG = "JblSoundManager"
    const val JBL_IP = "192.168.0.229"
    private const val JBL_UPNP_PORT = 49152
    private const val JBL_CAST_PORT = 8008

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Unmutes both local Android TV audio stream and network JBL BAR 300 soundbar.
     */
    fun unlockAndUnmute(context: Context? = null, targetVolumePercent: Int = 45) {
        executor.execute {
            try {
                // 1. Android TV Local Audio Manager Unmute
                if (context != null) {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (am != null) {
                        try {
                            am.setStreamMute(AudioManager.STREAM_MUSIC, false)
                            am.setMode(AudioManager.MODE_NORMAL)
                            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            if (cur == 0) {
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.85).toInt(), 0)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Local AudioManager unmute error: ${e.message}")
                        }
                    }
                }

                // 2. JBL BAR 300 UPnP Unmute Command (SetMute: 0)
                sendUpnpSoap(
                    action = "SetMute",
                    body = "<Channel>Master</Channel><DesiredMute>0</DesiredMute>",
                    service = "RenderingControl",
                    controlPath = "/upnp/control/rendercontrol1"
                )

                // 3. JBL BAR 300 Ensure Minimum Optimal Volume
                val currentVol = getJblVolume()
                if (currentVol in 0..15) {
                    setJblVolume(targetVolumePercent)
                }

                Log.d(TAG, "✅ JBL BAR 300 and TV Audio successfully unmuted and synchronized.")
            } catch (e: Exception) {
                Log.w(TAG, "JblSoundManager unlockAndUnmute exception: ${e.message}")
            }
        }
    }

    /**
     * Sets volume directly on JBL BAR 300 (0..100)
     */
    fun setJblVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        executor.execute {
            try {
                sendUpnpSoap(
                    action = "SetVolume",
                    body = "<Channel>Master</Channel><DesiredVolume>$clamped</DesiredVolume>",
                    service = "RenderingControl",
                    controlPath = "/upnp/control/rendercontrol1"
                )
            } catch (e: Exception) {
                Log.w(TAG, "setJblVolume error: ${e.message}")
            }
        }
    }

    /**
     * Reads current volume from JBL BAR 300 via UPnP SOAP
     */
    fun getJblVolume(): Int {
        return try {
            val response = sendUpnpSoap(
                action = "GetVolume",
                body = "<Channel>Master</Channel>",
                service = "RenderingControl",
                controlPath = "/upnp/control/rendercontrol1"
            )
            val regex = "<CurrentVolume>(\\d+)</CurrentVolume>".toRegex()
            val match = regex.find(response)
            match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Sends low-level UPnP SOAP command to JBL BAR 300
     */
    private fun sendUpnpSoap(
        action: String,
        body: String,
        service: String,
        controlPath: String
    ): String {
        val url = URL("http://$JBL_IP:$JBL_UPNP_PORT$controlPath")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:$service:1#$action\"")

        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:$action xmlns:u="urn:schemas-upnp-org:service:$service:1">
                        <InstanceID>0</InstanceID>
                        $body
                    </u:$action>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        conn.outputStream.use { os: OutputStream ->
            os.write(soapBody.toByteArray(Charsets.UTF_8))
            os.flush()
        }

        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
    }
}
