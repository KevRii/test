// ==================== app/src/main/java/com/paperai/Api.kt ====================
package com.paperai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object Api {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class ChatResult(val content: String, val reasoning: String)

    private fun dataUrl(path: String): String {
        val f = File(path)
        if (!f.exists()) return ""
        val bytes = f.readBytes()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    suspend fun chat(
        provider: Provider,
        model: String,
        systemPrompt: String,
        history: List<MessageNode>,
        temperature: Double,
        maxTokens: Int
    ): ChatResult = withContext(Dispatchers.IO) {

        val msgs = buildJsonArray {
            if (systemPrompt.isNotBlank()) {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            history.forEach { n ->
                if (n.role == "assistant") {
                    add(buildJsonObject {
                        put("role", "assistant")
                        put("content", n.content)
                    })
                } else {
                    val imgs = n.images.filter { File(it).exists() }
                    if (imgs.isEmpty()) {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", n.content)
                        })
                    } else {
                        val parts = buildJsonArray {
                            if (n.content.isNotBlank()) add(buildJsonObject {
                                put("type", "text")
                                put("text", n.content)
                            })
                            imgs.forEach { p ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", dataUrl(p)) }
                                })
                            }
                        }
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", parts)
                        })
                    }
                }
            }
        }

        val payload = buildJsonObject {
            put("model", model)
            put("messages", msgs)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", false)
        }

        val base = provider.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val reqBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))

        if (provider.apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${provider.apiKey}")
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}\n${body.take(600)}")
            }
            parseResponse(body)
        }
    }

    suspend fun listModels(provider: Provider): List<String> = withContext(Dispatchers.IO) {
        val base = provider.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/models")) base else "$base/models"
        val rb = Request.Builder().url(url).get()
        if (provider.apiKey.isNotBlank()) rb.addHeader("Authorization", "Bearer ${provider.apiKey}")
        client.newCall(rb.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val root = Json.parseToJsonElement(body).jsonObject
            root["data"]?.jsonArray?.mapNotNull {
                it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
        }
    }

    private fun parseResponse(body: String): ChatResult {
        val root = Json.parseToJsonElement(body).jsonObject
        val choices = root["choices"]?.jsonArray
        val msg = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw RuntimeException("响应格式异常：$body")

        val contentEl = msg["content"]
        val content = when (contentEl) {
            is JsonPrimitive -> contentEl.contentOrNull ?: ""
            is JsonArray -> contentEl.joinToString("") {
                it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
            }
            else -> ""
        }

        val reasoning = msg["reasoning_content"]?.jsonPrimitive?.contentOrNull
            ?: msg["reasoning"]?.jsonPrimitive?.contentOrNull
            ?: ""

        return ChatResult(content, reasoning)
    }
}
