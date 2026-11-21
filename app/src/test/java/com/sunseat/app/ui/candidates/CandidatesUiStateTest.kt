package com.sunseat.app.ui.candidates

import com.sunseat.app.ui.candidates.CandidatesEffect.AnimateSheet
import com.sunseat.app.ui.candidates.CandidatesEffect.Camera
import com.sunseat.app.ui.candidates.CandidatesEffect.FocusCard
import com.sunseat.app.ui.candidates.CandidatesEffect.Reveal
import com.sunseat.app.ui.candidates.CandidatesEffect.ScrollToTop
import com.sunseat.app.ui.candidates.SheetAnchor.Default
import com.sunseat.app.ui.candidates.SheetAnchor.Expanded
import com.sunseat.app.ui.map.FitReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #171: 후보 화면 전환 함수의 전환표. 상태(기본·완전 확장·미리보기·결과에서 돌아옴·가로) × 이벤트 전부의 다음 상태와 효과를 단언한다.
 * 효과가 빠지면(#136 범주 스크롤, #145 시트 높이, #158 초점, #135 맞춤) 여기서 실패한다.
 */
class CandidatesUiStateTest {
    private val portrait = ScreenOrientation.Portrait
    private val landscape = ScreenOrientation.Landscape

    private val default = CandidatesUiState(sheet = Default, journeyBar = 56f)
    private val expanded = default.copy(sheet = Expanded)
    private val previewFromDefault = default.copy(preview = PreviewEntry(3, Default))
    private val previewFromExpanded = expanded.copy(preview = PreviewEntry(3, Expanded))
    private val awayFromExpanded = expanded.copy(away = PreviewEntry(3, Expanded))
    private val landscapeList = default.copy(orientation = landscape)
    private val landscapePreview = previewFromExpanded.copy(orientation = landscape)

    /** 이름 붙인 상태 목록. 전환표의 행. */
    private val states = mapOf(
        "default" to default, "expanded" to expanded, "previewFromDefault" to previewFromDefault,
        "previewFromExpanded" to previewFromExpanded, "awayFromExpanded" to awayFromExpanded,
        "landscapeList" to landscapeList, "landscapePreview" to landscapePreview,
    )

    /** 모든 이벤트. 전환표의 열. */
    private val events = listOf(
        CandidatesEvent.CardTap(5), CandidatesEvent.PreviewClose(CloseSource.Back), CandidatesEvent.PreviewClose(CloseSource.Button),
        CandidatesEvent.SeatOpen, CandidatesEvent.ReturnFromResult, CandidatesEvent.SheetTarget(Expanded), CandidatesEvent.SheetTarget(Default),
        CandidatesEvent.SheetMotion(true), CandidatesEvent.SheetMotion(false), CandidatesEvent.SheetAnimated(Expanded),
        CandidatesEvent.JourneyBarMeasured(80f), CandidatesEvent.CategoryChange(true), CandidatesEvent.CategoryChange(false),
        CandidatesEvent.SortChange(true), CandidatesEvent.SortChange(false), CandidatesEvent.Rotate(portrait), CandidatesEvent.Rotate(landscape),
        CandidatesEvent.FitAction, CandidatesEvent.CandidatesChanged, CandidatesEvent.JourneyBarTap, CandidatesEvent.TripChanged, CandidatesEvent.PreviewLost,
    )

    /** 전환 번호를 뺀 효과(번호는 [transitionNumbers]가 따로 본다). */
    private fun List<CandidatesEffect>.plain() = map { if (it is AnimateSheet) it.copy(transition = 0) else it }

    /** 기대 효과: 상태 이름·이벤트 → 효과 목록. 표에 없는 칸은 효과 없음. */
    private fun expectedEffects(name: String, state: CandidatesUiState, event: CandidatesEvent): List<CandidatesEffect> {
        val preview = state.preview
        val portraitSheet = !state.landscape
        return when (event) {
            is CandidatesEvent.CardTap -> if (preview != null) emptyList()
                else listOfNotNull(AnimateSheet(Default).takeIf { portraitSheet }, Camera(FitReason.PREVIEW_OPEN))
            is CandidatesEvent.PreviewClose -> if (preview == null) emptyList()
                else listOfNotNull(AnimateSheet(preview.sheetBefore).takeIf { portraitSheet }, Reveal(preview.id), FocusCard(preview.id), Camera(FitReason.PREVIEW_CLOSE))
            CandidatesEvent.SeatOpen -> if (preview == null) emptyList() else listOfNotNull(AnimateSheet(preview.sheetBefore).takeIf { portraitSheet })
            CandidatesEvent.ReturnFromResult -> state.away?.let { listOfNotNull(AnimateSheet(it.sheetBefore).takeIf { portraitSheet }, Reveal(it.id), FocusCard(it.id)) }.orEmpty()
            is CandidatesEvent.CategoryChange -> if (event.changed) listOf(ScrollToTop) else emptyList()
            is CandidatesEvent.SortChange -> if (event.changed) listOf(ScrollToTop) else emptyList()
            is CandidatesEvent.Rotate -> if (event.orientation == portrait && state.landscape && preview != null) listOf(AnimateSheet(Default)) else emptyList()
            CandidatesEvent.FitAction -> listOf(Camera(FitReason.EXPLICIT))
            CandidatesEvent.CandidatesChanged -> listOf(Camera(FitReason.SELECTION))
            CandidatesEvent.JourneyBarTap -> if (portraitSheet && preview == null) listOf(AnimateSheet(Default)) else emptyList()
            CandidatesEvent.TripChanged, CandidatesEvent.PreviewLost -> listOf(CandidatesEffect.CancelPending)
            else -> emptyList()
        }
    }

    @Test fun transitionTableEffects() {
        for ((name, state) in states) for (event in events) {
            assertEquals("$name × $event", expectedEffects(name, state, event), reduceCandidates(state, event).effects.plain())
        }
    }

    @Test fun transitionTableInvariants() {
        for ((name, state) in states) for (event in events) {
            val next = reduceCandidates(state, event)
            val where = "$name × $event"
            // 가로에는 시트가 없다.
            if (next.state.landscape) assertTrue(where, next.effects.none { it is AnimateSheet } && next.state.restoring == null)
            // 미리보기·결과 왕복 중에는 시트 값이 목록 시트 자리를 덮지 않는다(#145).
            if (event is CandidatesEvent.SheetTarget && (state.preview != null || state.restoring != null || state.landscape)) assertEquals(where, state.sheet, next.state.sheet)
            // 목록으로 돌아오면 늘 선택 카드를 보이고 초점을 둔다(#158).
            if (next.effects.any { it is Reveal }) assertTrue(where, next.effects.any { it is FocusCard })
            // 미리보기는 한 번에 하나.
            if (state.preview != null && event is CandidatesEvent.CardTap) assertEquals(where, state, next.state)
        }
    }

    @Test fun cardTapRemembersSheetBeforePreview() {
        assertEquals(PreviewEntry(5, Default), reduceCandidates(default, CandidatesEvent.CardTap(5)).state.preview)
        assertEquals(PreviewEntry(5, Expanded), reduceCandidates(expanded, CandidatesEvent.CardTap(5)).state.preview)
        // 끌기 중 누름: 화면이 가장 가까운 앵커를 먼저 알린다.
        val dragging = reduceCandidates(default, CandidatesEvent.SheetTarget(Expanded)).state
        assertEquals(PreviewEntry(5, Expanded), reduceCandidates(dragging, CandidatesEvent.CardTap(5)).state.preview)
    }

    /** 미리보기 닫기 → 시트 복원 중(축약 여정 바 유지) → 애니메이션 끝: 목록 시트 자리가 들어가기 전 값이다. */
    @Test fun closingRestoresSheetAndHoldsCompactHeaderUntilAnimated() {
        val closing = reduceCandidates(previewFromExpanded, CandidatesEvent.PreviewClose(CloseSource.Back)).state
        assertNull(closing.preview)
        assertEquals(Expanded, closing.restoring)
        assertTrue(closing.holdCompactHeader)
        // 복원 중의 시트 값(기본 앵커를 지남)은 무시한다.
        assertEquals(closing, reduceCandidates(closing, CandidatesEvent.SheetTarget(Default)).state)
        val done = reduceCandidates(closing, CandidatesEvent.SheetAnimated(Expanded, closing.transition)).state
        assertNull(done.restoring)
        assertFalse(done.holdCompactHeader)
        assertEquals(Expanded, done.sheet)
    }

    /** 결과로 떠나는 동안 시트를 되돌리고, 다 되돌리기 전에 화면이 사라지면 돌아와서 마저 되돌린다. */
    @Test fun seatOpenAndReturnFromResult() {
        val leaving = reduceCandidates(previewFromExpanded, CandidatesEvent.SeatOpen).state
        assertEquals(PreviewEntry(3, Expanded), leaving.away)
        assertTrue("first frames of the list keep the compact bar", leaving.holdCompactHeader)
        // 저장·복원(새 구성)에는 측정값·복원 애니메이션이 빠진다.
        @Suppress("UNCHECKED_CAST") val restored = CandidatesUiStateSaver.restore(with(CandidatesUiStateSaver) { androidx.compose.runtime.saveable.SaverScope { true }.save(leaving) }!!)!!
        assertEquals(leaving.copy(journeyBar = 0f, transition = 0), restored)
        assertTrue(restored.holdCompactHeader)
        val back = reduceCandidates(restored, CandidatesEvent.ReturnFromResult)
        assertNull(back.state.away)
        assertEquals(listOf(AnimateSheet(Expanded), Reveal(3), FocusCard(3)), back.effects.plain())
        // 떠나는 동안 애니메이션이 끝나도 돌아올 때 쓸 값은 남긴다.
        assertEquals(PreviewEntry(3, Expanded), reduceCandidates(leaving, CandidatesEvent.SheetAnimated(Expanded, leaving.transition)).state.away)
    }

    /** #162: 시트가 움직이는 동안 잰 축약 여정 바 높이는 앵커에 바로 쓰지 않고 멈춘 뒤 반영한다. */
    @Test fun journeyBarMeasurementWaitsForIdleSheet() {
        val moving = reduceCandidates(default, CandidatesEvent.SheetMotion(true)).state
        val measured = reduceCandidates(moving, CandidatesEvent.JourneyBarMeasured(80f)).state
        assertEquals(56f, measured.journeyBar)
        assertEquals(80f, measured.pendingJourneyBar)
        val idle = reduceCandidates(measured, CandidatesEvent.SheetMotion(false)).state
        assertEquals(80f, idle.journeyBar)
        assertNull(idle.pendingJourneyBar)
        // 복원 애니메이션 중에도 미룬다.
        val restoring = reduceCandidates(previewFromExpanded, CandidatesEvent.PreviewClose(CloseSource.Button)).state
        assertEquals(56f, reduceCandidates(restoring, CandidatesEvent.JourneyBarMeasured(90f)).state.journeyBar)
        assertEquals(56f, reduceCandidates(default, CandidatesEvent.JourneyBarMeasured(56f)).state.journeyBar)
        assertEquals(70f, reduceCandidates(default, CandidatesEvent.JourneyBarMeasured(70f)).state.journeyBar)
    }

    /** 회전은 같은 상태를 다른 배치로: 미리보기·들어가기 전 시트 자리를 지키고, 가로에서는 복원할 시트가 없다(#144). */
    @Test fun rotationKeepsPreviewAndSheetBefore() {
        val turned = reduceCandidates(previewFromExpanded, CandidatesEvent.Rotate(landscape)).state
        assertEquals(previewFromExpanded.preview, turned.preview)
        val back = reduceCandidates(turned, CandidatesEvent.Rotate(portrait))
        assertEquals(previewFromExpanded.preview, back.state.preview)
        assertEquals(listOf(AnimateSheet(Default)), back.effects.plain())
        // 가로에서 닫으면 시트 복원 없이 카드·초점·맞춤만.
        assertEquals(listOf(Reveal(3), FocusCard(3), Camera(FitReason.PREVIEW_CLOSE)), reduceCandidates(turned, CandidatesEvent.PreviewClose(CloseSource.Back)).effects)
        // 복원 중에 가로로 돌리면 복원을 버린다.
        val restoring = reduceCandidates(previewFromExpanded, CandidatesEvent.PreviewClose(CloseSource.Back)).state
        assertNull(reduceCandidates(restoring, CandidatesEvent.Rotate(landscape)).state.restoring)
    }

    @Test fun saverRoundTripsEveryNamedState() {
        for ((name, state) in states) {
            val saved = with(CandidatesUiStateSaver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }!!
            assertEquals(name, state.copy(journeyBar = 0f, pendingJourneyBar = null, restoring = state.away?.sheetBefore, gesture = SheetGesture.Idle, transition = 0), CandidatesUiStateSaver.restore(saved))
        }
    }

    /** 시트를 움직이는 전환마다 새 번호를 붙이고, 끊긴 앞 전환의 애니메이션 끝 알림은 뒤 전환의 복원 상태를 지우지 않는다. */
    @Test fun transitionNumbers() {
        val opened = reduceCandidates(expanded, CandidatesEvent.CardTap(1))
        assertEquals(expanded.transition + 1, opened.state.transition)
        assertEquals(opened.state.transition, (opened.effects.first() as AnimateSheet).transition)
        val closing = reduceCandidates(opened.state, CandidatesEvent.PreviewClose(CloseSource.Back))
        val first = (closing.effects.first() as AnimateSheet).transition
        // 복원 중 다른 카드 → 다시 닫기: 첫 닫기의 늦은 알림은 무시한다.
        val reopened = reduceCandidates(closing.state, CandidatesEvent.CardTap(2)).state
        val closingAgain = reduceCandidates(reopened, CandidatesEvent.PreviewClose(CloseSource.Button)).state
        assertEquals(closingAgain, reduceCandidates(closingAgain, CandidatesEvent.SheetAnimated(Expanded, first)).state)
        assertNull(reduceCandidates(closingAgain, CandidatesEvent.SheetAnimated(Expanded, closingAgain.transition)).state.restoring)
        // 시트를 움직이지 않는 전환은 번호를 바꾸지 않는다.
        assertEquals(expanded.transition, reduceCandidates(expanded, CandidatesEvent.FitAction).state.transition)
    }

    /** 같은 화면에서 여정이 바뀌거나 미리보기 후보가 사라지면 미리보기·결과 왕복을 버리고 행 탭이 다시 먹는다. */
    @Test fun tripChangeAndLostPreviewClearPreview() {
        val restoring = reduceCandidates(previewFromExpanded, CandidatesEvent.PreviewClose(CloseSource.Back)).state
        val changed = reduceCandidates(restoring, CandidatesEvent.TripChanged).state
        assertNull(changed.preview); assertNull(changed.away); assertNull(changed.restoring)
        val lost = reduceCandidates(previewFromDefault, CandidatesEvent.PreviewLost).state
        assertNull(lost.preview)
        assertEquals(PreviewEntry(7, Default), reduceCandidates(lost, CandidatesEvent.CardTap(7)).state.preview)
    }

    /** 복원 중에 축약 여정 바를 누르면 복원을 그만두고, 애니메이션이 끝난 자리(기본)를 시트 자리로 기록한다. */
    @Test fun journeyBarTapDuringRestore() {
        val restoring = reduceCandidates(previewFromExpanded, CandidatesEvent.PreviewClose(CloseSource.Back)).state
        val tapped = reduceCandidates(restoring, CandidatesEvent.JourneyBarTap)
        assertNull(tapped.state.restoring)
        assertFalse(tapped.state.holdCompactHeader)
        val done = reduceCandidates(tapped.state, CandidatesEvent.SheetAnimated(Default, tapped.state.transition)).state
        assertEquals(Default, done.sheet)
    }

    /** 여정 변경·사라진 미리보기는 앞 전환의 남은 효과를 버리도록 새 전환 번호와 취소 효과를 낸다. */
    @Test fun tripChangeCancelsPendingEffects() {
        val closing = reduceCandidates(previewFromExpanded, CandidatesEvent.PreviewClose(CloseSource.Back)).state
        val changed = reduceCandidates(closing, CandidatesEvent.TripChanged)
        assertEquals(listOf(CandidatesEffect.CancelPending), changed.effects)
        assertEquals(closing.transition + 1, changed.state.transition)
        assertEquals(changed.state, reduceCandidates(changed.state, CandidatesEvent.SheetAnimated(Expanded, closing.transition)).state)
    }
}
