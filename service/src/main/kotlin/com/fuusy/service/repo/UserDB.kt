package com.fuusy.service.repo

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import com.google.gson.annotations.SerializedName

@Database(entities = [LoginResp::class], version = 4, exportSchema = false)
abstract class UserDB : RoomDatabase() {

    abstract val userDao: UserDao

    companion object {
        const val DB_NAME = "tb_user"

        @Volatile
        private var instance: UserDB? = null

        fun get(context: Context): UserDB {
            return instance ?: Room.databaseBuilder(context, UserDB::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}

@Entity(tableName = "tb_user")
data class LoginResp(
    @PrimaryKey
    @SerializedName("userId")
    val id: Int,
    val username: String,
    @SerializedName("nickName")
    val nickName: String = "",
    @SerializedName("deptId")
    val deptId: Long = 0,
    @SerializedName("deptName")
    val department: String = "",
    @SerializedName("admin")
    val admin: String = "",
    val company: String = "",
) {
    fun displayName(): String = nickName.ifBlank { username }

    /** 接口可能缺字段或为 null，入库前统一兜底为空字符串 */
    fun sanitizeForDb(): LoginResp = copy(
        username = username.blankToEmpty(),
        nickName = nickName.blankToEmpty().ifBlank { username.blankToEmpty() },
        department = department.blankToEmpty(),
        admin = admin.blankToEmpty(),
        company = company.blankToEmpty(),
    )

    private fun String?.blankToEmpty(): String = this?.trim().orEmpty()
}

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(info: LoginResp)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateUser(info: LoginResp)

    @Delete
    fun deleteUser(info: LoginResp)

    @Query("select * from tb_user where id =:id")
    fun queryLiveUser(id: Int = 0): LiveData<LoginResp>

    @Query("select * from tb_user where id =:id")
    fun queryUser(id: Int = 0): LoginResp?
}
