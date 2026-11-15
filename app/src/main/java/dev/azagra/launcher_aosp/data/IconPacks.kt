package dev.azagra.launcher_aosp.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser

/** Compatible con los packs estándar (ADW / Nova / Apex) que traen appfilter.xml. */
class IconPacks(private val ctx: Context) {

    data class Pack(val pkg: String, val label: String)

    private var loadedPkg: String? = null
    private var map: Map<String, String> = emptyMap()
    private var res: Resources? = null

    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "fr.neamar.kiss.THEME",
    )

    fun installed(): List<Pack> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, Pack>()
        for (a in actions) {
            pm.queryIntentActivities(Intent(a), PackageManager.ResolveInfoFlags.of(0)).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                found.getOrPut(pkg) { Pack(pkg, ri.loadLabel(pm).toString()) }
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    @Synchronized
    fun load(pkg: String?) {
        if (pkg == loadedPkg) return
        loadedPkg = pkg
        map = emptyMap()
        res = null
        if (pkg == null) return
        runCatching {
            val r = ctx.packageManager.getResourcesForApplication(pkg)
            val id = r.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) r.getXml(id) else
                Xml.newPullParser().also { it.setInput(r.assets.open("appfilter.xml"), null) }
            val m = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) m[comp] = dr
                }
                ev = parser.next()
            }
            map = m
            res = r
        }
    }

    @Synchronized
    fun icon(c: ComponentName): Drawable? {
        val r = res ?: return null
        val pkg = loadedPkg ?: return null
        val name = map["ComponentInfo{${c.packageName}/${c.className}}"] ?: return null
        var id = r.getIdentifier(name, "drawable", pkg)
        if (id == 0) id = r.getIdentifier(name, "mipmap", pkg)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(r, id, null) }.getOrNull()
    }
}
