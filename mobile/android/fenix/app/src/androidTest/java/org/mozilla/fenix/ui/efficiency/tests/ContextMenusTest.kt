/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.ui.efficiency.tests

import mozilla.components.feature.contextmenu.R as contextMenuR
import org.junit.After
import org.junit.Test
import org.mozilla.fenix.R
import org.mozilla.fenix.customannotations.Critical
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.helpers.DataGenerationHelper.getStringResource
import org.mozilla.fenix.helpers.TestAssetHelper.getGenericAsset
import org.mozilla.fenix.helpers.TestHelper.appContext
import org.mozilla.fenix.ui.efficiency.helpers.BaseTest
import org.mozilla.fenix.ui.efficiency.selectors.BrowserPageSelectors
import org.mozilla.fenix.ui.efficiency.selectors.SearchBarSelectors
import org.mozilla.fenix.ui.efficiency.selectors.TabDrawerSelectors

class ContextMenusTest : BaseTest() {

    private val openLinkInNewTab = getStringResource(contextMenuR.string.mozac_feature_contextmenu_open_link_in_new_tab)
    private val openLinkInPrivateTab =
        getStringResource(contextMenuR.string.mozac_feature_contextmenu_open_link_in_private_tab)
    private val openInTabGroup = getStringResource(R.string.contextmenu_open_in_tab_group)
    private val firstDefaultGroupName = getStringResource(R.string.create_tab_group_form_default_name, 1)
    private val secondDefaultGroupName = getStringResource(R.string.create_tab_group_form_default_name, 2)
    private val defaultGroupColor = getStringResource(R.string.tab_group_color_yellow)

    @After
    fun restoreTabGroupsSetting() {
        appContext.components.settings.tabGroupsEnabled = true
    }

    // TestRail link: https://mozilla.testrail.io/index.php?/cases/view/243837
    @Critical
    @Test
    fun verifyOpenLinkNewTabContextMenuOptionTest() {
        val pageLinks = mockWebServer.getGenericAsset(4)
        val genericURL = mockWebServer.getGenericAsset(1)

        on.browserPage
            .navigateToPage(pageLinks.url.toString())
            .longClickPageObjectUntilContextMenu("Link 1", openLinkInNewTab)
            .verifyContextMenuForLocalHostLinks(genericURL.url.toString())
            .mozClick(BrowserPageSelectors.CONTEXT_MENU_ITEM(openLinkInNewTab))

        on.browserPage.verifySnackbarText("New tab opened").mozClick(BrowserPageSelectors.SNACKBAR_ACTION_BUTTON)

        on.browserPage.verifyUrl(genericURL.url.toString())

        on.tabDrawer
            .navigateToPage()
            .mozVerifyElementIsSelected(TabDrawerSelectors.NORMAL_BROWSING_OPEN_TABS_BUTTON)
            .mozVerify(TabDrawerSelectors.TAB_ITEM_WITH_TITLE(pageLinks.title))
            .mozVerify(TabDrawerSelectors.TAB_ITEM_WITH_TITLE(genericURL.title))
    }

    @Test
    fun verifyOpenLinkInTabGroupCreatesNewGroupTest() {
        val pageLinks = mockWebServer.getGenericAsset(4)
        val genericURL = mockWebServer.getGenericAsset(1)

        on.browserPage
            .navigateToPage(pageLinks.url.toString())
            .longClickPageObjectUntilContextMenu("Link 1", openInTabGroup)
            .verifyContextMenuForLocalHostLinks(genericURL.url.toString())
            .mozClick(BrowserPageSelectors.CONTEXT_MENU_ITEM(openInTabGroup))

        on.browserPage
            .verifySnackbarText(linkAddedToGroupSnackbarText(firstDefaultGroupName))
            .mozClick(BrowserPageSelectors.SNACKBAR_ACTION_BUTTON)

        on.browserPage.verifyUrl(genericURL.url.toString())

        on.tabDrawer.navigateToPage().mozVerify(TabDrawerSelectors.TAB_ITEM_WITH_TITLE(firstDefaultGroupName))
        on.tabDrawer.verifyTabGroupFromTabGroupPage(
            tabGroupTitle = firstDefaultGroupName,
            numberOfTabs = 2,
            tabGroupColor = defaultGroupColor,
        )
    }

    @Test
    fun verifyOpenLinkInTabGroupAddsToActiveTabsGroupTest() {
        val pageLinks = mockWebServer.getGenericAsset(4)

        on.browserPage
            .navigateToPage(pageLinks.url.toString())
            .longClickPageObjectUntilContextMenu("Link 1", openInTabGroup)
            .mozClick(BrowserPageSelectors.CONTEXT_MENU_ITEM(openInTabGroup))
        on.browserPage
            .verifySnackbarText(linkAddedToGroupSnackbarText(firstDefaultGroupName))
            .mozWaitUntilAbsent(BrowserPageSelectors.SNACKBAR)

        // The active tab is now in a group, so the next link joins that group instead of creating a new one.
        on.browserPage
            .longClickPageObjectUntilContextMenu("Link 2", openInTabGroup)
            .mozClick(BrowserPageSelectors.CONTEXT_MENU_ITEM(openInTabGroup))
        on.browserPage.verifySnackbarText(linkAddedToGroupSnackbarText(firstDefaultGroupName))

        on.tabDrawer
            .navigateToPage()
            .mozVerify(TabDrawerSelectors.TAB_ITEM_WITH_TITLE(firstDefaultGroupName))
            .mozVerifyElementAbsent(TabDrawerSelectors.TAB_ITEM_WITH_TITLE(secondDefaultGroupName))
        on.tabDrawer.verifyTabGroupFromTabGroupPage(
            tabGroupTitle = firstDefaultGroupName,
            numberOfTabs = 3,
            tabGroupColor = defaultGroupColor,
        )
    }

    @Test
    fun verifyOpenLinkInTabGroupIsNotShownInPrivateTabTest() {
        val pageLinks = mockWebServer.getGenericAsset(4)

        on.tabDrawer
            .navigateToPage()
            .mozClick(TabDrawerSelectors.PRIVATE_TABS_PAGE_BUTTON)
            .mozClick(TabDrawerSelectors.FAB)
        on.searchBar
            .mozEnterText(pageLinks.url.toString(), SearchBarSelectors.TOOLBAR_IN_EDIT_MODE)
            .mozPressEnter(SearchBarSelectors.TOOLBAR_IN_EDIT_MODE)
        on.browserPage
            .navigateToPage()
            .longClickPageObjectUntilContextMenu("Link 1", openLinkInPrivateTab)
            .mozVerifyElementAbsent(BrowserPageSelectors.CONTEXT_MENU_ITEM(openInTabGroup))
    }

    @Test
    fun verifyOpenLinkInTabGroupIsNotShownWhenTabGroupsAreDisabledTest() {
        val pageLinks = mockWebServer.getGenericAsset(4)
        appContext.components.settings.tabGroupsEnabled = false

        on.browserPage
            .navigateToPage(pageLinks.url.toString())
            .longClickPageObjectUntilContextMenu("Link 1", openLinkInNewTab)
            .mozVerifyElementAbsent(BrowserPageSelectors.CONTEXT_MENU_ITEM(openInTabGroup))
    }

    private fun linkAddedToGroupSnackbarText(groupName: String) =
        getStringResource(R.string.contextmenu_snackbar_link_added_to_tab_group, groupName)
}
