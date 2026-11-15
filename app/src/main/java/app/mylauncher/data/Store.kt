package app.mylauncher.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("launcher")

private object K {
    val theme = stringPreferencesKey("theme")
    val dynamic = booleanPreferencesKey("dynamic")
    val drawerBg = stringPreferencesKey("drawer_bg")
    val drawerAlpha = floatPreferencesKey("drawer_alpha")
    val iconPack = stringPreferencesKey("icon_pack")
    val themed = booleanPreferencesKey("themed")
    val badges = booleanPreferencesKey("badges")
    val columns = intPreferencesKey("columns")
    val labels = booleanPreferencesKey("labels")
    val pages = intPreferencesKey("pages")
    val dock = stringPreferencesKey("dock")
    val dockInit = booleanPreferencesKey("dock_init")
    val home = stringPreferencesKey("home")
}

class SettingsStore(private val ctx: Context) {

    val flow: Flow<LauncherSettings> = ctx.store.data.map { read(it) }

    suspend fun update(block: (LauncherSettings) -> LauncherSettings) {
        ctx.store.edit { write(it, block(read(it))) }
    }

    /** Añade un elemento al primer hueco libre. Devuelve false si no hay sitio. */
    suspend fun addHomeItem(kind: ItemKind, data: String, sx: Int, sy: Int, page: Int): Boolean {
        var ok = false
        update { s ->
            val slot = findFree(s.home, s.homePages, page, sx, sy)
            if (slot == null) s else {
                ok = true
                val id = (s.home.maxOfOrNull { it.id } ?: 0) + 1
                s.copy(home = s.home + HomeItem(id, kind, slot.first, slot.second, slot.third, sx, sy, data))
            }
        }
        return ok
    }

    private fun read(p: Preferences) = LauncherSettings(
        themeMode = p[K.theme].toEnum(ThemeMode.SYSTEM),
        dynamicColor = p[K.dynamic] ?: true,
        drawerBg = p[K.drawerBg].toEnum(DrawerBg.SYSTEM),
        drawerAlpha = p[K.drawerAlpha] ?: 0.96f,
        iconPack = p[K.iconPack],
        themedIcons = p[K.themed] ?: false,
        badgeNumbers = p[K.badges] ?: true,
        columns = p[K.columns] ?: 5,
        showLabels = p[K.labels] ?: true,
        homePages = p[K.pages] ?: 2,
        dock = p[K.dock]?.lines()?.filter { it.isNotBlank() } ?: emptyList(),
        dockInit = p[K.dockInit] ?: false,
        home = p[K.home]?.lines()?.mapNotNull { HomeItem.decode(it) } ?: emptyList(),
    )

    private fun write(p: MutablePreferences, s: LauncherSettings) {
        p[K.theme] = s.themeMode.name
        p[K.dynamic] = s.dynamicColor
        p[K.drawerBg] = s.drawerBg.name
        p[K.drawerAlpha] = s.drawerAlpha
        val pack = s.iconPack
        if (pack == null) p.remove(K.iconPack) else p[K.iconPack] = pack
        p[K.themed] = s.themedIcons
        p[K.badges] = s.badgeNumbers
        p[K.columns] = s.columns
        p[K.labels] = s.showLabels
        p[K.pages] = s.homePages
        p[K.dock] = s.dock.take(DOCK_MAX).joinToString("\n")
        p[K.dockInit] = s.dockInit
        p[K.home] = s.home.joinToString("\n") { it.encode() }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        runCatching { enumValueOf<E>(this!!) }.getOrDefault(default)
}
