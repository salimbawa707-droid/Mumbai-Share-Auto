package com.mumbaishareauto.app

import com.mumbaishareauto.app.data.local.AppPreferencesRepository
import com.mumbaishareauto.app.data.local.LocalRouteDraft
import com.mumbaishareauto.app.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying AppPreferencesRepository theme, onboarding, mode, and local draft persistence.
 */
class AppPreferencesRepositoryTest {

    @Test
    fun preferencesAndLocalDrafts_persistAndResetCleanly() {
        val prefs = InMemorySharedPreferences()
        val repo1 = AppPreferencesRepository(prefs)

        assertFalse(repo1.onboardingCompleted.value)
        assertEquals(UserMode.PASSENGER, repo1.preferredMode.value)
        assertEquals(ThemePreference.SYSTEM, repo1.themePreference.value)

        repo1.setOnboardingCompleted(true)
        repo1.setPreferredMode(UserMode.DRIVER)
        repo1.setThemePreference(ThemePreference.DARK)

        val draft = LocalRouteDraft(
            id = "DRAFT_TEST_1",
            roleLabel = "Driver Posting Draft",
            pickupStand = "Bandra Station East Auto Stand",
            destination = "BKC Diamond Bourse",
            partyOrSeatCount = 3,
            totalFareInr = 90.0,
            validationSummary = "Evaluated locally",
            isStructurallyValid = true,
            isCommerciallyVerified = false,
            createdAtMs = 1700000000000L
        )
        repo1.saveLocalDraft(draft)

        val repo2 = AppPreferencesRepository(prefs)
        assertTrue(repo2.onboardingCompleted.value)
        assertEquals(UserMode.DRIVER, repo2.preferredMode.value)
        assertEquals(ThemePreference.DARK, repo2.themePreference.value)
        assertEquals(1, repo2.localDrafts.value.size)
        assertEquals("DRAFT_TEST_1", repo2.localDrafts.value.first().id)

        repo2.deleteLocalDraft("DRAFT_TEST_1")
        assertTrue(repo2.localDrafts.value.isEmpty())

        repo2.resetAllPreferences()
        assertFalse(repo2.onboardingCompleted.value)
        assertEquals(ThemePreference.SYSTEM, repo2.themePreference.value)
    }
}
