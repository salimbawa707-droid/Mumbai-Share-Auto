package com.mumbaishareauto.app

import android.content.SharedPreferences
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying FavoriteRouteRepository persistence behavior via an in-memory SharedPreferences fake.
 */
class FavoriteRouteRepositoryTest {

    @Test
    fun toggleAndClearFavorites_persistAcrossRepositoryInstances() {
        val prefs = InMemorySharedPreferences()
        val repo1 = FavoriteRouteRepository(prefs)

        assertTrue(repo1.favoriteKeys.value.isEmpty())

        repo1.toggleFavorite("w_bnd_e_bkc")
        repo1.toggleFavorite("w_adh_e_seepz")
        assertTrue(repo1.isFavorite("w_bnd_e_bkc"))
        assertEquals(setOf("w_bnd_e_bkc", "w_adh_e_seepz"), repo1.favoriteKeys.value)

        // Recreate repository against same SharedPreferences backing store to verify reload
        val repo2 = FavoriteRouteRepository(prefs)
        assertTrue(repo2.isFavorite("w_bnd_e_bkc"))
        assertTrue(repo2.isFavorite("w_adh_e_seepz"))

        repo2.removeFavorite("w_bnd_e_bkc")
        assertFalse(repo2.isFavorite("w_bnd_e_bkc"))

        repo2.clearAllFavorites()
        val repo3 = FavoriteRouteRepository(prefs)
        assertTrue(repo3.favoriteKeys.value.isEmpty())
    }
}

internal class InMemorySharedPreferences : SharedPreferences {
    private val store = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = (store[key] as? String) ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (store[key] as? Set<String>)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (store[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = null
        }

        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) store.clear()
            for ((k, v) in pending) {
                if (v == null) store.remove(k) else store[k] = v
            }
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
