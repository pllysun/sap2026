package edu.csuft.sap.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDialogTest {

    @Test
    fun splitsServerChangelogIntoSeparateItems() {
        val raw = """
            优化教学评价手动填写与一键满评的兼容性
            支持根据学校平台限制自动调整评价分数

            修复课表分享图片可能显示旧周次
        """.trimIndent()

        assertEquals(
            listOf(
                "优化教学评价手动填写与一键满评的兼容性",
                "支持根据学校平台限制自动调整评价分数",
                "修复课表分享图片可能显示旧周次",
            ),
            changelogItems(raw),
        )
    }

    @Test
    fun removesManualBulletPrefixesWithoutDuplicatingThem() {
        assertEquals(
            listOf("新增功能", "优化体验", "修复问题", "兼容旧格式"),
            changelogItems("· 新增功能\n• 优化体验\n- 修复问题\n1. 兼容旧格式"),
        )
    }

    @Test
    fun usesFallbackForMissingChangelog() {
        assertEquals(listOf("优化与修复。"), changelogItems(" \n "))
        assertEquals(listOf("优化与修复。"), changelogItems(null))
    }
}
