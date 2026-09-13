package com.panictracker.app.data

import com.panictracker.app.data.entity.Medication

/**
 * 약을 새로 추가할 때 고를 수 있는 기본값 모음.
 *
 * 여기 적힌 약동학 값은 공개된 문헌·제품설명서의 **대표값**입니다. 실제 수치는 개인의 대사,
 * 병용 약물, 제형에 따라 크게 달라지므로 반드시 본인이 복용 중인 제품의 설명서나
 * 처방의·약사의 안내에 맞게 수정해서 쓰세요. 값은 모두 화면에서 직접 고칠 수 있습니다.
 */
data class MedicationPreset(
    val ingredientName: String,
    val brandName: String,
    val doseMg: Double,
    val halfLifeHours: Double,
    val tmaxHours: Double,
    val onsetMinutes: Int,
    val durationHours: Double,
    val note: String = "",
) {
    fun toMedication(colorArgb: Int) = Medication(
        ingredientName = ingredientName,
        brandName = brandName,
        doseMg = doseMg,
        halfLifeHours = halfLifeHours,
        tmaxHours = tmaxHours,
        onsetMinutes = onsetMinutes,
        durationHours = durationHours,
        colorArgb = colorArgb,
        note = note,
    )
}

object MedicationPresets {

    /**
     * 그래프에서 약마다 돌려 쓰는 색.
     *
     * 색각 이상(적록·청황)에서도 서로 구분되도록 고른 6색입니다 — 모든 색 쌍이 OKLab
     * 색차 ΔE 9.3 이상(적색맹 기준), 정상 색각 기준 16.4 이상으로 떨어져 있습니다.
     * 다크 모드에서는 일부 색의 배경 대비가 3:1 에 조금 못 미치므로, 그래프에는
     * **항상 약 이름이 붙은 범례와 값 목록을 함께** 표시해 색만으로 구분하지 않게 합니다.
     *
     * 7가지 이상의 약을 동시에 볼 때는 색이 반복되므로, 그때는 약 편집 화면에서
     * 색을 직접 골라 주세요.
     */
    val palette: List<Int> = listOf(
        0xFF2460B7.toInt(), // 파랑
        0xFFA63D02.toInt(), // 적갈
        0xFF779F13.toInt(), // 올리브
        0xFF953A83.toInt(), // 자주
        0xFF9678E6.toInt(), // 연보라
        0xFFDC5F80.toInt(), // 분홍
    )

    fun colorFor(index: Int): Int = palette[index % palette.size]

    val all: List<MedicationPreset> = listOf(
        MedicationPreset(
            "알프라졸람", "자낙스정", 0.25, 12.0, 1.5, 20, 6.0,
            "속효성 벤조디아제핀. 필요시(PRN) 복용에 흔히 쓰입니다.",
        ),
        MedicationPreset(
            "알프라졸람 서방정", "자낙스엑스알정", 0.5, 12.0, 9.0, 60, 12.0,
            "서방형이라 Tmax 가 깁니다. 속방형과 곡선 모양이 다릅니다.",
        ),
        MedicationPreset(
            "로라제팜", "아티반정", 0.5, 12.0, 2.0, 30, 8.0,
            "간대사 부담이 적은 편으로 알려진 벤조디아제핀.",
        ),
        MedicationPreset(
            "클로나제팜", "리보트릴정", 0.5, 35.0, 2.0, 30, 12.0,
            "반감기가 길어 매일 복용 시 며칠에 걸쳐 축적됩니다.",
        ),
        MedicationPreset(
            "에티졸람", "데파스정", 0.25, 6.0, 3.0, 30, 6.0,
            "티에노디아제핀 계열.",
        ),
        MedicationPreset(
            "디아제팜", "바리움정", 5.0, 43.0, 1.0, 20, 8.0,
            "활성 대사체(데스메틸디아제팜)의 반감기가 더 길어 실제 작용은 더 오래갑니다.",
        ),
        MedicationPreset(
            "에스시탈로프람", "렉사프로정", 10.0, 30.0, 4.0, 60, 24.0,
            "SSRI. 혈중농도는 곡선대로 움직이지만 항불안 효과가 자리잡는 데는 보통 2~4주 걸립니다.",
        ),
        MedicationPreset(
            "파록세틴", "팍실정", 20.0, 21.0, 5.0, 60, 24.0,
            "SSRI. 효과 발현에 수 주가 걸립니다.",
        ),
        MedicationPreset(
            "설트랄린", "졸로푸트정", 50.0, 26.0, 6.0, 60, 24.0,
            "SSRI. 효과 발현에 수 주가 걸립니다.",
        ),
        MedicationPreset(
            "프로프라놀롤", "인데놀정", 10.0, 4.0, 1.5, 30, 4.0,
            "베타차단제. 두근거림·떨림 같은 신체증상에 씁니다.",
        ),
        MedicationPreset(
            "부스피론", "부스파정", 5.0, 2.5, 1.0, 45, 6.0,
            "비벤조계 항불안제.",
        ),
        MedicationPreset(
            "하이드록시진", "아타락스정", 10.0, 20.0, 2.0, 30, 8.0,
            "항히스타민계 항불안제. 졸림이 흔합니다.",
        ),
        MedicationPreset(
            "졸피뎀", "스틸녹스정", 10.0, 2.5, 1.0, 15, 6.0,
            "수면제. 반감기가 짧습니다.",
        ),
        MedicationPreset(
            "트라조돈", "트리티코정", 25.0, 7.0, 1.5, 30, 8.0,
            "저용량에서 수면 목적으로 쓰이기도 합니다.",
        ),
        MedicationPreset(
            "멜라토닌 서방정", "서카딘정", 2.0, 3.5, 3.0, 45, 8.0,
            "서방형 멜라토닌.",
        ),
    )

    /** 목록에 없을 때 쓰는 빈 템플릿. */
    val custom = MedicationPreset(
        ingredientName = "",
        brandName = "",
        doseMg = 1.0,
        halfLifeHours = 12.0,
        tmaxHours = 1.5,
        onsetMinutes = 30,
        durationHours = 6.0,
    )
}
