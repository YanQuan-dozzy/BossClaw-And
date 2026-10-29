package com.bossclaw.android.domain

object SafetyGuard {

    const val GREETING_MIN_CHARS = 120
    const val GREETING_TARGET_CHARS = 150
    const val MAX_GREETING_LENGTH = 250
    const val MAX_REPLY_LENGTH = 250
    const val REPLY_MIN_LENGTH = 8

    private const val OPENING_PREFIX = "您好，我想应聘贵公司的"

    private val AI_MENTIONS = listOf(
        "AI 助手", "AI助手", "人工智能", "自动投递", "机器人", "BossClaw", "bossclaw", "系统自动"
    )
    private val RECRUITER_TONE = Regex(
        "看到你的简历|你的简历|你的经历很匹配我们|很匹配我们|匹配我们|欢迎.*沟通|期待你|候选人|我们团队|我们公司|团队主要涉及|你很匹配"
    )
    private val FABRICATED_COMMIT = Regex(
        "薪资.{0,8}(要求|期望|多少|是|给)|期望薪资|实习工资|可到岗|现在(就)?入职|到岗时间|随时到岗|面试时间|可以面试|安排面试"
    )
    private val SCHOOL_TIER_WORDS = listOf("985", "211", "双一流", "名校", "重点大学", "重点院校")

    fun sanitizeGreeting(raw: String): String = clampGreetingText(clean(raw))

    fun sanitizeReply(raw: String): String = clampGreetingText(clean(raw), MAX_REPLY_LENGTH)

    /**
     * 单行化 + 智能截断：
     * 先压成单行（BOSS 聊天框按回车发送，多行只会发出前半句），
     * 超长时按「句末标点 → 逗号 → 硬切」三级回退，避免截断在半个词中间。
     */
    fun clampGreetingText(text: String, maxChars: Int = MAX_GREETING_LENGTH): String {
        val oneLine = text.trim().replace(Regex("""\s+"""), " ")
        if (oneLine.length <= maxChars) return oneLine
        val head = oneLine.substring(0, maxChars)
        fun lastIndex(chars: List<String>): Int = chars.maxOfOrNull { head.lastIndexOf(it) } ?: -1
        val sentenceIdx = lastIndex(listOf("。", "！", "？", "；", "!", "?", ";"))
        val clauseIdx = lastIndex(listOf("，", ","))
        val sentenceThreshold = (maxChars * 0.6f).toInt()
        if (sentenceIdx >= 8 && sentenceIdx >= sentenceThreshold) return head.substring(0, sentenceIdx + 1)
        if (clauseIdx >= 8 && clauseIdx > sentenceIdx) {
            return head.substring(0, clauseIdx).trimEnd() + "。"
        }
        if (sentenceIdx >= 8) return head.substring(0, sentenceIdx + 1)
        return head
    }

    /** 补上统一的求职者开场锚点；模型已给出等价开头时保持不变。 */
    fun ensureOpeningAnchor(greeting: String, jobTitle: String): String {
        val text = greeting.trim()
        if (text.startsWith("您好，我想应聘")) return text
        val title = jobTitle.trim().ifBlank { "该" }
        return "$OPENING_PREFIX${title}岗位。$text"
    }

    fun hasFabricatedCommit(text: String): Boolean = FABRICATED_COMMIT.containsMatchIn(text)

    fun greetingProblem(message: String, resume: String): String? {
        if (message.isBlank()) return "打招呼内容为空"
        if (message.length > MAX_GREETING_LENGTH) return "超过 $MAX_GREETING_LENGTH 字长度限制"
        if (AI_MENTIONS.any { message.contains(it, ignoreCase = true) }) return "包含 AI / 自动投递字眼"
        if (RECRUITER_TONE.containsMatchIn(message)) return "出现招聘方口吻或面试官视角表述"
        if (hasFabricatedCommit(message)) return "包含无依据的薪资 / 到岗 / 面试安排"
        val usedTier = SCHOOL_TIER_WORDS.filter { message.contains(it) }
        if (usedTier.isNotEmpty() && SCHOOL_TIER_WORDS.none { resume.contains(it) }) {
            return "包含未经核验的院校层级表述：${usedTier.joinToString("、")}"
        }
        return null
    }

    /** 长度偏短不阻断发送，只提示重生成（低于 120 字视为信息量不足）。 */
    fun greetingWarning(message: String): String? {
        if (message.isBlank()) return null
        return if (message.length < GREETING_MIN_CHARS) {
            "问候语偏短（${message.length} 字），建议 ${GREETING_MIN_CHARS}-$MAX_GREETING_LENGTH 字"
        } else {
            null
        }
    }

    /**
     * @param userFacts 用户在「沟通信息」里填写的真实信息；命中的承诺类别必须能在其中找到，
     *                  避免 AI 把没填过的薪资 / 到岗 / 面试安排说成事实。
     * @param checkCommitments 手动发送时由用户本人确认内容，可跳过承诺校验。
     */
    fun replyProblem(
        message: String,
        userFacts: String = "",
        checkCommitments: Boolean = true
    ): String? {
        if (message.isBlank()) return "回复内容为空"
        if (message.length < REPLY_MIN_LENGTH) return "回复过短（少于 $REPLY_MIN_LENGTH 字）"
        if (AI_MENTIONS.any { message.contains(it, ignoreCase = true) }) return "包含 AI / 自动投递字眼"
        if (RECRUITER_TONE.containsMatchIn(message)) return "出现招聘方口吻或面试官视角表述"
        if (checkCommitments && hasFabricatedCommit(message) && !factsCoverCommitments(message, userFacts)) {
            return "包含无依据的薪资 / 到岗 / 面试安排"
        }
        return null
    }

    /** 承诺类别 → 识别用关键词；回复里出现的类别必须在用户填写的信息中有对应表述。 */
    private val COMMIT_CATEGORIES: List<List<String>> = listOf(
        listOf("薪资", "期望", "工资", "K", "k", "万", "元"),
        listOf("面试"),
        listOf("到岗", "入职", "随时"),
        listOf("offer", "Offer")
    )

    private fun factsCoverCommitments(message: String, userFacts: String): Boolean {
        val facts = userFacts.trim()
        if (facts.isBlank()) return false
        val missing = COMMIT_CATEGORIES.filter { category ->
            category.any { message.contains(it) } &&
                category.none { facts.contains(it) }
        }
        return missing.isEmpty()
    }

    fun localReply(jobTitle: String): String {
        val title = jobTitle.trim()
        return if (title.isNotBlank()) {
            "您好，谢谢您联系我。我想了解更多关于「$title」岗位的具体工作内容，希望能多沟通，谢谢。"
        } else {
            "您好，谢谢您联系我。我想进一步了解岗位的具体工作内容，希望能多沟通，谢谢。"
        }
    }

    fun riskyKeywords(message: String): List<String> {
        val signals = mutableListOf<String>()
        if (message.contains("外包") || message.contains("驻场") || message.contains("派遣")) signals += "外包/驻场"
        if (message.contains("押金") || message.contains("培训费") || message.contains("培训贷") ||
            message.contains("先交费") || message.contains("收费")
        ) signals += "收费/押金/培训贷"
        if (message.contains("单休") || message.contains("大小周")) signals += "单休/大小周"
        if (message.contains("贷款") || message.contains("刷单") || message.contains("博彩") || message.contains("境外")) {
            signals += "高风险业务"
        }
        if (Regex("""试用[期薪]{1,2}.*(80%|八折)""").containsMatchIn(message)) signals += "试用期薪资打折"
        return signals
    }

    private fun clean(raw: String): String {
        var text = raw.trim()
            .replace("\r", "")
            .replace(Regex("""[`*_#>]"""), "")
            .replace(Regex("""^(打招呼|回复|消息)[：:]\s*"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        AI_MENTIONS.forEach { mention -> text = text.replace(mention, "", ignoreCase = true) }
        text = text.trim().trim('"', '“', '”')
        return text
    }
}
