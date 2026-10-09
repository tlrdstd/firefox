/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.usecases

import kotlinx.coroutines.flow.first
import mozilla.components.feature.tabgroups.storage.data.TabGroup
import mozilla.components.feature.tabgroups.storage.repository.TabGroupRepository
import mozilla.components.support.utils.DateTimeProvider
import mozilla.components.support.utils.DefaultDateTimeProvider
import org.mozilla.fenix.tabstray.data.TabGroupTheme

/**
 * Use cases for adding tabs to tab groups from outside the Tab Manager.
 *
 * @param tabGroupRepository Storage holding tab groups and their tab assignments.
 * @param defaultGroupName Builds the name of a newly created group from its number, e.g. 3 produces "Group 3".
 * @param dateTimeProvider Provides the timestamp recorded on a newly created group.
 */
class TabGroupUseCases(
    private val tabGroupRepository: TabGroupRepository,
    private val defaultGroupName: (groupNumber: Int) -> String,
    private val dateTimeProvider: DateTimeProvider = DefaultDateTimeProvider(),
) {

    /**
     * Adds [newTabId] to the tab group that [activeTabId] belongs to.
     *
     * When [activeTabId] is not already in a group, a new group holding both tabs is created and named after the next
     * available group number.
     *
     * @param activeTabId The ID of the parent tab the new child tab was opened from.
     * @param newTabId The newly opened tab to add to a group.
     * @return The name of the group [newTabId] was added to.
     * @throws IllegalStateException if [activeTabId] is assigned to a tab group that is not in storage, in which case
     *   [newTabId] is left unassigned.
     */
    suspend fun addTabToActiveTabsGroup(activeTabId: String, newTabId: String): String {
        val tabGroupData = tabGroupRepository.tabGroupDataFlow.first()
        val activeGroupId = tabGroupData.tabGroupAssignments[activeTabId]

        return if (activeGroupId == null) {
            val newGroup =
                TabGroup(
                    title = defaultGroupName(tabGroupData.tabGroups.size + 1),
                    theme = TabGroupTheme.default.name,
                    lastModified = dateTimeProvider.currentTimeMillis(),
                    tabIds = listOf(activeTabId, newTabId),
                )
            tabGroupRepository.createTabGroupWithTabs(tabGroup = newGroup)
            newGroup.title
        } else {
            val activeGroup =
                checkNotNull(tabGroupData.tabGroups.firstOrNull { it.id == activeGroupId }) {
                    "Tab $activeTabId is assigned to tab group $activeGroupId, which is not in storage"
                }
            tabGroupRepository.addTabGroupAssignment(tabId = newTabId, tabGroupId = activeGroupId)
            activeGroup.title
        }
    }
}
