package dev.azagra.forkyhome.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {

    const val BACKUP_APP_ID = "dev.azagra.forkyhome"
    const val BACKUP_VERSION = 1
    const val MAX_BACKUP_BYTES = 2 * 1024 * 1024 // 2 MB
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KDF_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    sealed interface InspectResult {
        data class Plain(val settings: LauncherSettings) : InspectResult
        data class NeedsPassword(val rawJson: String) : InspectResult
        data object Invalid : InspectResult
    }

    sealed interface DecryptResult {
        data class Success(val settings: LauncherSettings) : DecryptResult
        data object WrongPassword : DecryptResult
        data object Invalid : DecryptResult
    }

    data class RestorePlan(
        val settings: LauncherSettings,
        val skippedWidgets: Int,
        val missingApps: Int,
        val missingIconPack: Boolean,
    )

    fun exportToJson(settings: LauncherSettings, password: String?): String {
        val settingsJson = encodeSettings(settings)
        val root = JSONObject()
            .put("app", BACKUP_APP_ID)
            .put("version", BACKUP_VERSION)

        if (password.isNullOrEmpty()) {
            root.put("encrypted", false)
            root.put("settings", settingsJson)
        } else {
            val random = SecureRandom()
            val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
            val key = deriveKey(password, salt, KDF_ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val encryptedBytes = cipher.doFinal(settingsJson.toString().toByteArray(Charsets.UTF_8))

            val encoder = Base64.getEncoder()
            root.put("encrypted", true)
            root.put("kdf", KDF_ALGORITHM)
            root.put("iterations", KDF_ITERATIONS)
            root.put("salt", encoder.encodeToString(salt))
            root.put("iv", encoder.encodeToString(iv))
            root.put("payload", encoder.encodeToString(encryptedBytes))
        }
        return root.toString(2)
    }

    fun inspect(raw: String): InspectResult {
        if (raw.length > MAX_BACKUP_BYTES) return InspectResult.Invalid
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return InspectResult.Invalid
        if (root.optString("app") != BACKUP_APP_ID) return InspectResult.Invalid
        if (root.optInt("version", -1) != BACKUP_VERSION) return InspectResult.Invalid

        return if (root.optBoolean("encrypted", false)) {
            if (!root.has("salt") || !root.has("iv") || !root.has("payload")) {
                InspectResult.Invalid
            } else {
                InspectResult.NeedsPassword(raw)
            }
        } else {
            val settingsObj = root.optJSONObject("settings") ?: return InspectResult.Invalid
            val settings = runCatching { decodeSettings(settingsObj) }.getOrNull()
                ?: return InspectResult.Invalid
            InspectResult.Plain(settings)
        }
    }

    fun decrypt(raw: String, password: String): DecryptResult {
        if (password.isEmpty()) return DecryptResult.WrongPassword
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return DecryptResult.Invalid
        if (root.optString("app") != BACKUP_APP_ID || root.optInt("version", -1) != BACKUP_VERSION) {
            return DecryptResult.Invalid
        }
        if (!root.optBoolean("encrypted", false)) return DecryptResult.Invalid
        if (root.optString("kdf", KDF_ALGORITHM) != KDF_ALGORITHM) return DecryptResult.Invalid

        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(root.getString("salt")) }.getOrNull()
            ?: return DecryptResult.Invalid
        val iv = runCatching { decoder.decode(root.getString("iv")) }.getOrNull()
            ?: return DecryptResult.Invalid
        val payload = runCatching { decoder.decode(root.getString("payload")) }.getOrNull()
            ?: return DecryptResult.Invalid
        val iterations = root.optInt("iterations", KDF_ITERATIONS).coerceIn(10_000, 500_000)

        val plainBytes = try {
            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(payload)
        } catch (_: Exception) {
            return DecryptResult.WrongPassword
        }

        val settingsObj = runCatching { JSONObject(String(plainBytes, Charsets.UTF_8)) }.getOrNull()
            ?: return DecryptResult.Invalid
        val settings = runCatching { decodeSettings(settingsObj) }.getOrNull()
            ?: return DecryptResult.Invalid
        return DecryptResult.Success(settings)
    }

    fun prepareForRestore(
        imported: LauncherSettings,
        installedKeys: Set<String>,
        installedPacks: Set<String>,
    ): RestorePlan {
        var skippedWidgets = 0
        var missingApps = 0

        val filteredHome = buildList {
            for (item in imported.home) {
                when (item.kind) {
                    ItemKind.WIDGET -> skippedWidgets++
                    ItemKind.APP -> {
                        if (item.data in installedKeys) {
                            add(item)
                        } else {
                            missingApps++
                        }
                    }
                    ItemKind.FOLDER -> {
                        val payload = FolderPayload.decode(item.data)
                        val keptApps = payload.apps.filter { it in installedKeys }
                        missingApps += (payload.apps.size - keptApps.size)
                        if (keptApps.isNotEmpty()) {
                            add(item.copy(data = payload.copy(apps = keptApps).encode()))
                        }
                    }
                }
            }
        }
        val compactedHome = compactHomePages(filteredHome)

        val filteredDock = buildList {
            for (appKey in imported.dock) {
                if (appKey in installedKeys) {
                    add(appKey)
                } else {
                    missingApps++
                }
            }
        }.take(DOCK_MAX)

        val missingIconPack = imported.iconPack != null && imported.iconPack !in installedPacks
        val sanitized = imported.copy(
            iconPack = if (missingIconPack) null else imported.iconPack,
            home = compactedHome,
            homePages = activePageCount(compactedHome),
            dock = filteredDock,
            dockInit = true,
            onboardingDone = true,
        )

        return RestorePlan(
            settings = sanitized,
            skippedWidgets = skippedWidgets,
            missingApps = missingApps,
            missingIconPack = missingIconPack,
        )
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val chars = password.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, iterations, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun encodeSettings(s: LauncherSettings): JSONObject {
        val obj = JSONObject()
        obj.put("themeMode", s.themeMode.name)
        obj.put("dynamicColor", s.dynamicColor)
        obj.put("drawerBg", s.drawerBg.name)
        obj.put("drawerAlpha", s.drawerAlpha.toDouble())
        if (s.iconPack != null) obj.put("iconPack", s.iconPack)
        obj.put("iconShape", s.iconShape.name)
        obj.put("themedIcons", s.themedIcons)
        obj.put("badgeNumbers", s.badgeNumbers)
        obj.put("columns", s.columns)
        obj.put("drawerIconScale", s.drawerIconScale.toDouble())
        obj.put("showLabels", s.showLabels)
        obj.put("homeCols", s.homeCols)
        obj.put("homeRows", s.homeRows)
        obj.put("homeIconScale", s.homeIconScale.toDouble())
        obj.put("showHomeLabels", s.showHomeLabels)
        obj.put("lockDesktop", s.lockDesktop)
        obj.put("autoAddNewApps", s.autoAddNewApps)
        obj.put("swipeDownNotifications", s.swipeDownNotifications)
        obj.put("doubleTapToSleep", s.doubleTapToSleep)
        obj.put("showAtAGlance", s.showAtAGlance)
        obj.put("smartspaceShowDate", s.smartspaceShowDate)
        obj.put("smartspaceShowTime", s.smartspaceShowTime)
        obj.put("smartspaceTimeFormat", s.smartspaceTimeFormat.name)
        obj.put("smartspaceShowCalendar", s.smartspaceShowCalendar)
        obj.put("smartspaceShowAlarm", s.smartspaceShowAlarm)
        obj.put("smartspaceShowBattery", s.smartspaceShowBattery)

        val customIconsObj = JSONObject()
        s.customIcons.forEach { (k, v) -> customIconsObj.put(k, v) }
        obj.put("customIcons", customIconsObj)

        obj.put("hiddenApps", JSONArray(s.hiddenApps.toList()))
        obj.put("protectedApps", JSONArray(s.protectedApps.toList()))
        obj.put("dock", JSONArray(s.dock))

        val homeArr = JSONArray()
        s.home.forEach { item ->
            homeArr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("kind", item.kind.name)
                    .put("page", item.page)
                    .put("col", item.col)
                    .put("row", item.row)
                    .put("spanX", item.spanX)
                    .put("spanY", item.spanY)
                    .put("data", item.data)
            )
        }
        obj.put("home", homeArr)
        return obj
    }

    private fun decodeSettings(obj: JSONObject): LauncherSettings {
        val defaults = LauncherSettings()
        val customIconsMap = buildMap {
            val ci = obj.optJSONObject("customIcons")
            if (ci != null) {
                val keys = ci.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = ci.optString(k, "")
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                }
            }
        }
        val hiddenSet = obj.optJSONArray("hiddenApps").toStringSet()
        val protectedSet = obj.optJSONArray("protectedApps").toStringSet()
        val dockList = obj.optJSONArray("dock").toStringList().take(DOCK_MAX)
        val homeArr = obj.optJSONArray("home")
        val homeList = buildList {
            if (homeArr != null) {
                for (i in 0 until homeArr.length()) {
                    val itemObj = homeArr.optJSONObject(i) ?: continue
                    val kind = runCatching { ItemKind.valueOf(itemObj.optString("kind")) }.getOrNull() ?: continue
                    val id = itemObj.optInt("id", i + 1)
                    val page = itemObj.optInt("page", 0).coerceAtLeast(0)
                    val col = itemObj.optInt("col", 0).coerceAtLeast(0)
                    val row = itemObj.optInt("row", 0).coerceAtLeast(0)
                    val spanX = itemObj.optInt("spanX", 1).coerceIn(1, 6)
                    val spanY = itemObj.optInt("spanY", 1).coerceIn(1, 7)
                    val data = itemObj.optString("data", "")
                    if (data.isNotBlank()) {
                        add(HomeItem(id, kind, page, col, row, spanX, spanY, data))
                    }
                }
            }
        }
        val compactedHome = compactHomePages(homeList)
        return LauncherSettings(
            themeMode = obj.optString("themeMode").toEnumOrDefault(defaults.themeMode),
            dynamicColor = obj.optBoolean("dynamicColor", defaults.dynamicColor),
            drawerBg = obj.optString("drawerBg").toEnumOrDefault(defaults.drawerBg),
            drawerAlpha = obj.optDouble("drawerAlpha", defaults.drawerAlpha.toDouble()).toFloat().coerceIn(0.50f, 1.0f),
            iconPack = if (obj.isNull("iconPack")) null else obj.optString("iconPack", "").takeIf { it.isNotBlank() },
            iconShape = obj.optString("iconShape").toEnumOrDefault(defaults.iconShape),
            themedIcons = obj.optBoolean("themedIcons", defaults.themedIcons),
            badgeNumbers = obj.optBoolean("badgeNumbers", defaults.badgeNumbers),
            columns = obj.optInt("columns", defaults.columns).coerceIn(4, 6),
            drawerIconScale = obj.optDouble("drawerIconScale", defaults.drawerIconScale.toDouble()).toFloat().coerceIn(0.80f, 1.20f),
            showLabels = obj.optBoolean("showLabels", defaults.showLabels),
            homePages = activePageCount(compactedHome),
            homeCols = obj.optInt("homeCols", defaults.homeCols).coerceIn(4, 6),
            homeRows = obj.optInt("homeRows", defaults.homeRows).coerceIn(4, 7),
            homeIconScale = obj.optDouble("homeIconScale", defaults.homeIconScale.toDouble()).toFloat().coerceIn(0.80f, 1.20f),
            showHomeLabels = obj.optBoolean("showHomeLabels", defaults.showHomeLabels),
            lockDesktop = obj.optBoolean("lockDesktop", defaults.lockDesktop),
            autoAddNewApps = obj.optBoolean("autoAddNewApps", defaults.autoAddNewApps),
            swipeDownNotifications = obj.optBoolean("swipeDownNotifications", defaults.swipeDownNotifications),
            doubleTapToSleep = obj.optBoolean("doubleTapToSleep", defaults.doubleTapToSleep),
            showAtAGlance = obj.optBoolean("showAtAGlance", defaults.showAtAGlance),
            onboardingDone = true,
            smartspaceShowDate = obj.optBoolean("smartspaceShowDate", defaults.smartspaceShowDate),
            smartspaceShowTime = obj.optBoolean("smartspaceShowTime", defaults.smartspaceShowTime),
            smartspaceTimeFormat = obj.optString("smartspaceTimeFormat").toEnumOrDefault(defaults.smartspaceTimeFormat),
            smartspaceShowCalendar = obj.optBoolean("smartspaceShowCalendar", defaults.smartspaceShowCalendar),
            smartspaceShowAlarm = obj.optBoolean("smartspaceShowAlarm", defaults.smartspaceShowAlarm),
            smartspaceShowBattery = obj.optBoolean("smartspaceShowBattery", defaults.smartspaceShowBattery),
            customIcons = customIconsMap,
            hiddenApps = hiddenSet,
            protectedApps = protectedSet,
            dock = dockList,
            dockInit = true,
            home = compactedHome,
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i, "")
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()

    private inline fun <reified E : Enum<E>> String?.toEnumOrDefault(default: E): E =
        if (isNullOrBlank()) default else runCatching { enumValueOf<E>(this) }.getOrDefault(default)
}
