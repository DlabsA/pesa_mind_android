package cc.dlabs.pesamind.core.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {
    private const val PREFS_NAME = "pesamind_theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode_enabled"
    private var prefs: SharedPreferences? = null

    private val _darkModeFlow = MutableStateFlow(false)
    val darkModeFlow: StateFlow<Boolean> = _darkModeFlow.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _darkModeFlow.value = isDarkModeEnabled()
    }

    fun isDarkModeEnabled(): Boolean {
        return prefs?.getBoolean(KEY_DARK_MODE, false) ?: false
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DARK_MODE, enabled)?.apply()
        _darkModeFlow.value = enabled
    }

    fun toggleDarkMode() {
        setDarkModeEnabled(!isDarkModeEnabled())
    }
}


