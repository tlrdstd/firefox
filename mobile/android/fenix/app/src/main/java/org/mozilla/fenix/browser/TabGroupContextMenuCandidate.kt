/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.content.res.Resources
import android.view.View
import androidx.annotation.VisibleForTesting
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.isHttpLink
import mozilla.components.feature.contextmenu.isUrlSchemeAllowed
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.ui.widgets.SnackbarDelegate
import org.mozilla.fenix.R
import org.mozilla.fenix.components.usecases.TabGroupUseCases

/** Provides the "Open in new tab in group" context menu candidate for [BrowserFragment]. */
object TabGroupContextMenuCandidate {

    const val OPEN_IN_TAB_GROUP_ID = "fenix.contextmenu.open_in_tab_group"

    /**
     * Context menu item: "Open in tab group".
     *
     * Opens the long-pressed link in an unselected background tab and adds it to the group the active tab belongs to.
     * When the active tab is not in a group, a new group holding both tabs is created.
     *
     * @param resources [Resources] used to obtain the title of menu item candidate.
     * @param tabGroupsEnabled [Boolean] Used to check whether the Tab Groups feature is enabled.
     * @param tabsUseCases Used to open the link in a new tab.
     * @param tabGroupUseCases Used to resolve which group the new tab belongs to.
     * @param snackBarParentView The view in which to find a suitable parent for displaying the snackbar.
     * @param snackbarDelegate Used to display the snackbar confirming which group the new tab was added to.
     * @param scope Scope used to add the new tab to its group before the snackbar is shown.
     */
    fun createOpenInNewTabInGroupCandidate(
        resources: Resources,
        tabGroupsEnabled: Boolean,
        tabsUseCases: TabsUseCases,
        tabGroupUseCases: TabGroupUseCases,
        snackBarParentView: View,
        snackbarDelegate: SnackbarDelegate,
        scope: CoroutineScope,
    ) =
        ContextMenuCandidate(
            id = OPEN_IN_TAB_GROUP_ID,
            label = resources.getString(R.string.contextmenu_open_in_tab_group),
            showFor = { tab, hitResult ->
                shouldShowCandidate(tab = tab, hitResult = hitResult, tabGroupsEnabled = tabGroupsEnabled)
            },
            action = { parentTab, hitResult ->
                scope.launch {
                    openLinkInActiveTabsGroup(
                        parentTab = parentTab,
                        hitResult = hitResult,
                        resources = resources,
                        tabsUseCases = tabsUseCases,
                        tabGroupUseCases = tabGroupUseCases,
                        snackBarParentView = snackBarParentView,
                        snackbarDelegate = snackbarDelegate,
                    )
                }
            },
        )

    /**
     * Whether the "Open in tab group" item should be shown for the long-pressed [hitResult] in [tab].
     *
     * @param tab The [SessionState] the context menu was opened from.
     * @param hitResult The [HitResult] describing the long-pressed element.
     * @param tabGroupsEnabled Whether the Tab Groups feature is enabled.
     */
    @VisibleForTesting
    internal fun shouldShowCandidate(
        tab: SessionState,
        hitResult: HitResult,
        tabGroupsEnabled: Boolean,
    ): Boolean =
        tabGroupsEnabled && !tab.content.private && hitResult.isHttpLink() && tab.isUrlSchemeAllowed(hitResult.getUrl())

    /**
     * Opens the [hitResult] link in an unselected background tab and adds it to the group [parentTab] belongs to,
     * creating a new group holding both tabs when [parentTab] is not in one.
     *
     * @param parentTab The [SessionState] the context menu was opened from.
     * @param hitResult The [HitResult] describing the long-pressed element.
     * @param resources [Resources] used to obtain the snackbar text.
     * @param tabsUseCases Used to open the link in a new tab.
     * @param tabGroupUseCases Used to resolve which group the new tab belongs to.
     * @param snackBarParentView The view in which to find a suitable parent for displaying the snackbar.
     * @param snackbarDelegate Used to display the snackbar confirming which group the new tab was added to.
     */
    @VisibleForTesting
    internal suspend fun openLinkInActiveTabsGroup(
        parentTab: SessionState,
        hitResult: HitResult,
        resources: Resources,
        tabsUseCases: TabsUseCases,
        tabGroupUseCases: TabGroupUseCases,
        snackBarParentView: View,
        snackbarDelegate: SnackbarDelegate,
    ) {
        val newTabId =
            tabsUseCases.addTab(
                url = hitResult.getUrl(),
                selectTab = false,
                startLoading = true,
                textDirectiveUserActivation = true,
                parentId = parentTab.id,
                contextId = parentTab.contextId,
            )

        val groupName =
            tabGroupUseCases.addTabToActiveTabsGroup(
                activeTabId = parentTab.id,
                newTabId = newTabId,
            )

        snackbarDelegate.show(
            snackBarParentView = snackBarParentView,
            text = resources.getString(R.string.contextmenu_snackbar_link_added_to_tab_group, groupName),
            duration = Snackbar.LENGTH_LONG,
            action = resources.getString(R.string.contextmenu_snackbar_link_added_to_tab_group_action),
        ) {
            tabsUseCases.selectTab(newTabId)
        }
    }
}
