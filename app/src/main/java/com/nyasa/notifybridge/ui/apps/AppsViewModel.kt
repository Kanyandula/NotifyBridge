package com.nyasa.notifybridge.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRow(val label: String, val pkg: String, val enabled: Boolean)

fun filterApps(all: List<AppRow>, q: String) =
    if (q.isBlank()) all else all.filter {
        it.label.contains(q, true) || it.pkg.contains(q, true) }

fun toggle(current: Set<String>, pkg: String, on: Boolean) =
    if (on) current + pkg else current - pkg

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    // One-shot flow: load launchable apps on IO, de-dup by package, sort by label
    private val installedAppsFlow = flow<List<Pair<String, String>>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        val seen = mutableSetOf<String>()
        val apps = resolveInfos
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                val label = ri.loadLabel(pm).toString()
                if (seen.add(pkg)) label to pkg else null
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
        emit(apps)
    }.flowOn(Dispatchers.IO)

    // Combine installed apps + allow-list → List<AppRow>
    val rows: StateFlow<List<AppRow>> = combine(installedAppsFlow, settings.allowList) { apps, allowList ->
        apps.map { (label, pkg) -> AppRow(label, pkg, pkg in allowList) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // Load app icons off the main thread; keyed by package name
    val icons: StateFlow<Map<String, Drawable?>> = installedAppsFlow
        .map { apps ->
            val pm = context.packageManager
            apps.associate { (_, pkg) ->
                pkg to runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    // Track allow-list separately so setEnabled can read current value synchronously
    private val _allowList = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            settings.allowList.collect { _allowList.value = it }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun setEnabled(pkg: String, on: Boolean) {
        _allowList.update { toggle(it, pkg, on) }
        viewModelScope.launch { settings.setAllowList(_allowList.value) }
    }
}
