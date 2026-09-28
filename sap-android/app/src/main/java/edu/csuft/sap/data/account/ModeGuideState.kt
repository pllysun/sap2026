package edu.csuft.sap.data.account

import android.content.Context

/** 引导进度按平台账号保存，不因切号而互相覆盖。 */
object ModeGuideState {
    private const val PREFS = "mode_guide"
    private const val VERSION = 1

    fun pending(context: Context, owner: String, resolved: Boolean, online: Boolean,
                member: Boolean, accessLevel: Int): ModeGuideKind? {
        if (owner == "_" || !resolved || !online) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return nextModeGuide(
            introDone = prefs.getInt("intro_$owner", 0) >= VERSION,
            academicDone = prefs.getInt("academic_$owner", 0) >= VERSION,
            fullAccess = member || accessLevel >= 2,
        )
    }

    fun complete(context: Context, owner: String, kind: ModeGuideKind, fullAccess: Boolean) {
        if (owner == "_") return
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (kind == ModeGuideKind.INTRO) editor.putInt("intro_$owner", VERSION)
        if (kind == ModeGuideKind.ACADEMIC_UNLOCKED || fullAccess) editor.putInt("academic_$owner", VERSION)
        editor.apply()
    }
}

enum class ModeGuideKind { INTRO, ACADEMIC_UNLOCKED }

/** 新用户看可用模式；游客稍后因云控获得完整能力时补看教务模式。 */
internal fun nextModeGuide(introDone: Boolean, academicDone: Boolean, fullAccess: Boolean): ModeGuideKind? = when {
    !introDone -> ModeGuideKind.INTRO
    fullAccess && !academicDone -> ModeGuideKind.ACADEMIC_UNLOCKED
    else -> null
}
