package kr.driveguard.core

enum class VoiceIntent { HELP, OK, END }

/**
 * 인식된 문장에서 호출·응답·종료만 고른다. 증상을 해석하지 않는다.
 * "도와줘"와 "가슴이 아파"는 같은 HELP다. 앱 자신의 질문 "괜찮으세요?"는 응답으로 보지 않는다.
 */
object Keywords {
    private val help = listOf("도와", "살려", "아파")
    private val ok = listOf("괜찮아", "괜찮습", "괜찮다")
    private val end = listOf("종료")

    fun classify(text: String): VoiceIntent? {
        val t = text.trim().trimEnd('.', '!', '?')
        if (t.isEmpty()) return null
        return when {
            end.any { it in t } -> VoiceIntent.END
            ok.any { it in t } || t == "네" -> VoiceIntent.OK
            help.any { it in t } -> VoiceIntent.HELP
            else -> null
        }
    }
}
