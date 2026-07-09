package com.fuusy.login.repo

import com.fuusy.common.network.BaseResp
import com.fuusy.service.repo.LoginResp
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LoginApi {

    @POST("mobile/auth/register")
    suspend fun register(@Body body: RegisterBody): BaseResp<Boolean>

    @POST("mobile/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordBody): BaseResp<Boolean>

    @POST("mobile/auth/login")
    suspend fun login(@Body body: LoginBody): BaseResp<LoginResp>

    @GET("mobile/auth/dept/list")
    suspend fun getDeptList(): BaseResp<List<DeptNode>>
}

data class RegisterBody(
    val username: String,
    val password: String,
    val nickName: String,
    val deptId: Long
)

data class ResetPasswordBody(
    val username: String,
    val nickName: String,
    val password: String
)

data class LoginBody(
    val username: String,
    val password: String
)

data class DeptNode(
    val id: Long,
    val name: String,
    val parentId: Long = 0,
    val children: List<DeptNode>? = null
)

/** 将树形部门扁平化为可选列表 */
fun flattenDepts(nodes: List<DeptNode>?, prefix: String = ""): List<Pair<Long, String>> {
    if (nodes.isNullOrEmpty()) return emptyList()
    val result = mutableListOf<Pair<Long, String>>()
    nodes.forEach { node ->
        val label = if (prefix.isEmpty()) node.name else "$prefix / ${node.name}"
        result.add(node.id to label)
        result.addAll(flattenDepts(node.children, label))
    }
    return result
}
