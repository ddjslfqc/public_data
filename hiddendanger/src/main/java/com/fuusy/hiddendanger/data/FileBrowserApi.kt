package com.fuusy.hiddendanger.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * FileBrowser 上传：
 * - 公共分享：/public/api/resources?hash=...
 * - 鉴权管理：/api/resources + Authorization Bearer（与你提供的 token 配套）
 * 请求体均为文件原始字节，不要用 multipart。
 */
interface FileBrowserApi {

    @POST("public/api/resources")
    suspend fun uploadPublic(
        @Query("hash") hash: String,
        @Query("path") path: String,
        @Query("action") action: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST("api/resources")
    suspend fun uploadAuthed(
        @Header("Authorization") authorization: String,
        @Query("source") source: String,
        @Query("path") path: String,
        @Query("override") override: Boolean = true,
        @Body body: RequestBody
    ): Response<ResponseBody>
}
