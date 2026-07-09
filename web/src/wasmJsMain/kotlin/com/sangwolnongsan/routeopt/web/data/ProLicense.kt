package com.sangwolnongsan.routeopt.web.data

/**
 * 유료(PRO) 이용권 코드 검증 — 오프라인 체크섬.
 *
 * 규칙: 영숫자만 추출·대문자화 → 길이 8자 이상, 문자코드 합 % 26 == 7.
 * 코드 발급(운영자): 아무 문구 7자 이상을 정하고, 합이 조건을 만족하도록
 * 마지막 글자(A~Z)를 조정한다.
 *
 * ⚠️ 클라이언트 검증이므로 완전한 보안이 아니라 접근 제어용 게이트다.
 * 실질적 통제는 티맵 앱키를 유료 고객에게만 전달하는 것으로 이뤄진다.
 * 추후 서버 검증(예: Firebase)으로 교체 가능하도록 이 객체에 격리해 둔다.
 */
object ProLicense {
    fun isValid(code: String): Boolean {
        val s = code.filter { it.isLetterOrDigit() }.uppercase()
        return s.length >= 8 && s.sumOf { it.code } % 26 == 7
    }
}
