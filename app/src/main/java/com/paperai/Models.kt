// ==================== app/src/main/java/com/paperai/Models.kt ====================
package com.paperai

import kotlinx.serialization.Serializable

@Serializable
data class MessageNode(
    val id: String,
    val parent: String? = null,
    val role: String = "user",          // "user" | "assistant"
    val content: String = "",
    val reasoning: String = "",
    val images: List<String> = emptyList(),  // 本地图片绝对路径
    val ts: Long = 0L,
    val error: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    val nodes: List<MessageNode> = emptyList()
)

@Serializable
data class LibItem(
    val id: String,
    val type: String = "book",          // "book" | "folder"
    val name: String = "",              // 用户备注 / 文件夹名
    val parent: String? = null,
    val order: Int = 0,
    val color: Int = 0,
    val created: Long = 0L,
    val updated: Long = 0L
)

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<String> = emptyList()
)

@Serializable
data class Template(
    val id: String,
    val name: String,
    val content: String
)

@Serializable
data class Settings(
    val providers: List<Provider> = emptyList(),
    val providerId: String = "",
    val model: String = "",
    val templates: List<Template> = emptyList(),
    val templateId: String = "",
    val systemPrompt: String = "",
    val temperature: Double = 0.6,
    val maxTokens: Int = 4096,
    val autoTemplate: Boolean = true
)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val items: List<LibItem> = emptyList(),
    val convs: Map<String, Conversation> = emptyMap()
)

// ---------- 默认配置 ----------

fun defaultProviders(): List<Provider> = listOf(
    Provider("openai", "OpenAI", "https://api.openai.com/v1", "", listOf("gpt-4o", "gpt-4o-mini")),
    Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "", listOf("deepseek-chat", "deepseek-reasoner")),
    Provider("dashscope", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "", listOf("qwen-vl-max", "qwen-plus")),
    Provider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "", listOf("glm-4v-plus", "glm-4-plus")),
    Provider("moonshot", "Kimi", "https://api.moonshot.cn/v1", "", listOf("moonshot-v1-8k-vision-preview")),
    Provider("custom", "自定义", "https://", "", emptyList())
)

fun defaultTemplates(): List<Template> = listOf(
    Template(
        "tpl_paper",
        "英语试卷分析",
        """你是一位资深英语教师与试卷分析专家。用户会提供英语试卷照片，请按以下结构系统分析。

【输出结构】
1. **试卷概览**：题型分布、总分、整体难度评估（易/中/难比例）
2. **逐题解析**：用 Markdown 表格输出 —— 题号 | 考点 | 正确答案 | 解析 | 易错点
3. **作答诊断**（若图片含学生作答）：错题定位 → 错因归类（词汇/语法/语篇/粗心）
4. **能力雷达**：语法、词汇、阅读、写作、听力 各维度得分率与短板
5. **提升方案**：针对薄弱点给出 3 条可立即执行的训练建议

【约束】
- 语言简洁，全部使用 Markdown 表格与列表
- 图片模糊或信息缺失时先说明，并给出重拍建议，不要编造
- 不确定的题目标注「待确认」"""
    ),
    Template(
        "tpl_reading",
        "阅读理解精讲",
        """你是一位英语阅读教学专家。请对用户上传的阅读题目图片进行精讲。

【输出结构】
1. 原文主旨与篇章结构（一句话概括 + 段落功能）
2. 逐题解析：题干定位 → 原文依据（引用原句）→ 正确选项分析 → 干扰项分析
3. 生词与长难句：列出 5 个高频词 + 2 个长难句结构拆解
4. 解题技巧总结

使用 Markdown 输出，条理清晰。"""
    ),
    Template(
        "tpl_grammar",
        "语法错题归因",
        """你是一位英语语法教学专家。请分析用户上传的语法题图片。

【输出结构】
1. 逐题：题号 | 考查语法点 | 正确答案 | 规则讲解 | 易混淆点
2. 错题归类：把学生做错的题按语法点归类统计
3. 规则速记卡：把涉及的语法规则做成 3 条可背诵的口诀
4. 同类练习建议

使用 Markdown 表格输出。"""
    )
)
