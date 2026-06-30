package com.fuusy.login.repo

import com.fuusy.common.base.BaseRepository
import com.fuusy.common.network.net.StateLiveData
import com.fuusy.service.repo.LoginResp

class LoginRepo(private val service: LoginApi) : BaseRepository() {

    suspend fun login(userName: String, password: String, stateLiveData: StateLiveData<LoginResp>) =
        executeResp({ service.login(LoginBody(userName, password)) }, stateLiveData)

    suspend fun register(
        userName: String,
        password: String,
        nickName: String,
        deptId: Long,
        stateLiveData: StateLiveData<Boolean>
    ) = executeResp({
        service.register(RegisterBody(userName, password, nickName, deptId))
    }, stateLiveData)

    suspend fun loadDeptList(): Result<List<Pair<Long, String>>> = try {
        val resp = service.getDeptList()
        if (resp.isSuccess) {
            Result.success(flattenDepts(resp.data))
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "获取部门失败"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
