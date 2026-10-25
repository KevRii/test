// ==================== app/src/main/java/com/paperai/Store.kt ====================
package com.paperai

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

class Store(private val ctx: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val dataFile = File(ctx.filesDir, "paperai_data.json")
    private val imgDir = File(ctx.filesDir, "images").also { it.mkdirs() }

    fun imageDir(): File = imgDir

    fun load(): AppData {
        val raw = try {
            if (dataFile.exists()) json.decodeFromString<AppData>(dataFile.readText()) else AppData()
        } catch (e: Exception) {
            AppData()
        }
        return normalize(raw)
    }

    fun save(d: AppData) {
        try {
            val tmp = File(dataFile.parentFile, "paperai_data.tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), d))
            tmp.renameTo(dataFile)
        } catch (_: Exception) {
        }
    }

    private fun normalize(d: AppData): AppData {
        var s = d.settings
        if (s.providers.isEmpty()) s = s.copy(providers = defaultProviders())
        if (s.providerId.isBlank() || s.providers.none { it.id == s.providerId })
            s = s.copy(providerId = s.providers.first().id)
        if (s.templates.isEmpty()) s = s.copy(templates = defaultTemplates())
        if (s.templateId.isBlank() || s.templates.none { it.id == s.templateId })
            s = s.copy(templateId = s.templates.first().id)
        if (s.model.isBlank())
            s = s.copy(model = s.providers.firstOrNull { it.id == s.providerId }?.models?.firstOrNull() ?: "")
        if (s.providers.none { it.id == "custom" })
            s = s.copy(providers = s.providers + Provider("custom", "自定义", "https://", "", emptyList()))
        return d.copy(settings = s)
    }
}
