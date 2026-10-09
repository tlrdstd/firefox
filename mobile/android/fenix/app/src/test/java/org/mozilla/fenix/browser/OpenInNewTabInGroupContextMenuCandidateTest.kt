/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.view.View
import androidx.compose.ui.text.style.TextOverflow
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.tabgroups.storage.data.TabGroup
import mozilla.components.feature.tabgroups.storage.data.TabGroupData
import mozilla.components.feature.tabgroups.storage.fakes.FakeTabGroupRepository
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.test.fakes.engine.FakeEngine
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.support.utils.FakeDateTimeProvider
import mozilla.components.ui.widgets.SnackbarDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OpenInNewTabInGroupContextMenuCandidateTest {

    private val parentTab = createTab("https://www.mozilla.org", id = "parent")
    private val link = HitResult.UNKNOWN("https://firefox.com")
    private val dateTimeProvider = FakeDateTimeProvider()

    private fun createStore() =
        BrowserStore(
            initialState = BrowserState(tabs = listOf(parentTab), selectedTabId = parentTab.id),
            middleware = EngineMiddleware.create(engine = FakeEngine(), scope = TestScope()),
        )

    private fun createCandidate(
        store: BrowserStore = createStore(),
        repository: FakeTabGroupRepository = FakeTabGroupRepository(),
        scope: CoroutineScope = TestScope(),
        isTabGroupsEnabled: Boolean = true,
        openInNewTabShowFor: Boolean = true,
        snackbarDelegate: SnackbarDelegate = TestSnackbarDelegate(),
    ) =
        createOpenInNewTabInGroupCandidate(
            context = testContext,
            openInNewTabCandidate =
                ContextMenuCandidate(
                    id = OPEN_IN_NEW_TAB_CANDIDATE_ID,
                    label = "Open link in new tab",
                    showFor = { _, _ -> openInNewTabShowFor },
                    action = { _, _ -> },
                ),
            tabsUseCases = TabsUseCases(store),
            tabGroupRepository = repository,
            scope = scope,
            isTabGroupsEnabled = { isTabGroupsEnabled },
            snackBarParentView = CoordinatorLayout(testContext),
            snackbarDelegate = snackbarDelegate,
            dateTimeProvider = dateTimeProvider,
        )

    @Test
    fun `WHEN tab groups are enabled THEN visibility matches open in new tab`() {
        assertTrue(createCandidate(openInNewTabShowFor = true).showFor(parentTab, link))
        assertFalse(createCandidate(openInNewTabShowFor = false).showFor(parentTab, link))
    }

    @Test
    fun `WHEN tab groups are disabled THEN the candidate is hidden`() {
        assertFalse(createCandidate(isTabGroupsEnabled = false).showFor(parentTab, link))
    }

    @Test
    fun `WHEN the current tab is not grouped THEN a new group with both tabs is created`() = runTest {
        val store = createStore()
        val existingGroup =
            TabGroup(id = "existing", title = "Work", theme = "Red", lastModified = 1L, tabIds = listOf("other"))
        val repository =
            FakeTabGroupRepository(
                TabGroupData(tabGroups = listOf(existingGroup), tabGroupAssignments = mapOf("other" to "existing"))
            )
        val snackbarDelegate = TestSnackbarDelegate()

        createCandidate(store, repository, scope = this, snackbarDelegate = snackbarDelegate).action(parentTab, link)
        advanceUntilIdle()

        assertEquals(2, store.state.tabs.size)
        val newTab = store.state.tabs.last()
        assertEquals("https://firefox.com", newTab.content.url)
        assertEquals(parentTab.id, newTab.parentId)
        assertEquals(parentTab.id, store.state.selectedTabId)

        val data = repository.tabGroupDataFlow.first()
        assertEquals(2, data.tabGroups.size)
        val newGroup = data.tabGroups.last()
        assertEquals(listOf(parentTab.id, newTab.id), newGroup.tabIds)
        assertEquals(testContext.getString(R.string.create_tab_group_form_default_name, 2), newGroup.title)
        assertEquals("Pink", newGroup.theme)
        assertEquals(dateTimeProvider.currentTimeMillis(), newGroup.lastModified)
        assertEquals(newGroup.id, data.tabGroupAssignments[parentTab.id])
        assertEquals(newGroup.id, data.tabGroupAssignments[newTab.id])

        assertEquals(R.string.context_menu_snackbar_new_tab_opened_in_group, snackbarDelegate.lastText)
    }

    @Test
    fun `WHEN no groups exist THEN the new group uses the default theme`() = runTest {
        val repository = FakeTabGroupRepository()

        createCandidate(repository = repository, scope = this).action(parentTab, link)
        advanceUntilIdle()

        val newGroup = repository.tabGroupDataFlow.first().tabGroups.single()
        assertEquals("Yellow", newGroup.theme)
        assertEquals(testContext.getString(R.string.create_tab_group_form_default_name, 1), newGroup.title)
    }

    @Test
    fun `WHEN the current tab is grouped THEN the new tab joins that group`() = runTest {
        val store = createStore()
        val group =
            TabGroup(id = "group", title = "Work", theme = "Red", lastModified = 1L, tabIds = listOf(parentTab.id))
        val repository =
            FakeTabGroupRepository(
                TabGroupData(tabGroups = listOf(group), tabGroupAssignments = mapOf(parentTab.id to group.id))
            )

        createCandidate(store, repository, scope = this).action(parentTab, link)
        advanceUntilIdle()

        val newTab = store.state.tabs.last()
        val data = repository.tabGroupDataFlow.first()
        assertEquals(1, data.tabGroups.size)
        assertEquals(group.id, data.tabGroupAssignments[newTab.id])
    }

    @Test
    fun `WHEN the snackbar action is clicked THEN the new tab is selected`() = runTest {
        val store = createStore()
        val snackbarDelegate = TestSnackbarDelegate()

        createCandidate(store, scope = this, snackbarDelegate = snackbarDelegate).action(parentTab, link)
        advanceUntilIdle()

        assertNotNull(snackbarDelegate.lastActionListener)
        snackbarDelegate.lastActionListener?.invoke(mockk())

        assertEquals(store.state.tabs.last().id, store.state.selectedTabId)
    }
}

private class TestSnackbarDelegate : SnackbarDelegate {
    var lastText: Int = 0
    var lastActionListener: ((v: View) -> Unit)? = null

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
    ) {
        lastText = text
        lastActionListener = listener
    }

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
        lastActionListener = listener
    }
}
