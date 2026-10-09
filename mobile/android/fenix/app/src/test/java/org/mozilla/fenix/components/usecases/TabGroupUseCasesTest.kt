/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.usecases

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mozilla.components.support.utils.FakeDateTimeProvider
import org.junit.Test
import org.mozilla.fenix.tabgroups.fakes.FakeTabGroupRepository
import org.mozilla.fenix.tabgroups.storage.data.TabGroup
import org.mozilla.fenix.tabgroups.storage.data.TabGroupData
import org.mozilla.fenix.tabstray.data.TabGroupTheme

class TabGroupUseCasesTest {

    private val dateTimeProvider = FakeDateTimeProvider()

    @Test
    fun `WHEN the active tab is not in a group THEN create a new group holding both tabs`() = runTest {
        val repository = FakeTabGroupRepository()
        val useCases = tabGroupUseCases(repository = repository)

        val groupName = useCases.addTabToActiveTabsGroup(activeTabId = "1", newTabId = "2")

        val tabGroupData = repository.tabGroupDataFlow.first()
        val createdGroup = tabGroupData.tabGroups.single()
        val expectedGroup =
            TabGroup(
                id = createdGroup.id,
                title = "Group 1",
                theme = TabGroupTheme.default.name,
                closed = false,
                lastModified = dateTimeProvider.currentTimeMillis(),
            )

        assertEquals(expected = expectedGroup, actual = createdGroup)
        assertEquals(
            expected = mapOf("1" to createdGroup.id, "2" to createdGroup.id),
            actual = tabGroupData.tabGroupAssignments,
        )
        assertEquals(expected = "Group 1", actual = groupName)
    }

    @Test
    fun `WHEN a new group is created and other groups exist THEN name it after the next group number`() = runTest {
        val repository =
            FakeTabGroupRepository(
                initialTabGroupData = TabGroupData(tabGroups = listOf(tabGroup(id = "group1"), tabGroup(id = "group2")))
            )
        val useCases = tabGroupUseCases(repository = repository)

        val groupName = useCases.addTabToActiveTabsGroup(activeTabId = "1", newTabId = "2")

        val tabGroupData = repository.tabGroupDataFlow.first()
        assertEquals("Group 3", tabGroupData.tabGroups.last().title)
        assertEquals("Group 3", groupName)
    }

    @Test
    fun `WHEN the active tab is already in a group THEN add the new tab to that group`() = runTest {
        val repository =
            FakeTabGroupRepository(
                initialTabGroupData =
                    TabGroupData(
                        tabGroups = listOf(tabGroup(id = "group1", title = "Research")),
                        tabGroupAssignments = mapOf("1" to "group1"),
                    )
            )
        val useCases = tabGroupUseCases(repository = repository)

        val groupName = useCases.addTabToActiveTabsGroup(activeTabId = "1", newTabId = "2")

        val tabGroupData = repository.tabGroupDataFlow.first()
        assertEquals(listOf("group1"), tabGroupData.tabGroups.map { it.id })
        assertEquals(mapOf("1" to "group1", "2" to "group1"), tabGroupData.tabGroupAssignments)
        assertEquals("Research", groupName)
    }

    @Test
    fun `WHEN the active tab's group is not in storage THEN throw and leave the new tab unassigned`() = runTest {
        val repository =
            FakeTabGroupRepository(initialTabGroupData = TabGroupData(tabGroupAssignments = mapOf("1" to "group1")))
        val useCases = tabGroupUseCases(repository = repository)

        assertFailsWith<IllegalStateException> {
            useCases.addTabToActiveTabsGroup(activeTabId = "1", newTabId = "2")
        }

        val tabGroupData = repository.tabGroupDataFlow.first()
        assertEquals(expected = mapOf("1" to "group1"), actual = tabGroupData.tabGroupAssignments)
    }

    private fun tabGroupUseCases(repository: FakeTabGroupRepository) =
        TabGroupUseCases(
            tabGroupRepository = repository,
            defaultGroupName = { groupNumber -> "Group $groupNumber" },
            dateTimeProvider = dateTimeProvider,
        )

    private fun tabGroup(id: String, title: String = id) =
        TabGroup(id = id, title = title, theme = TabGroupTheme.default.name, lastModified = 0L)
}
