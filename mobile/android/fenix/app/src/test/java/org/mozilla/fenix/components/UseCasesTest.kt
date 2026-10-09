/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import java.util.Locale
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabgroups.storage.fakes.FakeTabGroupRepository
import mozilla.components.support.locale.LocaleUseCases
import mozilla.components.support.test.robolectric.testContext
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.settings.advanced.DefaultLocaleSettingsController
import org.mozilla.fenix.settings.advanced.LocaleSettingsStore

@RunWith(AndroidJUnit4::class)
class UseCasesTest {

    @Test
    fun `WHEN the in-app language is changed THEN the cached tab group use cases name new groups in the new locale`() =
        runBlocking {
            val repository = FakeTabGroupRepository()
            val tabGroupUseCases = useCases(repository).tabGroupUseCases

            assertEquals(
                expected = "Group 1",
                actual = tabGroupUseCases.addTabToActiveTabsGroup(activeTabId = "tab-1", newTabId = "tab-2"),
            )

            localeSettingsController().updateBaseConfiguration(testContext, Locale.GERMAN)

            assertEquals(
                expected = "Gruppe 2",
                actual = tabGroupUseCases.addTabToActiveTabsGroup(activeTabId = "tab-3", newTabId = "tab-4"),
            )
        }

    private fun useCases(tabGroupRepository: FakeTabGroupRepository) =
        UseCases(
            context = testContext,
            crashReporter = lazy { mockk(relaxed = true) },
            engine = lazy { mockk(relaxed = true) },
            store = lazy { BrowserStore() },
            shortcutManager = lazy { mockk(relaxed = true) },
            topSitesStorage = lazy { mockk(relaxed = true) },
            bookmarksStorage = lazy { mockk(relaxed = true) },
            historyStorage = lazy { mockk(relaxed = true) },
            lastSavedFolderCache = lazy { mockk(relaxed = true) },
            syncedTabsCommands = lazy { mockk(relaxed = true) },
            tabGroupRepository = lazy { tabGroupRepository },
            adsClientProvider = lazy { mockk(relaxed = true) },
            appStore = lazy { mockk(relaxed = true) },
            client = lazy { mockk(relaxed = true) },
            strictMode = lazy { mockk(relaxed = true) },
        )

    private fun localeSettingsController() =
        DefaultLocaleSettingsController(
            activity = mockk<Activity>(relaxed = true),
            localeSettingsStore = mockk<LocaleSettingsStore>(relaxed = true),
            browserStore = BrowserStore(),
            localeUseCase = mockk<LocaleUseCases>(relaxed = true),
        )
}
