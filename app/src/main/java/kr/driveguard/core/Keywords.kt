package kr.driveguard.core

enum class VoiceIntent { HELP, OK, CANCEL, END }

/**
 * 인식된 문장에서 호출·응답·취소·종료만 고른다. 증상을 해석하지 않는다.
 * "도와줘"와 "가슴이 아파"는 같은 HELP다. 앱 자신의 질문 "괜찮으세요?"는 응답으로 보지 않는다.
 * CANCEL은 "괜찮아 취소해줘"처럼 "괜찮"과 "취소"가 함께 있을 때다. 응답 없음 확정 뒤에는 이것만 취소로 받아,
 * 구조자가 운전자에게 "괜찮아요?"라고 묻는 말로 흐름이 멈추지 않게 한다. 누가 말했는지는 구분하지 못한다.
 */
object Keywords {
    private val help = listOf("도와", "살려", "아파")
    private val ok = listOf("괜찮아", "괜찮습", "괜찮다")
    private val end = listOf("종료")
    private const val CANCEL_WORD = "취소"
    private const val CANCEL_PAIR = "괜찮"

    fun classify(text: String): VoiceIntent? {
        val t = text.trim().trimEnd('.', '!', '?')
        if (t.isEmpty()) return null
        return when {
            end.any { it in t } -> VoiceIntent.END
            CANCEL_PAIR in t && CANCEL_WORD in t -> VoiceIntent.CANCEL
            ok.any { it in t } || t == "네" -> VoiceIntent.OK
            help.any { it in t } -> VoiceIntent.HELP
            else -> null
        }
    }
}
