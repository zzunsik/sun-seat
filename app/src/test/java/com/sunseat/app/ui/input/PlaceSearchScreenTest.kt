package com.sunseat.app.ui.input

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.sunseat.app.R
import com.sunseat.app.data.favorites.Favorite
import com.sunseat.app.data.favorites.FavoriteKind
import com.sunseat.app.data.place.Place
import com.sunseat.app.data.recent.RecentPlace
import com.sunseat.app.domain.LatLng
import com.sunseat.app.domain.TravelMode
import com.sunseat.app.ui.theme.SunSeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 장소 검색 화면의 상태별 표시와 동작. 값은 모두 직접 만든 것이다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class PlaceSearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()
    private val actions = InputActions(
        closeSearch = { calls += "close" },
        searchNow = { calls += "retry" },
        choosePlace = { calls += "choose:" + it.name },
        chooseFavorite = { calls += "favorite:" + it.placeName },
        toggleFavorite = { calls += "star:" + it.name },
        manageFavorites = { calls += "manage:$it" },
        useCurrentLocation = { calls += "location" },
        chooseRecent = { calls += "recent:" + it.name },
    )
    private val station = Place("1", "예시역", "가상구 1", "가상로 1", LatLng(37.51, 127.0), "교통 > 지하철")
    private val cafe = Place("2", "견본 카페", "가상구 2", "", LatLng(37.5, 127.0), "음식점 > 카페")
    private val home = Favorite("h", FavoriteKind.HOME, "", "우리집", "가상구 3", 37.6, 127.1, 0)
    private val recent = RecentPlace("r", "지난번 카페", "가상구 4", 37.4, 127.2, TravelMode.TRANSIT, 0)

    private fun show(
        search: SearchState,
        favorites: List<Favorite> = emptyList(),
        showFavorites: Boolean = true,
        showLocation: Boolean = true,
        recentPlaces: List<RecentPlace> = emptyList(),
        near: LatLng? = null,
    ) {
        compose.setContent {
            SunSeatTheme(darkTheme = false) {
                PlaceSearchScreen(search, actions, favorites = favorites, showFavorites = showFavorites, showLocation = showLocation, recent = recentPlaces, showRecent = true, near = near)
            }
        }
        compose.waitForIdle()
    }

    private fun tap(text: String) = compose.onNode(hasClickAction() and hasText(text)).performSemanticsAction(SemanticsActions.OnClick)
    private val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test fun emptyOriginQueryOffersCurrentLocationFavoritesAndRecent() {
        show(SearchState(EndpointField.ORIGIN), favorites = listOf(home), recentPlaces = listOf(recent))
        compose.onNodeWithText("출발지 이름이나 주소").assertExists()
        tap("현재 위치")
        tap("지난번 카페")
        // 집은 칩과 목록 두 곳, 회사는 아직 없어 설정 버튼
        compose.onAllNodes(hasClickAction() and hasText("집"))[0].performSemanticsAction(SemanticsActions.OnClick)
        tap("회사 설정")
        assertEquals(listOf("location", "recent:지난번 카페", "favorite:우리집", "manage:WORK"), calls)
        compose.onNode(progress).assertDoesNotExist()
    }

    @Test fun destinationHasNoCurrentLocationAndEmptyRecentMessage() {
        show(SearchState(EndpointField.DESTINATION))
        compose.onNodeWithText("도착지 이름이나 주소").assertExists()
        compose.onNodeWithText("현재 위치").assertDoesNotExist()
        compose.onNodeWithText("최근 고른 장소가 없어요.", substring = true).assertExists()
        tap("즐겨찾기 편집")
        assertEquals(listOf("manage:null"), calls)
    }

    @Test fun typedQueryHidesShortcutsAndShowsLoading() {
        show(SearchState(EndpointField.ORIGIN, query = "예시", loading = true), favorites = listOf(home), recentPlaces = listOf(recent))
        compose.onNode(progress).assertExists()
        compose.onNodeWithText("현재 위치").assertDoesNotExist()
        compose.onNodeWithText("지난번 카페").assertDoesNotExist()
        // 즐겨찾기 목록 줄(주소 표시)은 숨기고 위쪽 칩만 남는다
        compose.onNodeWithText(home.address).assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText("집")).assertExists()
        compose.onNodeWithContentDescription("검색어 지우기").assertExists()
    }

    @Test fun resultsChooseAndStarReflectSavedState() {
        val saved = Favorite("s", FavoriteKind.CUSTOM, "견본 카페", "견본 카페", "가상구 2", 37.5, 127.0, 0)
        show(SearchState(EndpointField.DESTINATION, query = "예", results = listOf(station, cafe)), favorites = listOf(saved), near = LatLng(37.5, 127.0))
        // 분류 마지막 단계 · 도로명 주소 우선 · 출발지와 거리
        compose.onNodeWithText("지하철 · 가상로 1 · 1.1km").assertExists()
        compose.onNodeWithText("카페 · 가상구 2 · 0m").assertExists()
        compose.onNodeWithContentDescription("즐겨찾기에 추가").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("즐겨찾기에서 빼기").assertExists()
        tap("예시역")
        assertEquals(listOf("star:예시역", "choose:예시역"), calls)
    }

    @Test fun errorWithRetryAndNoResultMessage() {
        show(SearchState(EndpointField.DESTINATION, query = "예시", messageRes = R.string.search_error_network, canRetry = true))
        compose.onNodeWithText("인터넷에 연결하지 못했어요.", substring = true).assertExists()
        tap("다시 시도")
        assertEquals(listOf("retry"), calls)
    }

    @Test fun nonRetryableErrorAndNoResultsHideRetry() {
        show(SearchState(EndpointField.DESTINATION, query = "없는곳 ", messageRes = R.string.search_no_results))
        compose.onNodeWithText("‘없는곳’ 검색 결과가 없어요.", substring = true).assertExists()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
    }

    @Test fun favoriteSetupSearchUsesHintAndHidesStarsAndLocation() {
        show(SearchState(EndpointField.DESTINATION, query = "", results = listOf(station), hintRes = R.string.favorites_search_home), showFavorites = false, showLocation = false)
        compose.onNodeWithText("집으로 지정할 장소 검색").assertExists()
        compose.onNodeWithText("즐겨찾기 편집").assertDoesNotExist()
        compose.onNodeWithContentDescription("즐겨찾기에 추가").assertDoesNotExist()
        compose.onNodeWithContentDescription("검색 닫기").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("close"), calls)
    }
}
