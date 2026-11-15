package dev.azagra.launcher_aosp.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.InputStream
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class IconPacks(private val ctx: Context) {

    data class Pack(
        val pkg: String,
        val label: String,
        val icon: ImageBitmap? = null,
    )

    private var loadedPkg: String? = null
    private var componentMap: Map<String, String> = emptyMap()
    private var packageMap: Map<String, String> = emptyMap()
    private var calendarMap: Map<String, String> = emptyMap()
    private var iconBacks: List<String> = emptyList()
    private var iconMasks: List<String> = emptyList()
    private var iconUpons: List<String> = emptyList()
    private var scaleFactor: Float = 1f
    private var res: Resources? = null

    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "org.adw.ActivityStarter.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME",
        "fr.neamar.kiss.THEME",
        "app.lawnchair.icons.THEMED_ICON",
        "com.fede.launcher.THEME_ICONPACK",
        "com.gau.go.launcherex.theme",
        "ginlemon.smartlauncher.THEMES",
    )

    fun installed(): List<Pack> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, Pack>()
        for (a in actions) {
            val resolved = runCatching {
                pm.queryIntentActivities(Intent(a), PackageManager.ResolveInfoFlags.of(0))
            }.getOrDefault(emptyList())
            resolved.forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg != ctx.packageName && !found.containsKey(pkg)) {
                    val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                    val preview = runCatching {
                        ri.loadIcon(pm)?.toBitmap(96, 96)?.asImageBitmap()
                    }.getOrNull()
                    found[pkg] = Pack(pkg, label, preview)
                }
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    @Synchronized
    fun load(pkg: String?) {
        if (pkg == loadedPkg) return
        loadedPkg = pkg
        componentMap = emptyMap()
        packageMap = emptyMap()
        calendarMap = emptyMap()
        iconBacks = emptyList()
        iconMasks = emptyList()
        iconUpons = emptyList()
        scaleFactor = 1f
        res = null
        if (pkg == null) return

        runCatching {
            val r = ctx.packageManager.getResourcesForApplication(pkg)
            val xmlId = r.getIdentifier("appfilter", "xml", pkg)
            var stream: InputStream? = null
            val parser: XmlPullParser = if (xmlId != 0) {
                r.getXml(xmlId)
            } else {
                val rawId = r.getIdentifier("appfilter", "raw", pkg)
                stream = if (rawId != 0) r.openRawResource(rawId) else r.assets.open("appfilter.xml")
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(stream, "UTF-8")
                }
            }

            val cMap = HashMap<String, String>(2048)
            val pMap = HashMap<String, String>(1024)
            val calMap = HashMap<String, String>(32)
            val backs = ArrayList<String>()
            val masks = ArrayList<String>()
            val upons = ArrayList<String>()
            var scale = 1f

            try {
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "item" -> {
                                val rawComp = parser.getAttributeValue(null, "component")
                                val dr = parser.getAttributeValue(null, "drawable")
                                if (!rawComp.isNullOrBlank() && !dr.isNullOrBlank()) {
                                    registerComponentMapping(rawComp, dr, cMap, pMap)
                                }
                            }
                            "calendar" -> {
                                val rawComp = parser.getAttributeValue(null, "component")
                                val prefix = parser.getAttributeValue(null, "prefix")
                                if (!rawComp.isNullOrBlank() && !prefix.isNullOrBlank()) {
                                    registerComponentMapping(rawComp, prefix, calMap, null)
                                }
                            }
                            "iconback" -> collectImgAttributes(parser, backs)
                            "iconmask" -> collectImgAttributes(parser, masks)
                            "iconupon" -> collectImgAttributes(parser, upons)
                            "scale" -> {
                                parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.let { f ->
                                    if (f in 0.2f..2.0f) scale = f
                                }
                            }
                        }
                    }
                    ev = parser.next()
                }
            } finally {
                runCatching { stream?.close() }
            }

            componentMap = cMap
            packageMap = pMap
            calendarMap = calMap
            iconBacks = backs
            iconMasks = masks
            iconUpons = upons
            scaleFactor = scale
            res = r
        }
    }

    private fun collectImgAttributes(parser: XmlPullParser, out: MutableList<String>) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            val attrVal = parser.getAttributeValue(i)
            if (attrName != null && attrName.startsWith("img") && !attrVal.isNullOrBlank()) {
                out.add(attrVal)
            }
        }
    }

    private fun registerComponentMapping(
        rawComp: String,
        target: String,
        compOut: MutableMap<String, String>,
        pkgOut: MutableMap<String, String>?,
    ) = registerComponentMappingToMaps(rawComp, target, compOut, pkgOut)

    private fun loadPackDrawable(r: Resources, pkg: String, drawableName: String): Drawable? {
        var id = r.getIdentifier(drawableName, "drawable", pkg)
        if (id == 0) id = r.getIdentifier(drawableName, "mipmap", pkg)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(r, id, null) }.getOrNull()
    }

    @Synchronized
    fun icon(c: ComponentName): Drawable? {
        val r = res ?: return null
        val pkg = loadedPkg ?: return null
        val fullKey = "${c.packageName}/${c.className}"
        val shortKey = c.flattenToShortString()

        val calPrefix = calendarMap[fullKey] ?: calendarMap[shortKey]
        if (calPrefix != null) {
            val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            loadPackDrawable(r, pkg, resolveCalendarDrawableName(calPrefix, day))?.let { return it }
        }

        val name = resolvePackDrawableName(c.packageName, c.className, componentMap, packageMap)
            ?: return null

        return loadPackDrawable(r, pkg, name)
    }

    @Synchronized
    fun renderBitmap(c: ComponentName, baseDrawable: Drawable, sizePx: Int): Bitmap? {
        val r = res ?: return null
        val pkg = loadedPkg ?: return null

        icon(c)?.let { packDrawable ->
            return runCatching { packDrawable.toBitmap(sizePx, sizePx) }.getOrNull()
        }

        if (iconBacks.isEmpty() && iconMasks.isEmpty() && iconUpons.isEmpty()) {
            return null
        }

        return runCatching {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val iconLayer = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val iconCanvas = Canvas(iconLayer)
            val scaledSize = (sizePx * clampIconPackScale(scaleFactor)).roundToInt().coerceAtLeast(1)
            val pad = (sizePx - scaledSize) / 2f
            val rawBmp = baseDrawable.toBitmap(scaledSize, scaledSize)
            iconCanvas.drawBitmap(rawBmp, pad, pad, paint)

            if (iconMasks.isNotEmpty()) {
                val maskName = iconMasks[abs(c.packageName.hashCode()) % iconMasks.size]
                loadPackDrawable(r, pkg, maskName)?.let { maskDr ->
                    val maskBmp = maskDr.toBitmap(sizePx, sizePx)
                    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    }
                    iconCanvas.drawBitmap(maskBmp, 0f, 0f, maskPaint)
                }
            }

            if (iconBacks.isNotEmpty()) {
                val backName = iconBacks[abs(c.packageName.hashCode()) % iconBacks.size]
                loadPackDrawable(r, pkg, backName)?.let { backDr ->
                    canvas.drawBitmap(backDr.toBitmap(sizePx, sizePx), 0f, 0f, paint)
                }
            }

            canvas.drawBitmap(iconLayer, 0f, 0f, paint)

            if (iconUpons.isNotEmpty()) {
                val uponName = iconUpons[abs(c.packageName.hashCode()) % iconUpons.size]
                loadPackDrawable(r, pkg, uponName)?.let { uponDr ->
                    canvas.drawBitmap(uponDr.toBitmap(sizePx, sizePx), 0f, 0f, paint)
                }
            }

            out
        }.getOrNull()
    }
}

internal fun registerComponentMappingToMaps(
    rawComp: String,
    target: String,
    compOut: MutableMap<String, String>,
    pkgOut: MutableMap<String, String>?,
) {
    val inside = rawComp.removePrefix("ComponentInfo{").removeSuffix("}").trim()
    val slash = inside.indexOf('/')
    if (slash <= 0) {
        compOut[rawComp] = target
        return
    }
    val pkg = inside.substring(0, slash)
    val cls = inside.substring(slash + 1)
    val fullCls = if (cls.startsWith(".")) "$pkg$cls" else cls
    val shortCls = if (cls.startsWith(pkg)) cls.removePrefix(pkg) else cls

    compOut["$pkg/$fullCls"] = target
    compOut["$pkg/$shortCls"] = target
    compOut["ComponentInfo{$pkg/$fullCls}"] = target
    compOut["ComponentInfo{$pkg/$shortCls}"] = target
    pkgOut?.putIfAbsent(pkg, target)
}

internal fun resolvePackDrawableName(
    pkg: String,
    className: String,
    componentMap: Map<String, String>,
    packageMap: Map<String, String>,
): String? {
    val fullKey = "$pkg/$className"
    val shortCls = if (className.startsWith(pkg)) className.removePrefix(pkg) else className
    val shortKey = "$pkg/$shortCls"
    return componentMap[fullKey]
        ?: componentMap[shortKey]
        ?: packageMap[pkg]
}

internal fun resolveCalendarDrawableName(prefix: String, dayOfMonth: Int): String =
    "$prefix${dayOfMonth.coerceIn(1, 31)}"

internal fun clampIconPackScale(rawScale: Float): Float =
    rawScale.coerceIn(0.4f, 1.2f)
