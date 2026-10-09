/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.content.Context
import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.R as contextMenuR
import mozilla.components.feature.tabgroups.storage.data.TabGroup
import mozilla.components.feature.tabgroups.storage.repository.TabGroupRepository
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.utils.DateTimeProvider
import mozilla.components.support.utils.DefaultDateTimeProvider
import mozilla.components.ui.widgets.SnackbarDelegate
import org.mozilla.fenix.R
import org.mozilla.fenix.tabstray.data.TabGroupTheme

internal const val OPEN_IN_NEW_TAB_CANDIDATE_ID = "mozac.feature.contextmenu.open_in_new_tab"
internal const val OPEN_IN_NEW_TAB_IN_GROUP_CANDIDATE_ID = "fenix.contextmenu.open_in_new_tab_in_group"

/**
 * Context menu item: "Open link in new tab in group".
 *
 * Shown whenever [openInNewTabCandidate] is shown, as long as tab groups are enabled. Opens the link in a new
 * background tab and groups it with the current tab: if the current tab already belongs to a group the new tab joins
 * that group, otherwise a new group containing both tabs is created with the default name and next theme.
 *
 * @param context [Context] used to resolve strings.
 * @param openInNewTabCandidate The "Open link in new tab" candidate whose visibility rules are reused.
 * @param tabsUseCases [TabsUseCases] used for adding and selecting tabs.
 * @param tabGroupRepository [TabGroupRepository] used to read and write tab group data.
 * @param scope [CoroutineScope] used to write tab group data. Should outlive the calling UI.
 * @param isTabGroupsEnabled Returns whether the tab groups feature is enabled.
 * @param snackBarParentView The view in which to find a suitable parent for displaying the snackbar.
 * @param snackbarDelegate [SnackbarDelegate] used to show the snackbar.
 * @param dateTimeProvider [DateTimeProvider] used to stamp newly created groups.
 */
@Suppress("LongParameterList")
internal fun createOpenInNewTabInGroupCandidate(
    context: Context,
    openInNewTabCandidate: ContextMenuCandidate,
    tabsUseCases: TabsUseCases,
    tabGroupRepository: TabGroupRepository,
    scope: CoroutineScope,
    isTabGroupsEnabled: () -> Boolean,
    snackBarParentView: View,
    snackbarDelegate: SnackbarDelegate,
    dateTimeProvider: DateTimeProvider = DefaultDateTimeProvider(),
) =
    ContextMenuCandidate(
        id = OPEN_IN_NEW_TAB_IN_GROUP_CANDIDATE_ID,
        label = context.getString(R.string.context_menu_open_link_in_new_tab_in_group),
        showFor = { tab, hitResult -> isTabGroupsEnabled() && openInNewTabCandidate.showFor(tab, hitResult) },
        action = { parent, hitResult ->
            val newTabId =
                tabsUseCases.addTab(
                    hitResult.getLinkUrl(),
                    selectTab = false,
                    startLoading = true,
                    textDirectiveUserActivation = true,
                    parentId = parent.id,
                    contextId = parent.contextId,
                )

            scope.launch {
                addToParentGroupOrCreateGroup(
                    context = context,
                    tabGroupRepository = tabGroupRepository,
                    parentTabId = parent.id,
                    newTabId = newTabId,
                    dateTimeProvider = dateTimeProvider,
                )
            }

            snackbarDelegate.show(
                snackBarParentView = snackBarParentView,
                text = R.string.context_menu_snackbar_new_tab_opened_in_group,
                duration = Snackbar.LENGTH_LONG,
                action = contextMenuR.string.mozac_feature_contextmenu_snackbar_action_switch,
            ) {
                tabsUseCases.selectTab(newTabId)
            }
        },
    )

private suspend fun addToParentGroupOrCreateGroup(
    context: Context,
    tabGroupRepository: TabGroupRepository,
    parentTabId: String,
    newTabId: String,
    dateTimeProvider: DateTimeProvider,
) {
    val data = tabGroupRepository.tabGroupDataFlow.first()
    val parentGroupId = data.tabGroupAssignments[parentTabId]
    if (parentGroupId != null) {
        tabGroupRepository.addTabGroupAssignment(tabId = newTabId, tabGroupId = parentGroupId)
        return
    }

    // Matches the defaults used by the tabs tray "create tab group" form (see newTabGroupForm).
    val theme =
        data.tabGroups
            .maxByOrNull { it.lastModified }
            ?.theme
            ?.let { stored -> TabGroupTheme.entries.firstOrNull { it.name == stored } }
            ?.next() ?: TabGroupTheme.default
    tabGroupRepository.createTabGroupWithTabs(
        TabGroup(
            title = context.getString(R.string.create_tab_group_form_default_name, data.tabGroups.size + 1),
            theme = theme.name,
            lastModified = dateTimeProvider.currentTimeMillis(),
            tabIds = listOf(parentTabId, newTabId),
        )
    )
}

// Mirrors android-components' internal HitResult.getLink() for the link types this candidate is shown for.
private fun HitResult.getLinkUrl(): String =
    when (this) {
        is HitResult.IMAGE_SRC -> uri
        else -> src
    }
