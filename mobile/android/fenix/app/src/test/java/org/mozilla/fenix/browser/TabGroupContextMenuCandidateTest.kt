/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.view.View
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.snackbar.Snackbar
import io.mockk.mockk
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.tabgroups.storage.data.TabGroup
import mozilla.components.feature.tabgroups.storage.data.TabGroupData
import mozilla.components.feature.tabgroups.storage.fakes.FakeTabGroupRepository
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.ui.widgets.SnackbarDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R
import org.mozilla.fenix.components.usecases.TabGroupUseCases
import org.mozilla.fenix.tabstray.data.TabGroupTheme

@RunWith(AndroidJUnit4::class)
class TabGroupContextMenuCandidateTest {

    private val snackbarParentView: View = mockk(relaxed = true)
    private val snackbarDelegate = FakeSnackbarDelegate()

    @Test
    fun `WHEN tab groups are enabled and a link is long-pressed in a normal tab THEN show the candidate`() {
        assertTrue(
            TabGroupContextMenuCandidate.shouldShowCandidate(
                tab = createTab(url = "https://mozilla.org"),
                hitResult = HitResult.UNKNOWN(LINK_URL),
                tabGroupsEnabled = true,
            )
        )
    }

    @Test
    fun `WHEN tab groups are disabled THEN do not show the candidate`() {
        assertFalse(
            TabGroupContextMenuCandidate.shouldShowCandidate(
                tab = createTab(url = "https://mozilla.org"),
                hitResult = HitResult.UNKNOWN(LINK_URL),
                tabGroupsEnabled = false,
            )
        )
    }

    @Test
    fun `WHEN the tab is private THEN do not show the candidate`() {
        assertFalse(
            TabGroupContextMenuCandidate.shouldShowCandidate(
                tab = createTab(url = "https://mozilla.org", private = true),
                hitResult = HitResult.UNKNOWN(LINK_URL),
                tabGroupsEnabled = true,
            )
        )
    }

    @Test
    fun `WHEN the long-pressed element is not an http link THEN do not show the candidate`() {
        assertFalse(
            TabGroupContextMenuCandidate.shouldShowCandidate(
                tab = createTab(url = "https://mozilla.org"),
                hitResult = HitResult.IMAGE("https://mozilla.org/image.png"),
                tabGroupsEnabled = true,
            )
        )
    }

    @Test
    fun `WHEN the candidate is created THEN it is labeled and identified as the tab group menu item`() {
        val candidate =
            TabGroupContextMenuCandidate.createOpenInNewTabInGroupCandidate(
                resources = testContext.resources,
                tabGroupsEnabled = true,
                tabsUseCases = mockk(relaxed = true),
                tabGroupUseCases = mockk(relaxed = true),
                snackBarParentView = snackbarParentView,
                snackbarDelegate = snackbarDelegate,
                scope = TestScope(),
            )

        assertEquals(TabGroupContextMenuCandidate.OPEN_IN_TAB_GROUP_ID, candidate.id)
        assertEquals("Open in tab group", candidate.label)
    }

    @Test
    fun `WHEN the link is opened THEN it opens in an unselected tab parented to the active tab and joins its group`() =
        runTest {
            val browserStore = browserStore()
            val repository = FakeTabGroupRepository()

            openLinkInActiveTabsGroup(browserStore, repository)

            val newTab = browserStore.state.tabs.last()
            assertEquals(2, browserStore.state.tabs.size)
            assertEquals(LINK_URL, newTab.content.url)
            assertEquals(PARENT_TAB_ID, newTab.parentId)
            assertEquals(PARENT_TAB_ID, browserStore.state.selectedTabId)

            val tabGroupData = repository.tabGroupDataFlow.first()
            val groupId = tabGroupData.tabGroups.single().id
            assertEquals(
                mapOf(PARENT_TAB_ID to groupId, newTab.id to groupId),
                tabGroupData.tabGroupAssignments,
            )
        }

    @Test
    fun `WHEN the parent tab is not in a group THEN the snackbar names the newly created group`() = runTest {
        openLinkInActiveTabsGroup(browserStore(), FakeTabGroupRepository())

        val snackbar = snackbarDelegate.textSnackbars.single()
        assertEquals("Link added to Group 1", snackbar.text)
        assertEquals("View", snackbar.action)
        assertEquals(Snackbar.LENGTH_LONG, snackbar.duration)
        assertSame(snackbarParentView, snackbar.snackBarParentView)
    }

    @Test
    fun `WHEN the parent tab is already in a group THEN the snackbar names that group`() = runTest {
        val group = tabGroup(title = "Research")
        val repository =
            FakeTabGroupRepository(
                initialTabGroupData =
                    TabGroupData(
                        tabGroups = listOf(group),
                        tabGroupAssignments = mapOf(PARENT_TAB_ID to group.id),
                    )
            )

        openLinkInActiveTabsGroup(browserStore(), repository)

        assertEquals("Link added to Research", snackbarDelegate.textSnackbars.single().text)
    }

    @Test
    fun `WHEN the snackbar action is clicked THEN the newly opened tab is selected`() = runTest {
        val browserStore = browserStore()

        openLinkInActiveTabsGroup(browserStore, FakeTabGroupRepository())

        val newTabId = browserStore.state.tabs.first { it.id != PARENT_TAB_ID }.id
        assertEquals(PARENT_TAB_ID, browserStore.state.selectedTabId)

        val listener = assertNotNull(snackbarDelegate.textSnackbars.single().listener)
        listener.invoke(snackbarParentView)

        assertEquals(newTabId, browserStore.state.selectedTabId)
    }

    private fun browserStore() =
        BrowserStore(
            initialState =
                BrowserState(
                    tabs = listOf(createTab(url = "https://mozilla.org", id = PARENT_TAB_ID)),
                    selectedTabId = PARENT_TAB_ID,
                ),
            middleware = EngineMiddleware.create(engine = mockk(relaxed = true), scope = TestScope()),
        )

    private suspend fun openLinkInActiveTabsGroup(
        browserStore: BrowserStore,
        repository: FakeTabGroupRepository,
    ) =
        TabGroupContextMenuCandidate.openLinkInActiveTabsGroup(
            parentTab = browserStore.state.tabs.first { it.id == PARENT_TAB_ID },
            hitResult = HitResult.UNKNOWN(LINK_URL),
            resources = testContext.resources,
            tabsUseCases = TabsUseCases(browserStore),
            tabGroupUseCases =
                TabGroupUseCases(
                    tabGroupRepository = repository,
                    defaultGroupName = { groupNumber ->
                        testContext.getString(R.string.create_tab_group_form_default_name, groupNumber)
                    },
                ),
            snackBarParentView = snackbarParentView,
            snackbarDelegate = snackbarDelegate,
        )

    private fun tabGroup(title: String) =
        TabGroup(
            id = "tab-group-id",
            title = title,
            theme = TabGroupTheme.default.name,
            lastModified = 0L,
            tabIds = emptyList(),
        )
}

private const val PARENT_TAB_ID = "parent-tab-id"
private const val LINK_URL = "https://mozilla.org/link"

private class FakeSnackbarDelegate : SnackbarDelegate {

    data class TextSnackbar(
        val snackBarParentView: View,
        val text: String,
        val duration: Int,
        val isError: Boolean,
        val action: String?,
        val withDismissAction: Boolean,
        val listener: ((v: View) -> Unit)?,
    )

    val textSnackbars: MutableList<TextSnackbar> = mutableListOf()

    override fun show(
        snackBarParentView: View,
        text: String,
        subText: String?,
        subTextOverflow: TextOverflow?,
        duration: Int,
        isError: Boolean,
        action: String?,
        withDismissAction: Boolean,
        listener: ((v: View) -> Unit)?,
    ) {
        textSnackbars +=
            TextSnackbar(
                snackBarParentView = snackBarParentView,
                text = text,
                duration = duration,
                isError = isError,
                action = action,
                withDismissAction = withDismissAction,
                listener = listener,
            )
    }

    override fun show(
        snackBarParentView: View,
        text: Int,
        subText: String?,
        subTextOverflow: TextOverflow?,
        duration: Int,
        isError: Boolean,
        action: Int,
        withDismissAction: Boolean,
        listener: ((v: View) -> Unit)?,
    ) = throw UnsupportedOperationException("The code under test only uses the String overload.")
}
