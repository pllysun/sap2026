package edu.csuft.sap.design

import android.content.Context
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.remote.dto.MeData
import edu.csuft.sap.data.remote.dto.UserDto
import edu.csuft.sap.di.Graph

/** 配合独立 avatarqa 包和本地 HTTP 桩，用真实“我的”页面验证慢加载与缓存命中。 */
internal fun seedProfileAvatarPreview(context: Context) {
    check(BuildConfig.APPLICATION_ID.endsWith(".avatarqa"))
    CurrentAccount.set("avatar-qa")
    MemberState.setAccess(listOf(4), 1)
    MemberState.setMode(context, AppMode.CLASS)
    Graph.accountManager.useClass("avatar-qa")
    Graph.userStore.save(MeData(
        user = UserDto(id = 42, studentId = "avatar-qa", name = "头像缓存验收",
            avatar = BuildConfig.BASE_URL + "/avatar.png"),
        roles = listOf(4), appAccessLevel = 1, updatedAt = 1L,
    ))
}
