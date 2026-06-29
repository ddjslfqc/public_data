package com.fuusy.common.network

/** 供 OKR 等接口注入 X-User-Id 请求头，登录后在 App / Login 层写入 */
object UserIdProvider {

    @Volatile
    var userId: Long? = null
}
