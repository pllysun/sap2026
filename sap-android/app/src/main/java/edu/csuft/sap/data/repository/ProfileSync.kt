package edu.csuft.sap.data.repository

import edu.csuft.sap.data.remote.dto.MeData

/** 无头像也可能是完整资料；只有缺缓存/版本或资料更新时才需要完整接口。 */
internal fun needsFullProfile(cached: MeData?, light: MeData): Boolean =
    cached?.user == null || cached.updatedAt == null || light.updatedAt == null ||
        light.updatedAt > cached.updatedAt

/** 轻量接口不含头像，不能用它的新时间戳确认“已取得新头像”，否则失败后不再重试。 */
internal fun mergeProfileWithoutAvatar(cached: MeData?, light: MeData): MeData = light.copy(
    user = light.user?.copy(avatar = cached?.user?.avatar) ?: cached?.user,
    updatedAt = cached?.updatedAt,
)
