package com.prtracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

class DriveBackupClient(
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun findBackup(token: String): DriveBackupFile? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("name='$BACKUP_FILE_NAME' and trashed=false", "UTF-8")
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL?spaces=appDataFolder&q=$q&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime desc")
            .header("Authorization", "Bearer $token")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Unable to read Drive backup metadata: HTTP ${response.code}")
            val files = JSONObject(response.body?.string().orEmpty()).optJSONArray("files")
            val first = files?.optJSONObject(0) ?: return@withContext null
            DriveBackupFile(
                id = first.getString("id"),
                name = first.optString("name", BACKUP_FILE_NAME),
                modifiedTime = first.optString("modifiedTime", ""),
                size = first.optLong("size", 0L),
            )
        }
    }

    suspend fun uploadBackup(token: String, file: File): DriveBackupFile = withContext(Dispatchers.IO) {
        val existing = findBackup(token)
        if (existing == null) createBackup(token, file) else updateBackup(token, existing.id, file)
    }

    suspend fun downloadBackup(token: String, destination: File): DriveBackupFile = withContext(Dispatchers.IO) {
        val backup = findBackup(token) ?: error("No Google Drive backup found.")
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/${backup.id}?alt=media")
            .header("Authorization", "Bearer $token")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Unable to download Drive backup: HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Downloaded backup was empty.")
        }
        backup
    }

    private fun createBackup(token: String, file: File): DriveBackupFile {
        val metadata = JSONObject()
            .put("name", BACKUP_FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadata.toString().toRequestBody(JSON_MEDIA_TYPE),
            )
            .addFormDataPart(
                "file",
                BACKUP_FILE_NAME,
                file.asRequestBody(SQLITE_MEDIA_TYPE),
            )
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id,name,modifiedTime,size")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        return executeFileRequest(request, "Unable to create Drive backup")
    }

    private fun updateBackup(token: String, fileId: String, file: File): DriveBackupFile {
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL/$fileId?uploadType=media&fields=id,name,modifiedTime,size")
            .header("Authorization", "Bearer $token")
            .patch(file.asRequestBody(SQLITE_MEDIA_TYPE))
            .build()

        return executeFileRequest(request, "Unable to update Drive backup")
    }

    private fun executeFileRequest(request: Request, failureMessage: String): DriveBackupFile {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$failureMessage: HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return DriveBackupFile(
                id = json.getString("id"),
                name = json.optString("name", BACKUP_FILE_NAME),
                modifiedTime = json.optString("modifiedTime", ""),
                size = json.optLong("size", 0L),
            )
        }
    }

    companion object {
        private const val BACKUP_FILE_NAME = DatabaseBackupManager.BACKUP_FILE_NAME
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private val SQLITE_MEDIA_TYPE = "application/x-sqlite3".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long,
)
