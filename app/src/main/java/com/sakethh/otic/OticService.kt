package com.sakethh.otic

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import com.sakethh.otic.OticVM.Companion.VALID_PORT_MSG
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class OticService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioRecordingJob: Job? = null
    private var socketJob: Job? = null
    private var streamingSocket: Socket? = null
    private var streamingOutputStream: OutputStream? = null
    private var serverSocket: ServerSocket? = null
    private var audioRecord: AudioRecord? = null

    private fun cleanUp() {
        audioRecord?.release()
        streamingOutputStream?.close()
        audioRecordingJob?.cancel()
        socketJob?.cancel()
        streamingSocket?.close()
        serverSocket?.close()

        audioRecordingJob = null
        socketJob = null
        streamingSocket = null
        streamingOutputStream = null
        serverSocket = null
        audioRecord = null
        isServiceRunning = false
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        e.printStackTrace()
        toast(e.message ?: e.stackTraceToString())
        onDestroy()
    }

    private fun toast(msg: String) = mainHandler.post {
        Toast.makeText(
            applicationContext, msg, Toast.LENGTH_SHORT
        ).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        require(serverPort in 0..65535) { VALID_PORT_MSG }

        cleanUp()

        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            toast(PERMISSION_REQUIRED_MESSAGE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(applicationContext, "1")
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("Streaming the audio...")
            .setContentText("Streaming on ${ipv4Address ?: "null"}:$serverPort")
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()

        startForeground(1, notification)
        isServiceRunning = true

        socketJob = CoroutineScope(Dispatchers.IO + coroutineExceptionHandler).launch {
            try {
                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                val ipv4Address = ipv4Address ?: error("Can't find ipv4 address")

                serverSocket?.bind(InetSocketAddress(ipv4Address, serverPort))
                logger("Waiting for receiver...")
                streamingSocket = serverSocket?.accept()
                logger("receiver found")
                streamingOutputStream =
                    streamingSocket?.getOutputStream() ?: error("getOutputStream is null")
                startStreaming()
            } catch (e: Exception) {
                toast(e.message ?: e.stackTraceToString())
                e.printStackTrace()
                stopSelf()
            } finally {
                cleanUp()
            }
        }
        return START_STICKY
    }

    companion object {
        val permissions =
            listOf("android.permission.RECORD_AUDIO", "android.permission.POST_NOTIFICATIONS")
        const val PERMISSION_REQUIRED_MESSAGE =
            "Grant RECORD_AUDIO (audio streaming) & POST_NOTIFICATIONS (service updates)."
        val ipv4Address =
            NetworkInterface.getNetworkInterfaces()?.toList()?.firstNotNullOf { networkInterface ->
                networkInterface.inetAddresses?.toList()?.find {
                    !it.isLoopbackAddress && it is Inet4Address
                }?.hostAddress
            }

        var serverPort by mutableIntStateOf(58585)
            private set

        fun updateServerPort(port: Int) {
            serverPort = port
        }

        private const val sampleRate =
            48000 // default rate of PipeWire: https://wiki.archlinux.org/title/PipeWire, which is also the default in tumbleweed: https://en.opensuse.org/openSUSE:Pipewire
        var isServiceRunning by mutableStateOf(false)
            private set
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun startStreaming() {
        audioRecordingJob =
            CoroutineScope(Dispatchers.IO + coroutineExceptionHandler).launch audioRecordingJob@{
                val streamingChannel = AudioFormat.CHANNEL_IN_MONO
                val streamingFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize =
                    AudioRecord.getMinBufferSize(sampleRate, streamingChannel, streamingFormat)
                val bufferSize = maxOf(minBufferSize, 4096)

                audioRecord = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        streamingChannel,
                        streamingFormat,
                        bufferSize
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message ?: "AudioRecord not initialized")
                    cleanUp()
                    return@audioRecordingJob
                }

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    logger("AudioRecord not initialized")
                    return@audioRecordingJob
                }

                audioRecord?.startRecording()

                logger("starting the stream")
                val dataBuffer = ByteArray(bufferSize)
                while (this@audioRecordingJob.isActive) {
                    try {
                        audioRecord?.read(dataBuffer, 0, dataBuffer.size)?.apply {
                            if (this > 0) {
                                streamingOutputStream?.write(dataBuffer, 0, this)
                            }
                        }
                    } catch (e: Exception) {
                        toast(e.message ?: e.stackTraceToString())
                        e.printStackTrace()
                        break
                    }
                }
                audioRecord?.release()
            }.also { it.join() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUp()
        logger("clearing things up")
    }
}