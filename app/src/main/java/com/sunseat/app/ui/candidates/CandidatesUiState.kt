package com.sunseat.app.ui.candidates

import androidx.compose.runtime.saveable.Saver
import com.sunseat.app.ui.map.FitReason

/*
 * 후보 화면 상태 모델(#171, docs/bug-analysis-2026-11.md 4.2).
 *
 * 시트 자리·미리보기·결과에서 돌아오기·시트 움직임·방향·시트 앵커에 쓰는 측정값을 상태 하나에 두고,
 * 화면은 이벤트만 보낸다. 전환 함수 [reduceCandidates]가 다음 상태와 그 전환에 필요한 효과 목록을 함께 돌려주므로
 * 이벤트마다 효과를 따로 붙이다 빠뜨리는 일(#136 범주 스크롤, #145 시트 높이, #158 접근성 초점)이 생기지 않는다.
 *
 * 상태에 두지 않는 것:
 * - 선택·범주·정렬: ViewModel([CandidatesState.Options])이 유일한 출처다. 바뀌면 이벤트로 알린다.
 * - 목록 스크롤 위치: LazyListState가 저장·복원한다(#99). 상태는 스크롤을 바꿀 때만 효과([CandidatesEffect.ScrollToTop],
 *   [CandidatesEffect.Reveal])로 요청한다.
 * - 카메라 모드: 지도 카메라 정책(#168, MapCameraPolicy)이 소유한다. 화면은 맞춤 이유만 [CandidatesEffect.Camera]로 요청한다.
 */

/** 목록 시트의 자리(세로). 가로(#144)에는 시트가 없다. */
internal enum class SheetAnchor { Default, Expanded }

internal enum class ScreenOrientation { Portrait, Landscape }

/** 시트가 손가락으로 끌리거나 정착·복원 애니메이션 중이면 [Moving]. */
internal enum class SheetGesture { Idle, Moving }

/** 미리보기를 닫은 경로. 효과는 같다(DESIGN 12.3 Back 우선순위 ④). */
internal enum class CloseSource { Back, Button }

/** 미리보기 대상 후보와 들어가기 전 목록 시트 자리(#145). */
internal data class PreviewEntry(val id: Int, val sheetBefore: SheetAnchor)

/**
 * @property sheet 목록 시트가 마지막으로 향한 자리(끌기 중에는 가장 가까운 앵커). 미리보기·복원 중에는 바뀌지 않는다.
 * @property preview 열린 미리보기.
 * @property away 미리보기에서 "자리 보기"로 결과에 갔다. 돌아오면(새 구성) 이 값으로 목록을 되돌린다.
 * @property restoring 미리보기를 닫거나 결과에서 돌아와 시트를 이 자리로 되돌리는 중. 완전 확장으로 되돌리는 동안에는
 *   시트가 잠깐 기본 앵커를 지나도 축약 여정 바를 유지한다(#101).
 * @property journeyBar 시트 최대 높이(완전 확장 앵커)를 정하는 축약 여정 바 높이(dp). 시트가 움직이는 동안에는 바뀌지 않는다(#162).
 * @property pendingJourneyBar 움직이는 동안 새로 잰 높이. 시트가 멈추면 [journeyBar]에 반영한다.
 * @property transition 시트를 움직이는 전환의 번호. 끊긴 앞 전환의 [CandidatesEvent.SheetAnimated]가 뒤 전환의 복원 상태를 지우지 않게 한다.
 */
internal data class CandidatesUiState(
    val sheet: SheetAnchor = SheetAnchor.Default,
    val preview: PreviewEntry? = null,
    val away: PreviewEntry? = null,
    val restoring: SheetAnchor? = null,
    val gesture: SheetGesture = SheetGesture.Idle,
    val orientation: ScreenOrientation = ScreenOrientation.Portrait,
    val journeyBar: Float = 0f,
    val pendingJourneyBar: Float? = null,
    val transition: Int = 0,
) {
    val landscape: Boolean get() = orientation == ScreenOrientation.Landscape

    /** 목록 상단을 축약 여정 바로 고정해야 하는 복원 중인가(#101). 결과에서 돌아온 첫 프레임도 포함한다. 가로에는 시트가 없어 화면이 쓰지 않는다. */
    val holdCompactHeader: Boolean get() = restoring == SheetAnchor.Expanded
}

internal sealed interface CandidatesEvent {
    /** 후보 행 누름: 미리보기 열기(#84 같은 행 반복 포함). */
    data class CardTap(val id: Int) : CandidatesEvent
    data class PreviewClose(val source: CloseSource) : CandidatesEvent
    /** 미리보기의 "자리 보기"로 결과로 떠남. */
    data object SeatOpen : CandidatesEvent
    /** 결과에서 Back으로 돌아와 화면이 새로 구성됨([CandidatesUiState.away]가 있을 때 한 번). */
    data object ReturnFromResult : CandidatesEvent
    /** 목록 시트가 향하는 자리(끌기 중 가장 가까운 앵커 포함). */
    data class SheetTarget(val anchor: SheetAnchor) : CandidatesEvent
    /** 시트 움직임 시작(끌기·애니메이션)과 멈춤. */
    data class SheetMotion(val moving: Boolean) : CandidatesEvent
    /** [CandidatesEffect.AnimateSheet]가 끝남(중단 포함). [transition]이 지금 전환이 아니면 무시한다. */
    data class SheetAnimated(val anchor: SheetAnchor, val transition: Int = 0) : CandidatesEvent
    /** 축약 여정 바 높이(dp)를 잼. */
    data class JourneyBarMeasured(val height: Float) : CandidatesEvent
    /** 범주 탭(#82). [changed]는 다른 범주를 골랐는가(빈 범주의 "전체 보기" 포함). */
    data class CategoryChange(val changed: Boolean) : CandidatesEvent
    /** 정렬 기준 선택(#83). */
    data class SortChange(val changed: Boolean) : CandidatesEvent
    data class Rotate(val orientation: ScreenOrientation) : CandidatesEvent
    /** "지도 맞춤" 행동(#102 #141). */
    data object FitAction : CandidatesEvent
    /** 후보 집합·선택이 바뀜(첫 구성 포함). */
    data object CandidatesChanged : CandidatesEvent
    /** 완전 확장의 축약 여정 바 누름: 기본 높이로 내려 펼친 헤더를 보인다. */
    data object JourneyBarTap : CandidatesEvent
    /** 여정(출발·도착·수단)이 같은 화면에서 바뀜: 미리보기·결과 왕복을 버린다. */
    data object TripChanged : CandidatesEvent
    /** 열린 미리보기의 후보가 목록에서 사라짐(다시 불러오기 등): 미리보기를 닫은 것으로 본다. */
    data object PreviewLost : CandidatesEvent
}

internal sealed interface CandidatesEffect {
    /** 목록·미리보기 시트를 [anchor]로 움직인다(세로만). 끝나면 [CandidatesEvent.SheetAnimated]. */
    data class AnimateSheet(val anchor: SheetAnchor, val transition: Int = 0) : CandidatesEffect
    /** 목록 맨 위(새 1순위)부터 보인다(#83 #136). */
    data object ScrollToTop : CandidatesEffect
    /** 후보 [id] 카드가 화면 밖이면 보이게 옮기고, 보이면 위치를 그대로 둔다(#145 #99). */
    data class Reveal(val id: Int) : CandidatesEffect
    /** 접근성(화면 읽기·키보드) 초점을 후보 [id] 카드로(#158). */
    data class FocusCard(val id: Int) : CandidatesEffect
    /** 카메라 맞춤 요청. 실행 여부는 카메라 정책이 모드로 정한다(#168, DESIGN 12.3). */
    data class Camera(val reason: FitReason) : CandidatesEffect
}

internal data class CandidatesTransition(val state: CandidatesUiState, val effects: List<CandidatesEffect> = emptyList())

/** 전환 함수. 순수 함수이며 같은 상태·이벤트에는 같은 결과를 낸다. 시트를 움직이는 전환에는 새 전환 번호를 붙인다. */
internal fun reduceCandidates(state: CandidatesUiState, event: CandidatesEvent): CandidatesTransition {
    val next = transition(state, event)
    if (next.effects.none { it is CandidatesEffect.AnimateSheet }) return next
    val number = state.transition + 1
    return CandidatesTransition(next.state.copy(transition = number), next.effects.map { if (it is CandidatesEffect.AnimateSheet) it.copy(transition = number) else it })
}

private fun transition(state: CandidatesUiState, event: CandidatesEvent): CandidatesTransition = when (event) {
    is CandidatesEvent.CardTap -> if (state.preview != null) CandidatesTransition(state) else CandidatesTransition(
        state.copy(preview = PreviewEntry(event.id, state.sheet), away = null, restoring = null),
        // 미리보기는 요약 높이(기본 앵커)로 열고 선택 경로에 맞춘다(#102). 사용자가 옮긴 지도도 행 탭에는 맞춘다(#71).
        listOfNotNull(CandidatesEffect.AnimateSheet(SheetAnchor.Default).takeUnless { state.landscape }, CandidatesEffect.Camera(FitReason.PREVIEW_OPEN)),
    )
    is CandidatesEvent.PreviewClose -> state.preview?.let { entry ->
        // #145 들어가기 전 시트 높이, 선택 카드 보이기, #158 선택 카드로 초점, #135 지도 띠에 다시 맞춤(수동 이동 뒤에는 정책이 거른다).
        CandidatesTransition(state.copy(preview = null, restoring = entry.sheetBefore.takeUnless { state.landscape }), restoreEffects(state, entry) + CandidatesEffect.Camera(FitReason.PREVIEW_CLOSE))
    } ?: CandidatesTransition(state)
    // 결과로 떠나는 동안(화면 전환 중) 목록 시트를 들어가기 전 높이로 되돌려 둔다. [CandidatesUiState.away]는 저장되어
    // 돌아왔을 때(새 구성) [CandidatesEvent.ReturnFromResult]가 시트 높이(전환 중에 다 못 되돌렸으면)·선택 카드·초점을 마저 맞춘다.
    CandidatesEvent.SeatOpen -> state.preview?.let { entry ->
        CandidatesTransition(state.copy(preview = null, away = entry, restoring = entry.sheetBefore.takeUnless { state.landscape }),
            listOfNotNull(CandidatesEffect.AnimateSheet(entry.sheetBefore).takeUnless { state.landscape }))
    } ?: CandidatesTransition(state)
    // 결과에서 돌아오면 미리보기는 닫힌 목록으로, 들어가기 전 시트 높이와 스크롤을 보존한다(DESIGN 12.3). 카메라는 새 구성의 첫 맞춤이 맡는다.
    CandidatesEvent.ReturnFromResult -> state.away?.let { entry ->
        CandidatesTransition(state.copy(away = null, restoring = entry.sheetBefore.takeUnless { state.landscape }), restoreEffects(state, entry))
    } ?: CandidatesTransition(state)
    is CandidatesEvent.SheetTarget ->
        // 미리보기 시트·복원 애니메이션·가로의 시트 값은 목록 시트 자리가 아니다.
        if (state.preview != null || state.restoring != null || state.landscape) CandidatesTransition(state)
        else CandidatesTransition(state.copy(sheet = event.anchor))
    is CandidatesEvent.SheetMotion -> CandidatesTransition(
        if (event.moving) state.copy(gesture = SheetGesture.Moving) else state.copy(gesture = SheetGesture.Idle).settleMeasurements(),
    )
    is CandidatesEvent.SheetAnimated -> CandidatesTransition(
        if (event.transition != state.transition) state else state.restoring?.let { state.copy(restoring = null, sheet = if (state.preview == null) it else state.sheet).settleMeasurements() } ?: state,
    )
    // #162: 시트 앵커(완전 확장 높이)는 시트가 움직이는 동안 바뀌지 않는다. 새 측정은 멈춘 뒤 반영한다.
    is CandidatesEvent.JourneyBarMeasured -> CandidatesTransition(
        if (state.gesture == SheetGesture.Moving || state.restoring != null) state.copy(pendingJourneyBar = event.height.takeIf { it != state.journeyBar })
        else state.copy(journeyBar = event.height, pendingJourneyBar = null),
    )
    is CandidatesEvent.CategoryChange -> CandidatesTransition(state, listOfNotNull(CandidatesEffect.ScrollToTop.takeIf { event.changed }))
    is CandidatesEvent.SortChange -> CandidatesTransition(state, listOfNotNull(CandidatesEffect.ScrollToTop.takeIf { event.changed }))
    is CandidatesEvent.Rotate -> if (event.orientation == state.orientation) CandidatesTransition(state) else {
        // 같은 상태를 다른 배치로 그린다(#144). 가로에는 시트가 없어 복원할 것이 없고, 세로로 돌아온 미리보기는 요약 높이로 연다.
        val next = state.copy(orientation = event.orientation, restoring = if (event.orientation == ScreenOrientation.Landscape) null else state.restoring)
        CandidatesTransition(next, listOfNotNull(CandidatesEffect.AnimateSheet(SheetAnchor.Default).takeIf { !next.landscape && next.preview != null }))
    }
    CandidatesEvent.FitAction -> CandidatesTransition(state, listOf(CandidatesEffect.Camera(FitReason.EXPLICIT)))
    CandidatesEvent.CandidatesChanged -> CandidatesTransition(state, listOf(CandidatesEffect.Camera(FitReason.SELECTION)))
    CandidatesEvent.JourneyBarTap -> CandidatesTransition(state, listOfNotNull(CandidatesEffect.AnimateSheet(SheetAnchor.Default).takeUnless { state.landscape || state.preview != null }))
    CandidatesEvent.TripChanged -> CandidatesTransition(state.copy(preview = null, away = null, restoring = null))
    CandidatesEvent.PreviewLost -> CandidatesTransition(state.copy(preview = null))
}

/** 목록으로 돌아올 때 공통 효과: 들어가기 전 시트 높이(세로), 선택 카드 보이기, 선택 카드로 초점. */
private fun restoreEffects(state: CandidatesUiState, entry: PreviewEntry): List<CandidatesEffect> = listOfNotNull(
    CandidatesEffect.AnimateSheet(entry.sheetBefore).takeUnless { state.landscape },
    CandidatesEffect.Reveal(entry.id),
    CandidatesEffect.FocusCard(entry.id),
)

private fun CandidatesUiState.settleMeasurements(): CandidatesUiState =
    if (gesture == SheetGesture.Idle && restoring == null && pendingJourneyBar != null) copy(journeyBar = pendingJourneyBar, pendingJourneyBar = null) else this

/** 회전·재생성·결과 화면 왕복에 남길 값. 측정값·움직임·진행 중인 복원 애니메이션은 새 구성에서 다시 정한다. */
internal val CandidatesUiStateSaver: Saver<CandidatesUiState, Any> = Saver(
    save = { state ->
        arrayListOf(
            state.sheet.ordinal, state.orientation.ordinal,
            state.preview?.id ?: -1, state.preview?.sheetBefore?.ordinal ?: -1,
            state.away?.id ?: -1, state.away?.sheetBefore?.ordinal ?: -1,
        )
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST") val v = saved as List<Int>
        fun entry(id: Int, sheet: Int) = if (id >= 0 && sheet >= 0) PreviewEntry(id, SheetAnchor.entries[sheet]) else null
        CandidatesUiState(
            sheet = SheetAnchor.entries[v[0]], orientation = ScreenOrientation.entries[v[1]],
            preview = entry(v[2], v[3]), away = entry(v[4], v[5]),
        ).let { state ->
            // 결과에서 돌아온 새 구성은 첫 프레임부터 되돌리는 중으로 본다(축약 여정 바 유지, #101). 저장한 방향과 지금 방향이 달라도
            // 첫 프레임에 펼친 헤더가 끼지 않도록 방향과 무관하게 두고, 가로로 돌면 [CandidatesEvent.Rotate]가, 아니면 [CandidatesEvent.ReturnFromResult]가 이어 받는다.
            state.copy(restoring = state.away?.sheetBefore)
        }
    },
)
