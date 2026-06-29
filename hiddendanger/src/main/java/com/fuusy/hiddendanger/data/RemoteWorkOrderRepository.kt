package com.fuusy.hiddendanger.data

import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File

class RemoteWork(private val api: WorkOrderApiService) {
	suspend fun getFormItems(): List<DynamicFormAdapter.FormItem> {
		try {
			val response = api.getFormStructure()
			if (response.code == 200 && response.data?.formItems != null) {
				return response.data.formItems
			} else {
				android.util.Log.w("RemoteWork", "服务器返回错误: code=${response.code}")
				return emptyList()
			}
		} catch (e: retrofit2.HttpException) {
			android.util.Log.e("RemoteWork", "HTTP异常: ${e.code()} - ${e.message()}")
			if (e.code() == 302) {
				android.util.Log.w("RemoteWork", "收到302重定向，可能需要认证或接口路径有误")
			}
			return emptyList()
		} catch (e: Exception) {
			android.util.Log.e("RemoteWork", "网络请求异常: ${e.message}", e)
			return emptyList()
		}
	}

	suspend fun saveWorkOrder(formJson: String, attachments: List<Pair<String?, String?>>): Boolean {
		val gson = com.google.gson.Gson()
		val map: MutableMap<String, Any> = gson.fromJson(formJson, MutableMap::class.java) as MutableMap<String, Any>
		map["attachments"] = attachments.map { it.first }
		val response = api.saveWorkOrder(map)
		return response.isSuccessful
	}

	suspend fun updateWorkOrder(formJson: String, attachments: List<Pair<String?, String?>>): Boolean {
		val gson = com.google.gson.Gson()
		val map: MutableMap<String, Any> = gson.fromJson(formJson, MutableMap::class.java) as MutableMap<String, Any>
		map["attachments"] = attachments.map { it.first }
		val response = api.updateWorkOrder(map)
		return response.isSuccessful
	}

	suspend fun deleteWorkOrderById(id: String): Boolean {
		val response = api.deleteWorkOrderById(id)
		return response.isSuccessful
	}

	private fun renameFileToEnglish(path: String): String {
		val file = File(path)
		val parent = file.parentFile
		val newFile = File(parent, "upload_${System.currentTimeMillis()}.${file.extension}")
		return if (file.exists() && file.renameTo(newFile)) newFile.absolutePath else path
	}

	suspend fun uploadAttachment(filePath: String, fileName: String = "", fileType: String = ""): String? {
		android.util.Log.d("UploadAttachment", "准备上传: $filePath")
		val englishPath = renameFileToEnglish(filePath)
		val file = File(englishPath)
		if (!file.exists()) {
			android.util.Log.e("UploadAttachment", "文件不存在: $englishPath")
			return null
		}
		val lower = englishPath.lowercase()
		val mimeType = when {
			lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
			lower.endsWith(".png") -> "image/png"
			lower.endsWith(".gif") -> "image/gif"
			lower.endsWith(".mp4") -> "video/mp4"
			lower.endsWith(".mov") -> "video/quicktime"
			lower.endsWith(".avi") -> "video/avi" // 关键：AVI
			lower.endsWith(".ts") -> "video/mp2t"
			lower.endsWith(".mkv") -> "video/x-matroska"
			lower.endsWith(".m4v") -> "video/x-m4v"
			lower.endsWith(".3gp") -> "video/3gpp"
			else -> "application/octet-stream"
		}
		android.util.Log.d("UploadAttachment", "mimeType: $mimeType, fileName: ${file.name}")
		val requestFile = RequestBody.create(mimeType.toMediaTypeOrNull(), file)
		val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
		val fileNamePart = RequestBody.create("text/plain".toMediaTypeOrNull(), fileName.ifBlank { file.name })
		// 如果服务端需要 fileType 标注资源大类，按照 mimeType 首段给默认
		val finalFileType = if (fileType.isNotBlank()) fileType else if (mimeType.startsWith("image")) "image" else if (mimeType.startsWith("video")) "video" else "file"
		val fileTypePart = RequestBody.create("text/plain".toMediaTypeOrNull(), finalFileType)
		val response = api.uploadAttachment(body, fileNamePart, fileTypePart)
		android.util.Log.d("UploadAttachment", "response.isSuccessful=${response.isSuccessful}, code=${response.code()}, body=${response.body()}, errorBody=${response.errorBody()?.string()}")
		return if (response.isSuccessful && response.body()?.code == 200) response.body()?.data else null
	}
} 