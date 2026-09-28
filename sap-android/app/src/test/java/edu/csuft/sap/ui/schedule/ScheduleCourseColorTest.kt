package edu.csuft.sap.ui.schedule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.gson.Gson
import edu.csuft.sap.data.schedule.*
import edu.csuft.sap.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleCourseColorTest {
    private val system = DisplayCourse("示例课", "", "", 1, 1, 2, emptyList(), 0, false)
    private val strong = ScheduleSettings(cardColorIntensityPercent = 200)

    @Test fun defaultMatchesThePrevious165PercentPalette() {
        // 升级前 165% 的 12 色快照，避免只改滑条标签却没有调整实际色彩。
        val previous165 = listOf(0xFF75B3F6, 0xFFF8976C, 0xFF8290E9, 0xFF9981F3,
            0xFFF9C559, 0xFFF779AA, 0xFF7FAEE4, 0xFFF9B35F, 0xFFF67B7B, 0xFF98AAC8,
            0xFFA686EE, 0xFFF0D859)
        CoursePalette.indices.forEach { index ->
            val course = system.copy(colorIndex = index)
            val expected = Color(previous165[index])
            val actual = scheduleCourseColor(course, ScheduleSettings()).container
            assertEquals(expected.red, actual.red, 1f / 255f)
            assertEquals(expected.green, actual.green, 1f / 255f)
            assertEquals(expected.blue, actual.blue, 1f / 255f)
            assertEquals(actual, scheduleCourseColor(course, ScheduleSettings(cardColorIntensityPercent = 100)).container)
            assertEquals(actual, scheduleCourseColor(course, ScheduleSettings(cardColorIntensity = 165)).container)
        }
        val custom = system.copy(isCustom = true, customColor = 0xFF7955CC)
        assertEquals(customCourseColor(custom.customColor!!), scheduleCourseColor(custom, ScheduleSettings()))
    }

    @Test fun everyAutomaticPaletteCanBeMadeLighterAndStronger() {
        CoursePalette.indices.forEach { index ->
            val course = system.copy(colorIndex = index)
            val colors = listOf(0, 25, 50, 100, 150, 200).map {
                scheduleCourseColor(course, ScheduleSettings(cardColorIntensityPercent = it)).container
            }
            colors.zipWithNext().forEach { (lighter, darker) ->
                assertTrue("palette $index must become darker as intensity increases", lighter.luminance() > darker.luminance())
            }
        }
    }

    @Test fun checkboxUsesCourseOriginIncludingCustomCoursesWithPresetColors() {
        val presetCustom = system.copy(isCustom = true, customId = "custom-preset")
        val pickedCustom = presetCustom.copy(customColor = 0xFF7968BB)
        for (course in listOf(presetCustom, pickedCustom)) {
            val original = scheduleCourseColor(course, ScheduleSettings())
            assertEquals(original, scheduleCourseColor(course, strong))
            assertNotEquals(original.container, scheduleCourseColor(course, strong.copy(colorIntensityAffectsCustom = true)).container)
            assertEquals(original, scheduleCourseColor(course, strong.copy(colorIntensityAffectsCustom = false)))
        }
        assertNotEquals(paletteColor(0).container, scheduleCourseColor(system, strong).container)
    }

    @Test fun adjustmentPreservesHueAndDoesNotChangeOpacity() {
        for (base in CoursePalette.map { it.container } + Color(0xAA2244BB)) {
            val originalHue = normalizedChannels(base)
            for (intensity in listOf(25, 50, 150, 200)) {
                val adjusted = base.withCourseIntensity(intensity)
                val chroma = maxOf(adjusted.red, adjusted.green, adjusted.blue) - minOf(adjusted.red, adjusted.green, adjusted.blue)
                // sRGB 最终量化为 8 位；淡色的色差很小，按一个 RGB 色阶评估误差。
                normalizedChannels(adjusted).zip(originalHue).forEach { (a, b) -> assertEquals(b * chroma, a * chroma, 1.01f / 255f) }
                assertEquals(base.alpha, adjusted.alpha, 0.005f)
            }
        }
        assertEquals(scheduleCourseColor(system, strong).container,
            scheduleCourseColor(system, strong.copy(cardOpacity = 30)).container)
    }

    @Test fun adjustedPaletteTextRemainsReadableAtSupportedOpacities() {
        for (index in CoursePalette.indices) for (intensity in listOf(0, 25, 50, 61, 100, 150, 200)) for (opacity in listOf(30, 55, 100)) {
            val settings = ScheduleSettings(cardColorIntensityPercent = intensity, cardOpacity = opacity)
            val colors = scheduleCourseColor(system.copy(colorIndex = index), settings)
            val visible = colors.container.copy(alpha = settings.opacityFraction).compositeOver(PageBg)
            assertTrue("palette $index, intensity $intensity, opacity $opacity", contrastRatio(colors.onContainer, visible) >= 4.5f)
        }
    }

    @Test fun neutralCustomColorsRemainNeutral() {
        for (base in listOf(Color.Black, Color.White, Color.Gray)) for (intensity in listOf(25, 200)) {
            val color = base.withCourseIntensity(intensity)
            assertEquals(color.red, color.green, 0.001f)
            assertEquals(color.green, color.blue, 0.001f)
        }
    }

    @Test fun missingAndLegacyDefaultSettingsUseTheNewBaseline() {
        val old = Gson().fromJson("{\"cardOpacity\":70}", ScheduleSettings::class.java)
        assertEquals(100, old.colorIntensityPercent)
        assertFalse(old.colorIntensityAffectsCustom)
        for (value in listOf(-1, 0, 24, 100, 201)) {
            assertEquals(100, old.copy(cardColorIntensity = value).colorIntensityPercent)
        }
        for (value in listOf(-1, 201)) {
            assertEquals(100, old.copy(cardColorIntensityPercent = value).colorIntensityPercent)
        }
    }

    @Test fun legacyCustomIntensityIsConvertedOnlyOnceAcrossSettingsAndJson() {
        for ((legacy, converted) in mapOf(25 to 15, 100 to 100, 150 to 91, 165 to 100, 200 to 121)) {
            val old = Gson().fromJson("{\"cardColorIntensity\":$legacy}", ScheduleSettings::class.java)
            assertEquals(converted, old.colorIntensityPercent)
            var settings = old
            repeat(3) {
                val display = Gson().fromJson(Gson().toJson(settings.toDisplaySettings()), ScheduleDisplaySettings::class.java)
                settings = ScheduleSettings().withDisplaySettings(display)
                assertEquals(converted, settings.colorIntensityPercent)
            }
        }
        val legacyDisplay = Gson().fromJson("{\"cardColorIntensity\":165}", ScheduleDisplaySettings::class.java)
        assertEquals(100, ScheduleSettings().withDisplaySettings(legacyDisplay).colorIntensityPercent)
    }

    @Test fun zeroIsWhiteAndRemainsZeroAfterSavingAndRestoring() {
        val settings = ScheduleSettings(cardColorIntensityPercent = 0)
        val restored = Gson().fromJson(Gson().toJson(settings), ScheduleSettings::class.java)
        assertEquals(0, restored.colorIntensityPercent)
        assertEquals(0, ScheduleSettings().withDisplaySettings(restored.toDisplaySettings()).colorIntensityPercent)
        CoursePalette.indices.forEach { index ->
            assertEquals(Color.White, scheduleCourseColor(system.copy(colorIndex = index), restored).container)
        }
        assertEquals(100, restored.withDefaultPersonalization().colorIntensityPercent)
    }

    @Test fun intensityPersistsGloballyAndResetPreservesSemesterData() {
        val settings = strong.copy(semesterStartDate = "2026-09-07", totalWeeks = 22, colorIntensityAffectsCustom = true)
        val root = ScheduleRoot(accounts = mapOf("first" to AccountData(profiles = listOf(
            ScheduleProfile("first-term", "课表", ProfileKind.TERM, settings = settings))),
            "second" to AccountData(profiles = listOf(ScheduleProfile("other-term", "另一课表", ProfileKind.TERM,
                settings = ScheduleSettings(semesterStartDate = "2026-02-23"))))))
            .withDisplaySettings(settings.toDisplaySettings())
        val restored = Gson().fromJson(Gson().toJson(root), ScheduleRoot::class.java)
        restored.accounts.values.flatMap { it.profiles }.forEach {
            assertEquals(200, it.settings.colorIntensityPercent)
            assertTrue(it.settings.colorIntensityAffectsCustom)
        }
        val reset = settings.withDefaultPersonalization()
        assertEquals(100, reset.colorIntensityPercent)
        assertFalse(reset.colorIntensityAffectsCustom)
        assertEquals(settings.semesterStartDate, reset.semesterStartDate)
        assertEquals(22, reset.totalWeeks)
    }

    private fun normalizedChannels(color: Color): List<Float> {
        val low = minOf(color.red, color.green, color.blue)
        val delta = maxOf(color.red, color.green, color.blue) - low
        return listOf(color.red, color.green, color.blue).map { (it - low) / delta }
    }
}
