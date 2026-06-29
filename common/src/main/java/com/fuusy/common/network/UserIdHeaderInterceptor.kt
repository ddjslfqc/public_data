package com.fuusy.common.network

import okhttp3.Interceptor
import okhttp3.Response

class UserIdHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val id = UserIdProvider.userId ?: return chain.proceed(original)
        val request = original.newBuilder()
            .header("X-User-Id", id.toString())
            .build()
        return chain.proceed(request)
    }
}
