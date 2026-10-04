package com.storepos.app.update

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val notes: List<String>,
    val downloadUrl: String,
    val required: Boolean
)

interface UpdateSource {
    suspend fun latest(): Result<AppUpdate?>
}
