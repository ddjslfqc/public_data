package com.fuusy.common.utils

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.*
import java.io.IOException

class WebRTCPusher(
    private val context: Context,
    private val signalingUrl: String, // ZLMediaKit HTTP接口
    private val onLog: (String) -> Unit // 日志回调
) {
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private lateinit var audioTrack: AudioTrack

    fun init() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        val options = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        val audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
    }

    fun connectAndPush() {
        onLog("创建 PeerConnection")
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        )
//        startAudioLevelMonitor()
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                // ZLMediaKit HTTP接口不需要单独发送 candidate
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })
        peerConnection?.addTrack(audioTrack)
        onLog("添加音频轨道到 PeerConnection")

        // 创建 offer
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(this, desc)
                // POST offer.sdp 到 ZLMediaKit HTTP接口
                postOfferSdp(desc.description)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                onLog("Offer创建失败: $p0")
            }

            override fun onSetFailure(p0: String?) {
                onLog("SetLocal失败: $p0")
            }
        }, MediaConstraints())
    }

    private fun postOfferSdp(offerSdp: String) {
        onLog("offer.sdp内容：\n$offerSdp")
        val client = OkHttpClient()
        val body = offerSdp.toRequestBody("text/plain; charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url(signalingUrl)
            .post(body)
            .addHeader("Content-Type", "text/plain; charset=UTF-8")
            .addHeader("Accept", "application/json, text/plain, */*")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onLog("推流失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    onLog("收到 answer.sdp 响应内容：$respStr")
                    try {
                        val json = JSONObject(respStr)
                        // 兼容两种返回格式
                        val answerSdp = when {
                            json.has("sdp") -> json.getString("sdp")
                            json.has("data") && json.getJSONObject("data")
                                .has("sdp") -> json.getJSONObject("data").getString("sdp")

                            else -> ""
                        }
                        if (answerSdp.isNotEmpty()) {
                            setRemoteAnswer(answerSdp)
                        } else {
                            onLog("推流失败: 未找到 answer.sdp 字段")
                        }
                    } catch (e: Exception) {
                        onLog("推流失败: answer.sdp 解析异常: ${e.message}")
                    }
                } else {
                    onLog("推流失败: ${response.code}")
                }
            }
        })
    }

    private fun setRemoteAnswer(answerSdp: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                onLog("SetRemote成功，推流已建立")
            }

            override fun onSetFailure(p0: String?) {
                onLog("SetRemote失败: $p0")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, answer)
    }

    fun close() {
        peerConnection?.close()
        factory.dispose()
    }

    private var audioLevelThread: Thread? = null
    private var isAudioLevelRunning = false

    fun startAudioLevelMonitor() {
        isAudioLevelRunning = true
        audioLevelThread = Thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                    onLog("音频录制初始化失败：无效的音频参数")
                    return@Thread
                }
                
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    onLog("音频录制初始化失败：状态异常")
                    audioRecord.release()
                    return@Thread
                }
                
                val buffer = ShortArray(bufferSize)
                audioRecord.startRecording()
                
                while (isAudioLevelRunning) {
                    try {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            val rms = Math.sqrt(buffer.take(read).map { it * it.toDouble() }.average())
                            if (rms > 1000) { // 阈值可调整
                                onLog("【本地检测】正在说话，音量：$rms")
                            } else {
                                onLog("【本地检测】静音，音量：$rms")
                            }
                        }
                        Thread.sleep(200)
                    } catch (e: Exception) {
                        onLog("音频录制读取异常: ${e.message}")
                        break
                    }
                }
                
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {
                    onLog("音频录制释放异常: ${e.message}")
                }
            } catch (e: SecurityException) {
                onLog("音频录制权限被拒绝: ${e.message}")
            } catch (e: Exception) {
                onLog("音频录制初始化异常: ${e.message}")
            }
        }
        audioLevelThread?.start()
    }

    fun stopAudioLevelMonitor() {
        isAudioLevelRunning = false
        audioLevelThread?.join()
    }
}
