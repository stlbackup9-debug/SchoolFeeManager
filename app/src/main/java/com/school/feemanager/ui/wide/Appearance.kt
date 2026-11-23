package com.school.feemanager.ui.wide

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Properties

/** The app colours a school can choose (sidebar / buttons / headings + highlight). */
enum class Accent(val label: String, val base: Color, val highlight: Color) {
    BLUE("School blue", Color(0xFF1F4E79), Color(0xFFE2B33C)),
    ROYAL("Royal blue", Color(0xFF1C4FA0), Color(0xFFFFC94A)),
    GREEN("Forest green", Color(0xFF1E6B45), Color(0xFFE2B33C)),
    TEAL("Teal", Color(0xFF0F5E66), Color(0xFFF2C94C)),
    MAROON("Maroon", Color(0xFF7A1F2B), Color(0xFFE8C468)),
    PURPLE("Royal purple", Color(0xFF4B2E83), Color(0xFFE2B33C)),
    CHARCOAL("Charcoal", Color(0xFF2F3B4A), Color(0xFF5BC0EB)),
}

/** Every colour the Windows screens use, for one accent in light or night mode. */
data class Palette(
    val night: Boolean,
    val sidebar: Color,
    val onSidebar: Color,
    val sidebarSelected: Color,
    val sidebarSelectedText: Color,
    val accent: Color,        // headings, icons, links, checkboxes
    val button: Color,        // filled buttons
    val onButton: Color,
    val highlight: Color,     // gold line / sidebar icons
    val background: Color,
    val surface: Color,
    val tableHeader: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val green: Color,
    val red: Color,
    val amber: Color,
    val link: Color,
    val selectionBar: Color,
    val footer: Color,
    val groupRow: Color,
    val selectedRow: Color,
    val inputBorder: Color,
    val placeholder: Color,
    val inputDisabled: Color,
) {
    companion object {
        fun of(a: Accent, night: Boolean): Palette = if (!night) Palette(
            night = false,
            sidebar = a.base, onSidebar = Color.White, sidebarSelected = Color.White, sidebarSelectedText = a.base,
            accent = a.base, button = a.base, onButton = Color.White, highlight = a.highlight,
            background = Color(0xFFF3F6FA), surface = Color.White, tableHeader = lerp(a.base, Color.White, 0.86f),
            line = Color(0xFFDCE3EB), text = Color(0xFF1E2A36), muted = Color(0xFF5C6B7A),
            green = Color(0xFF2E7D32), red = Color(0xFFC62828), amber = Color(0xFFB7791F), link = lerp(a.base, Color(0xFF2F6FB0), 0.35f),
            selectionBar = lerp(a.base, Color.White, 0.9f), footer = Color(0xFFF1F4F8), groupRow = lerp(a.base, Color.White, 0.965f),
            selectedRow = lerp(a.base, Color.White, 0.92f), inputBorder = Color(0xFFC6D0DB), placeholder = Color(0xFF8A97A6), inputDisabled = Color(0xFFF6F8FB),
        ) else {
            val surface = Color(0xFF1A1F26)
            Palette(
                night = true,
                sidebar = lerp(a.base, Color.Black, 0.45f), onSidebar = Color(0xFFE8ECF1), sidebarSelected = lerp(a.base, Color.White, 0.82f),
                sidebarSelectedText = lerp(a.base, Color.Black, 0.35f),
                accent = lerp(a.base, Color.White, 0.55f), button = lerp(a.base, Color.White, 0.18f), onButton = Color.White, highlight = a.highlight,
                background = Color(0xFF11151A), surface = surface, tableHeader = lerp(a.base, surface, 0.55f),
                line = Color(0xFF2C343E), text = Color(0xFFE4E9EF), muted = Color(0xFF9AA6B2),
                green = Color(0xFF6BC46F), red = Color(0xFFF0706C), amber = Color(0xFFF2B35B), link = lerp(a.base, Color.White, 0.6f),
                selectionBar = lerp(a.base, surface, 0.7f), footer = Color(0xFF20262E), groupRow = Color(0xFF1E242C),
                selectedRow = lerp(a.base, surface, 0.72f), inputBorder = Color(0xFF3A4450), placeholder = Color(0xFF6F7C89), inputDisabled = Color(0xFF151A20),
            )
        }
    }

    fun colorScheme(): ColorScheme = if (!night) lightColorScheme(
        primary = accent, onPrimary = onButton, primaryContainer = tableHeader, onPrimaryContainer = accent,
        secondary = link, onSecondary = Color.White, secondaryContainer = selectionBar, onSecondaryContainer = accent, tertiary = highlight,
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = Color(0xFFEEF2F7), onSurfaceVariant = muted, outline = Color(0xFFC3CCD6), outlineVariant = line, error = red,
        surfaceContainer = Color(0xFFF1F4F8), surfaceContainerHigh = Color(0xFFEBEFF4), surfaceContainerHighest = Color(0xFFE4E9EF),
        surfaceContainerLow = Color(0xFFF7F9FB), surfaceContainerLowest = Color.White,
    ) else darkColorScheme(
        primary = accent, onPrimary = Color(0xFF0E1216), primaryContainer = tableHeader, onPrimaryContainer = text,
        secondary = link, onSecondary = Color(0xFF0E1216), secondaryContainer = selectionBar, onSecondaryContainer = text, tertiary = highlight,
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = Color(0xFF232A33), onSurfaceVariant = muted, outline = inputBorder, outlineVariant = line, error = red,
        surfaceContainer = Color(0xFF20262E), surfaceContainerHigh = Color(0xFF252C35), surfaceContainerHighest = Color(0xFF2B333D),
        surfaceContainerLow = surface, surfaceContainerLowest = background,
    )
}

/**
 * The colours used by the Windows screens. They follow the chosen colour and night mode; reading them in
 * composition (or drawing) is tracked, so everything repaints when the user changes the appearance.
 */
object School {
    var palette by mutableStateOf(Palette.of(Accent.BLUE, false))
    val Navy get() = palette.accent
    val Sidebar get() = palette.sidebar
    val OnSidebar get() = palette.onSidebar
    val SidebarSelected get() = palette.sidebarSelected
    val SidebarSelectedText get() = palette.sidebarSelectedText
    val Button get() = palette.button
    val OnButton get() = palette.onButton
    val Gold get() = palette.highlight
    val Background get() = palette.background
    val Surface get() = palette.surface
    val TableHeader get() = palette.tableHeader
    val Line get() = palette.line
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Green get() = palette.green
    val Red get() = palette.red
    val Amber get() = palette.amber
    val Blue get() = palette.link
    val SelectionBar get() = palette.selectionBar
    val Footer get() = palette.footer
    val GroupRow get() = palette.groupRow
    val SelectedRow get() = palette.selectedRow
    val InputBorder get() = palette.inputBorder
    val Placeholder get() = palette.placeholder
    val InputDisabled get() = palette.inputDisabled
}

/** Which screen layout to use: chosen from the screen size, or fixed by the user. */
enum class LayoutMode(val label: String) { AUTO("Automatic"), PHONE("Phone layout"), TABLET("Tablet layout") }

/** Sidebar of the tablet / Windows layout: names and icons, icons only, or chosen from the width. */
enum class SidebarMode(val label: String) { AUTO("Automatic"), FULL("Full (icons and names)"), COMPACT("Compact (icons)") }

/** Scale & layout, colour and night mode – saved per device. */
data class DisplaySettings(
    /** size of everything (like Windows "Scale"): 0.8 … 2.0 */
    val scale: Float = 1f,
    /** extra text size on top of the scale: 0.85 … 1.5 */
    val textSize: Float = 1f,
    val layout: LayoutMode = LayoutMode.AUTO,
    val sidebar: SidebarMode = SidebarMode.AUTO,
    val accent: Accent = Accent.BLUE,
    val night: Boolean = false,
    /** follow the phone's / Windows' dark-mode setting instead of [night] */
    val followSystem: Boolean = false,
) {
    companion object {
        val SCALES = listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
        const val MIN_TEXT = 0.85f
        const val MAX_TEXT = 1.5f
    }
}

/** Holds the display settings and saves them to display.properties (Windows: data folder; Android: app files). */
class DisplayState(private val file: File?, private val defaults: DisplaySettings = DisplaySettings()) {
    var settings by mutableStateOf(load())
        private set

    init { School.palette = Palette.of(settings.accent, settings.night) }

    fun update(change: (DisplaySettings) -> DisplaySettings) {
        val s = change(settings).let {
            it.copy(scale = it.scale.coerceIn(0.8f, 2f), textSize = it.textSize.coerceIn(DisplaySettings.MIN_TEXT, DisplaySettings.MAX_TEXT))
        }
        settings = s
        if (!s.followSystem) School.palette = Palette.of(s.accent, s.night)
        save()
    }

    fun reset() = update { defaults }

    fun zoom(step: Int) = update { s ->
        val list = DisplaySettings.SCALES
        val i = list.indexOfFirst { it >= s.scale - 0.001f }.let { if (it < 0) list.lastIndex else it }
        s.copy(scale = list[(i + step).coerceIn(0, list.lastIndex)])
    }

    private fun load(): DisplaySettings {
        val p = Properties()
        try { file?.takeIf { it.exists() }?.inputStream()?.use { p.load(it) } } catch (_: Exception) {}
        val d = defaults
        return DisplaySettings(
            scale = p.getProperty("scale")?.toFloatOrNull()?.coerceIn(0.8f, 2f) ?: d.scale,
            textSize = p.getProperty("textSize")?.toFloatOrNull()?.coerceIn(DisplaySettings.MIN_TEXT, DisplaySettings.MAX_TEXT) ?: d.textSize,
            layout = p.getProperty("layout")?.let { v -> LayoutMode.entries.firstOrNull { it.name == v } } ?: d.layout,
            sidebar = p.getProperty("sidebar")?.let { v -> SidebarMode.entries.firstOrNull { it.name == v } }
                ?: p.getProperty("compactSidebar")?.toBoolean()?.let { if (it) SidebarMode.COMPACT else SidebarMode.FULL }   // older files
                ?: d.sidebar,
            accent = p.getProperty("accent")?.let { a -> Accent.entries.firstOrNull { it.name == a } } ?: d.accent,
            night = p.getProperty("night")?.toBoolean() ?: d.night,
            followSystem = p.getProperty("followSystem")?.toBoolean() ?: d.followSystem,
        )
    }

    private fun save() {
        val f = file ?: return
        try {
            f.absoluteFile.parentFile?.mkdirs()
            val p = Properties()
            p["scale"] = settings.scale.toString(); p["textSize"] = settings.textSize.toString()
            p["layout"] = settings.layout.name; p["sidebar"] = settings.sidebar.name
            p["accent"] = settings.accent.name; p["night"] = settings.night.toString(); p["followSystem"] = settings.followSystem.toString()
            f.outputStream().use { p.store(it, "School Fee Manager – display settings (Scale & Layout, colour, night mode)") }
        } catch (e: Exception) {
            System.err.println("Cannot save display settings: ${e.message}")
        }
    }
}

val LocalDisplay = staticCompositionLocalOf<DisplayState?> { null }

private val desktopType = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

/**
 * The app theme (Android and Windows): colours from the chosen school colour and day / night (or the system's
 * dark mode), size from the Scale & Layout settings (on top of the device's own display scaling).
 */
@Composable
fun SchoolTheme(content: @Composable () -> Unit) {
    val s = LocalDisplay.current?.settings ?: DisplaySettings()
    val night = if (s.followSystem) androidx.compose.foundation.isSystemInDarkTheme() else s.night
    val palette = Palette.of(s.accent, night)
    if (School.palette != palette) School.palette = palette   // same value every time for these inputs, so this settles at once
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * s.scale, base.fontScale * s.textSize)) {
        MaterialTheme(colorScheme = School.palette.colorScheme(), typography = desktopType) {
            // text without an explicit colour follows day / night
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides School.Text,
                com.school.feemanager.ui.LocalBrandColor provides School.Sidebar,
                content = content,
            )
        }
    }
}
