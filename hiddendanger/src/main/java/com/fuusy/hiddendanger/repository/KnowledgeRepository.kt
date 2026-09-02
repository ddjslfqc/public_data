package com.fuusy.hiddendanger.repository

import com.fuusy.common.network.ServerConfig
import com.fuusy.hiddendanger.data.FileBrowserApi
import com.fuusy.hiddendanger.data.KnowledgeIngestRequest
import com.fuusy.hiddendanger.data.KnowledgeIngestResponse
import com.fuusy.hiddendanger.data.QianwenApi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class KnowledgeRepository {

    /**
     * 独立 client：
     * 1) 上传超时加长（全局仅 10s 会超时）
     * 2) 不用 DetailedLoggingInterceptor（它会把二进制文件当 UTF-8 字符串重写，导致图片上传异常）
     */
    private val uploadHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val fileBrowserApi: FileBrowserApi by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.getFileBrowserBaseUrl())
            .client(uploadHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FileBrowserApi::class.java)
    }

    private val qianwenApi: QianwenApi by lazy {
        val apiKey = System.getenv("QIANWEN_API_KEY").orEmpty()
            .ifBlank { com.fuusy.common.utils.SpUtils.getString("qianwen_api_key").orEmpty() }
        val clientBuilder = uploadHttpClient.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
        if (apiKey.isNotBlank()) {
            clientBuilder.addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-API-Key", apiKey)
                        .build()
                )
            }
        }
        Retrofit.Builder()
            .baseUrl(ServerConfig.getQianwenBaseUrl())
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QianwenApi::class.java)
    }

    /** 本地文件：默认与 Web 一致走公共 hash；若配置了有效 Token 再走鉴权上传 */
    suspend fun uploadLocalFile(file: File): Result<KnowledgeIngestResponse> {
        val token = ServerConfig.getFileBrowserApiToken().trim()
        // 先公共分享（Web 同款，不需要 JWT）；手机超时多半是连不上 24634，与 token 无关
        val public = uploadPublic(file)
        if (public.isSuccess) return public
        if (token.isNotBlank()) {
            val authed = uploadAuthed(file, token)
            if (authed.isSuccess) return authed
        }
        return public
    }

    /** 粘贴链接：FileBrowser 链接直接记录；其他链接尝试千文 ingest-by-url */
    suspend fun ingestExternalUrl(url: String, title: String? = null): Result<KnowledgeIngestResponse> {
        val trimmed = url.trim()
        if (isFileBrowserUrl(trimmed)) {
            return Result.success(
                KnowledgeIngestResponse(
                    status = "uploaded_only",
                    sourceUrl = trimmed,
                    message = "链接已记录，知识库文件将由系统自动同步识别"
                )
            )
        }
        return ingestUrl(trimmed, title)
    }

    private suspend fun uploadAuthed(file: File, token: String): Result<KnowledgeIngestResponse> {
        val safeName = file.name.trim().ifBlank { "upload.bin" }
        val targetPath = "$UPLOAD_DIR/$safeName"
        return try {
            val body = file.asRequestBody(guessMime(safeName).toMediaTypeOrNull())
            val response = fileBrowserApi.uploadAuthed(
                authorization = "Bearer $token",
                source = ServerConfig.getFileBrowserSource(),
                path = targetPath,
                override = true,
                body = body
            )
            if (response.isSuccessful) {
                Result.success(
                    KnowledgeIngestResponse(
                        status = "uploaded_only",
                        sourceUrl = buildPublicDownloadUrl(
                            ServerConfig.getFileBrowserShareHash(),
                            targetPath
                        ),
                        message = "已上传至知识库，系统将自动识别入库"
                    )
                )
            } else {
                Result.failure(Exception(mapFileBrowserError(response.code(), response.message())))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkError(e))
        }
    }

    private suspend fun uploadPublic(file: File): Result<KnowledgeIngestResponse> {
        val hash = ServerConfig.getFileBrowserShareHash().trim()
        if (hash.isBlank()) {
            return Result.failure(IllegalStateException("未配置 FileBrowser 分享 hash"))
        }
        val safeName = file.name.trim().ifBlank { "upload.bin" }
        val targetPath = "$UPLOAD_DIR/$safeName"
        return try {
            val body = file.asRequestBody(guessMime(safeName).toMediaTypeOrNull())
            val response = fileBrowserApi.uploadPublic(
                hash = hash,
                path = targetPath,
                action = "rename",
                body = body
            )
            if (response.isSuccessful) {
                Result.success(
                    KnowledgeIngestResponse(
                        status = "uploaded_only",
                        sourceUrl = buildPublicDownloadUrl(hash, targetPath),
                        message = "已上传至知识库，系统将自动识别入库"
                    )
                )
            } else {
                Result.failure(Exception(mapFileBrowserError(response.code(), response.message())))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkError(e))
        }
    }

    private suspend fun ingestUrl(url: String, title: String?): Result<KnowledgeIngestResponse> =
        try {
            val resp = qianwenApi.ingestByUrl(
                KnowledgeIngestRequest(
                    url = url,
                    title = title?.substringBeforeLast('.'),
                    contentType = guessContentType(title ?: url)
                )
            )
            Result.success(resp)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                Result.success(
                    KnowledgeIngestResponse(
                        status = "uploaded_only",
                        sourceUrl = url,
                        message = "链接已保存；识别入库接口待开通，FileBrowser 文件会自动同步"
                    )
                )
            } else {
                Result.failure(wrapHttp(e))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkError(e))
        }

    private fun buildPublicDownloadUrl(hash: String, path: String): String {
        val base = ServerConfig.getFileBrowserBaseUrl().trimEnd('/')
        val file = URLEncoder.encode(path, Charsets.UTF_8.name())
        val h = URLEncoder.encode(hash, Charsets.UTF_8.name())
        return "$base/public/api/resources/download?hash=$h&file=$file"
    }

    private fun isFileBrowserUrl(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val base = ServerConfig.getFileBrowserBaseUrl().trimEnd('/')
        val baseHost = base.toHttpUrlOrNull()?.host ?: return false
        if (httpUrl.host != baseHost) return false
        return httpUrl.encodedPath.contains("/public/api/resources") ||
            httpUrl.encodedPath.contains("/api/resources")
    }

    private fun mapNetworkError(e: Exception): Exception {
        val cause = e.cause
        return when {
            e is SocketTimeoutException || cause is SocketTimeoutException ->
                Exception("上传超时，请确认手机能访问 42.228.15.242:24634 后重试")
            e is UnknownHostException || cause is UnknownHostException ->
                Exception("无法连接知识库服务器，请检查网络")
            e is HttpException -> Exception(mapFileBrowserError(e.code(), e.message()))
            e is IOException -> Exception(e.message?.takeIf { it.isNotBlank() } ?: "网络异常，上传失败")
            else -> Exception(e.message ?: "上传失败")
        }
    }

    private fun mapFileBrowserError(code: Int, fallback: String?): String = when (code) {
        401, 403 -> "鉴权失败或无上传权限（$code），请检查 FileBrowser Token"
        404 -> "上传目录不存在或分享无效（404）"
        409 -> "同名文件冲突（409），请重试或改名"
        413 -> "文件超过大小限制（413）"
        else -> fallback?.takeIf { it.isNotBlank() } ?: "上传失败（$code）"
    }

    private fun guessMime(name: String): String = when (
        name.substringAfterLast('.', "").lowercase()
    ) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "md", "txt" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun guessContentType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "pdf"
            "doc", "docx" -> "word"
            "png", "jpg", "jpeg", "webp", "gif" -> "image"
            "md" -> "markdown"
            else -> "file"
        }
    }

    private fun wrapHttp(e: HttpException): Exception {
        val body = e.response()?.errorBody()?.string().orEmpty()
        val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
        return Exception(detail ?: body.ifBlank { e.message() } ?: "请求失败")
    }

    companion object {
        private const val UPLOAD_DIR = "/上传"
    }
}
