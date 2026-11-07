package kr.driveguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordsTest {
    @Test fun `도움 표현은 증상과 무관하게 모두 같은 호출이다`() {
        for (text in listOf("도와줘", "도와주세요", "살려줘", "가슴이 아파", "가슴이 아파요", "머리가 아파요", "배가 아파")) {
            assertEquals(text, VoiceIntent.HELP, Keywords.classify(text))
        }
    }

    @Test fun `응답 표현`() {
        for (text in listOf("괜찮아요", "괜찮아", "괜찮습니다", "네")) {
            assertEquals(text, VoiceIntent.OK, Keywords.classify(text))
        }
    }

    @Test fun `앱의 질문이 그대로 들어와도 응답으로 보지 않는다`() {
        assertNull(Keywords.classify("괜찮으세요?"))
    }

    @Test fun `종료`() = assertEquals(VoiceIntent.END, Keywords.classify("안내 종료"))

    @Test fun `부분 결과와 다른 말은 무시한다`() {
        for (text in listOf("", " ", "가", "가슴이", "오늘 날씨", "네비 켜 줘")) assertNull(text, Keywords.classify(text))
    }

    @Test fun `문장 부호를 무시한다`() = assertEquals(VoiceIntent.OK, Keywords.classify("네."))
}
