/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.isHttpLink
import mozilla.components.feature.contextmenu.isUrlSchemeAllowed
import mozilla.components.feature.tabs.TabsUseCases
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
     */
    fun createOpenInNewTabInGroupCandidate(
        resources: Resources,
        tabGroupsEnabled: Boolean,
        tabsUseCases: TabsUseCases,
        tabGroupUseCases: TabGroupUseCases,
    ) =
        ContextMenuCandidate(
            id = OPEN_IN_TAB_GROUP_ID,
            label = resources.getString(R.string.contextmenu_open_in_tab_group),
            showFor = { tab, hitResult ->
                shouldShowCandidate(tab = tab, hitResult = hitResult, tabGroupsEnabled = tabGroupsEnabled)
            },
            action = { parentTab, hitResult ->
                openLinkInActiveTabsGroup(
                    parentTab = parentTab,
                    hitResult = hitResult,
                    tabsUseCases = tabsUseCases,
                    tabGroupUseCases = tabGroupUseCases,
                )
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
     * @param tabsUseCases Used to open the link in a new tab.
     * @param tabGroupUseCases Used to resolve which group the new tab belongs to.
     */
    @VisibleForTesting
    internal fun openLinkInActiveTabsGroup(
        parentTab: SessionState,
        hitResult: HitResult,
        tabsUseCases: TabsUseCases,
        tabGroupUseCases: TabGroupUseCases,
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

        tabGroupUseCases.addTabToActiveTabsGroup(activeTabId = parentTab.id, newTabId = newTabId)
    }
}
