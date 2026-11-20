package com.mumbaishareauto.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.mumbaishareauto.app.UserMode
import com.mumbaishareauto.app.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only draft snapshot created when a user inspects a structural form evaluation.
 * Explicitly marked non-submitted — does NOT represent a live backend booking.
 */
data class LocalRouteDraft(
    val id: String,
    val roleLabel: String,
    val pickupStand: String,
    val destination: String,
    val partyOrSeatCount: Int,
    val totalFareInr: Double?,
    val validationSummary: String,
    val isStructurallyValid: Boolean,
    val isCommerciallyVerified: Boolean = false,
    val createdAtMs: Long,
    val viaPointsSummary: String = ""
) {
    fun serialize(): String {
        val safePickup = pickupStand.replace("|", "/")
        val safeDest = destination.replace("|", "/")
        val safeSummary = validationSummary.replace("|", ";").replace("\n", " ")
        val safeVia = viaPointsSummary.replace("|", "/").replace("\n", ", ")
        val fareStr = totalFareInr?.toString() ?: ""
        return listOf(
            id,
            roleLabel,
            safePickup,
            safeDest,
            partyOrSeatCount.toString(),
            fareStr,
            safeSummary,
            isStructurallyValid.toString(),
            isCommerciallyVerified.toString(),
            createdAtMs.toString(),
            safeVia
        ).joinToString("|")
    }

    companion object {
        fun deserialize(raw: String): LocalRouteDraft? {
            val parts = raw.split("|")
            if (parts.size < 10) return null
            return try {
                LocalRouteDraft(
                    id = parts[0],
                    roleLabel = parts[1],
                    pickupStand = parts[2],
                    destination = parts[3],
                    partyOrSeatCount = parts[4].toInt(),
                    totalFareInr = parts[5].takeIf { it.isNotBlank() }?.toDoubleOrNull(),
                    validationSummary = parts[6],
                    isStructurallyValid = parts[7].toBooleanStrictOrNull() ?: false,
                    isCommerciallyVerified = parts[8].toBooleanStrictOrNull() ?: false,
                    createdAtMs = parts[9].toLong(),
                    viaPointsSummary = parts.getOrNull(10) ?: ""
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Persists device-local UI preferences and non-networked local drafts via SharedPreferences.
 */
class AppPreferencesRepository internal constructor(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val _onboardingCompleted = MutableStateFlow(
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    )
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _preferredMode = MutableStateFlow(loadMode())
    val preferredMode: StateFlow<UserMode> = _preferredMode.asStateFlow()

    private val _themePreference = MutableStateFlow(loadTheme())
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _localDrafts = MutableStateFlow(loadDrafts())
    val localDrafts: StateFlow<List<LocalRouteDraft>> = _localDrafts.asStateFlow()

    private fun loadMode(): UserMode {
        val raw = prefs.getString(KEY_USER_MODE, UserMode.PASSENGER.name)
        return UserMode.entries.firstOrNull { it.name == raw } ?: UserMode.PASSENGER
    }

    private fun loadTheme(): ThemePreference {
        val raw = prefs.getString(KEY_THEME_PREF, ThemePreference.SYSTEM.name)
        return ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.SYSTEM
    }

    private fun loadDrafts(): List<LocalRouteDraft> {
        val rawSet = prefs.getStringSet(KEY_LOCAL_DRAFTS, emptySet()) ?: emptySet()
        return rawSet.mapNotNull { LocalRouteDraft.deserialize(it) }
            .sortedByDescending { it.createdAtMs }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _onboardingCompleted.value = completed
    }

    fun setPreferredMode(mode: UserMode) {
        prefs.edit().putString(KEY_USER_MODE, mode.name).apply()
        _preferredMode.value = mode
    }

    fun setThemePreference(theme: ThemePreference) {
        prefs.edit().putString(KEY_THEME_PREF, theme.name).apply()
        _themePreference.value = theme
    }

    fun saveLocalDraft(draft: LocalRouteDraft) {
        val updated = (_localDrafts.value.filterNot { it.id == draft.id } + draft)
            .sortedByDescending { it.createdAtMs }
            .take(25)
        prefs.edit().putStringSet(KEY_LOCAL_DRAFTS, updated.map { it.serialize() }.toSet()).apply()
        _localDrafts.value = updated
    }

    fun deleteLocalDraft(draftId: String) {
        val updated = _localDrafts.value.filterNot { it.id == draftId }
        prefs.edit().putStringSet(KEY_LOCAL_DRAFTS, updated.map { it.serialize() }.toSet()).apply()
        _localDrafts.value = updated
    }

    fun clearAllDrafts() {
        prefs.edit().remove(KEY_LOCAL_DRAFTS).apply()
        _localDrafts.value = emptyList()
    }

    fun resetAllPreferences() {
        prefs.edit().clear().apply()
        _onboardingCompleted.value = false
        _preferredMode.value = UserMode.PASSENGER
        _themePreference.value = ThemePreference.SYSTEM
        _localDrafts.value = emptyList()
    }

    companion object {
        const val PREFS_NAME = "mumbai_share_auto_prefs"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_USER_MODE = "preferred_user_mode"
        const val KEY_THEME_PREF = "theme_preference"
        const val KEY_LOCAL_DRAFTS = "local_route_drafts"
    }
}
