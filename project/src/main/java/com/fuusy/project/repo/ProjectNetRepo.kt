package com.fuusy.project.repo

import android.os.Parcelable
import com.fuusy.common.network.ServerConfig
import com.fuusy.project.bean.WorkOrderListResponse
import com.fuusy.project.ui.model.VideoChannelInfo
import com.fuusy.project.network.ApiService
import kotlinx.parcelize.Parcelize
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Body
import android.util.Log

class ProjectNetRepo {
    // 统一用 ApiService
    private val projectApi = ApiService.projectApi
    private val videoApi = ApiService.videoApi

    companion object {
        private const val TAG = "VideoList"
    }

    suspend fun getWorkOrderList(): Result<WorkOrderListResponse> {
        return try {
            val resp = projectApi.getWorkOrderList()
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchVideoList(): Result<List<VideoInfo>> {
        return try {
            val requestUrl = "${ServerConfig.getBaseUrl()}mobile/video/list"
            Log.d(TAG, "请求视频列表: POST $requestUrl")
            val resp = videoApi.getVideoList(VideoListBody())
            val list = resp.data?.allVideo.orEmpty()
            Log.d(
                TAG,
                "视频列表响应: code=${resp.code}, msg=${resp.msg}, count=${list.size}, inPersonNum=${resp.data?.inPersonNum}"
            )
            list.forEachIndexed { index, video ->
                Log.d(
                    TAG,
                    "[$index] name=${video.show_name}, type=${video.type}, device=${video.device_id}, " +
                        "channel=${video.channel_id}, path=${video.videoPath}, location=${video.location}"
                )
            }
            if (resp.code == 200) {
                Result.success(list)
            } else {
                Result.failure(Exception(resp.msg ?: "获取视频列表失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取视频列表失败", e)
            Result.failure(e)
        }
    }

    suspend fun fetchVideoChannels(item: String, device: String): List<VideoChannelInfo> {
        return try {
            Log.d("VideoControl", "开始获取视频通道列表: item=$item, device=$device")
            val resp = videoApi.getVideoList(VideoListBody())
            Log.d("VideoControl", "获取视频列表API响应: code=${resp.code}")

            val channels = mutableListOf<VideoChannelInfo>()
            var channelNo = 1

            resp.data?.allVideo?.forEach { videoInfo ->
                Log.d(
                    "VideoControl",
                    "处理视频: device_id=${videoInfo.device_id}, channel_id=${videoInfo.channel_id}, videoPath=${videoInfo.videoPath}"
                )

                val videoChannelInfo = VideoChannelInfo(
                    channelNo = channelNo++,
                    streamUrl = videoInfo.videoPath ?: "",
                    cameraip = videoInfo.device_id ?: "",
                    channelId = videoInfo.channel_id ?: "",
                    region = "视频区域",
                    deviceId = videoInfo.device_id ?: "",
                    person = resp.data.inPersonNum?.toString() ?: "0"
                )
                channels.add(videoChannelInfo)
                Log.d("VideoControl", "创建视频通道信息: $videoChannelInfo")
            }
            Log.d("VideoControl", "总共获取到 ${channels.size} 个视频通道")
            channels
        } catch (e: Exception) {
            Log.e("VideoControl", "获取视频通道列表失败", e)
            getMockVideoChannels()
        }
    }

    fun getMockVideoChannels(): List<VideoChannelInfo> {
        return List(4) { i ->
            VideoChannelInfo(
                channelNo = i + 1,
                streamUrl = "rtsp://192.168.110.84:554/11",
                cameraip = "192.168.110.84",
                channelId = "11",
                region = "测试区域",
                deviceId = "mock_device_${i + 1}",
                person = "0"
            )
        }
    }
}

// Retrofit接口定义
interface VideoApi {
    @POST("mobile/video/list")
    suspend fun getVideoList(@Body body: VideoListBody): VideoListResponse
}

class VideoListBody

data class VideoListResponse(
    val code: Int,
    val msg: String?,
    val data: VideoListData?
)

data class VideoListData(
    val allVideo: List<VideoInfo>?,
    val inPersonNum: Int?
)

@Parcelize
data class VideoInfo(
    val videoPath: String?,
    val device_id: String?,
    val channel_id: String?,
    val show_name: String?,
    val type: Int?,
    val location: String? = null,
    val danger_label: String? = null,
    val channel_label: String? = null,
) : Parcelable

fun getMockVideoChannels(): List<VideoChannelInfo> {
    val repo = ProjectNetRepo()
    return repo.getMockVideoChannels()
}
