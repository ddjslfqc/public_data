package com.fuusy.common.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.net.URLDecoder
import java.nio.charset.Charset

class DetailedLoggingInterceptor : Interceptor {

    companion object {
        private const val TAG = "NetworkLog"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = logAndRebuildRequest(chain.request())
        return try {
            logResponse(chain.proceed(request))
        } catch (e: Exception) {
            Log.e("$TAG-ERROR", "网络请求异常: ${e.message}", e)
            throw e
        }
    }

    private fun logAndRebuildRequest(request: Request): Request {
        Log.d("$TAG-REQUEST", "┌────── Request ──────")
        val decodedUrl = decodeUrl(request.url.toString())
        Log.d("$TAG-REQUEST", "│ URL: $decodedUrl")
        Log.d("$TAG-REQUEST", "│ Method: ${request.method}")

        val queryNames = request.url.queryParameterNames
        if (queryNames.isNotEmpty()) {
            Log.d("$TAG-REQUEST", "│ Query:")
            queryNames.forEach { name ->
                request.url.queryParameterValues(name).forEach { value ->
                    Log.d("$TAG-REQUEST", "│   $name = $value")
                }
            }
        }

        request.headers.forEach { (name, value) ->
            Log.d("$TAG-REQUEST", "│ Header: $name = $value")
        }

        val body = request.body ?: run {
            Log.d("$TAG-REQUEST", "└─────────────────────")
            return request
        }

        val contentType = body.contentType()?.toString()?.lowercase().orEmpty()
        if (contentType.contains("multipart")) {
            Log.d("$TAG-REQUEST", "│ Body: [multipart/form-data, omitted]")
            Log.d("$TAG-REQUEST", "└─────────────────────")
            return request
        }

        val buffer = Buffer()
        body.writeTo(buffer)
        val charset = body.contentType()?.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
        val raw = buffer.readString(charset)
        val displayBody = if (contentType.contains("x-www-form-urlencoded")) {
            decodeUrl(raw)
        } else {
            raw
        }
        Log.d("$TAG-REQUEST", "│ Body: $displayBody")
        Log.d("$TAG-REQUEST", "└─────────────────────")

        val newBody = raw.toRequestBody(body.contentType())
        return request.newBuilder().method(request.method, newBody).build()
    }

    private fun logResponse(response: Response): Response {
        try {
            Log.d("$TAG-RESPONSE", "┌────── Response ──────")
            Log.d("$TAG-RESPONSE", "│ URL: ${response.request.url}")
            Log.d("$TAG-RESPONSE", "│ Code: ${response.code}")
            Log.d("$TAG-RESPONSE", "│ Message: ${response.message}")
            Log.d("$TAG-RESPONSE", "│ Success: ${response.isSuccessful}")

            response.headers.forEach { (name, value) ->
                Log.d("$TAG-RESPONSE", "│ Header: $name = $value")
            }

            response.body?.let {
                val peekBody = response.peekBody(1024 * 1024)
                val contentType = it.contentType()?.toString()?.lowercase().orEmpty()
                val display = if (contentType.contains("x-www-form-urlencoded")) {
                    decodeUrl(peekBody.string())
                } else {
                    peekBody.string()
                }
                Log.d("$TAG-RESPONSE", "│ Body: $display")
            }

            Log.d("$TAG-RESPONSE", "└──────────────────────")
        } catch (e: Exception) {
            Log.e("$TAG-ERROR", "读取响应失败: ${e.message}", e)
        }
        return response
    }

    private fun decodeUrl(raw: String): String =
        try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
}
