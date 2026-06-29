import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset
import java.net.URLDecoder

class DetailedLoggingInterceptor : Interceptor {
    companion object {
        private const val TAG = "NetworkLog"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // 打印请求信息
        logRequest(request)
        
        return try {
            val response = chain.proceed(request)
            // 打印响应信息
            logResponse(response)
            response
        } catch (e: Exception) {
            Log.e("$TAG-ERROR", "网络请求异常: ${e.message}", e)
            throw e
        }
    }

    private fun logRequest(request: Request) {
        Log.d("$TAG-REQUEST", "┌────── Request ──────")
        val decodedUrl = try { URLDecoder.decode(request.url.toString(), "UTF-8") } catch (_: Exception) { request.url.toString() }
        Log.d("$TAG-REQUEST", "│ URL: $decodedUrl")
        Log.d("$TAG-REQUEST", "│ Method: ${request.method}")
        
        // 打印请求头
        request.headers.forEach { (name, value) ->
            Log.d("$TAG-REQUEST", "│ Header: $name = $value")
        }
        
        // 打印请求体（对表单进行解码）
        request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
            val raw = buffer.readString(charset)
            val contentType = body.contentType()?.toString()?.lowercase() ?: ""
            val displayBody = if (contentType.contains("x-www-form-urlencoded")) {
                try { URLDecoder.decode(raw, charset.name()) } catch (_: Exception) { raw }
            } else raw
            Log.d("$TAG-REQUEST", "│ Body: $displayBody")
        }
        
        Log.d("$TAG-REQUEST", "└─────────────────────")
    }

    private fun logResponse(response: Response) {
        try {
            Log.d("$TAG-RESPONSE", "┌────── Response ──────")
            Log.d("$TAG-RESPONSE", "│ URL: ${response.request.url}")
            Log.d("$TAG-RESPONSE", "│ Code: ${response.code}")
            Log.d("$TAG-RESPONSE", "│ Message: ${response.message}")
            Log.d("$TAG-RESPONSE", "│ Success: ${response.isSuccessful}")
            
            // 打印响应头
            response.headers.forEach { (name, value) ->
                Log.d("$TAG-RESPONSE", "│ Header: $name = $value")
            }
            
            // 安全地读取响应体
            response.body?.let { body ->
                val source = body.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer
                val responseBodyString = buffer.clone().readUtf8()
                val contentType = body.contentType()?.toString()?.lowercase() ?: ""
                val display = if (contentType.contains("x-www-form-urlencoded")) {
                    try { URLDecoder.decode(responseBodyString, Charsets.UTF_8.name()) } catch (_: Exception) { responseBodyString }
                } else responseBodyString
                Log.d("$TAG-RESPONSE", "│ Body: $display")
            }
            
            Log.d("$TAG-RESPONSE", "└──────────────────────")
        } catch (e: Exception) {
            Log.e("$TAG-ERROR", "读取响应失败: ${e.message}", e)
        }
    }
}