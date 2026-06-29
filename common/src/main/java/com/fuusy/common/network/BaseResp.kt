package com.fuusy.common.network

/**
 * json返回的基本类型
 */
import com.google.gson.annotations.SerializedName

class BaseResp<T>{
    @SerializedName(value = "errorCode", alternate = ["code"])
    var errorCode = -1
    
    @SerializedName(value = "errorMsg", alternate = ["message", "status"])
    var errorMsg: String? = null
    
    var data: T? = null
    var dataState: DataState? = null
    var error: Throwable? = null
    
    val isSuccess: Boolean
        get() = errorCode == 0 || errorCode == 200  // 支持HTTP状态码200
        
    // 添加带参数的构造函数
    constructor()
    constructor(errorCode: Int, data: T?, errorMsg: String?) {
        this.errorCode = errorCode
        this.data = data
        this.errorMsg = errorMsg
    }
}