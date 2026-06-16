<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-header">
        <div class="logo-area" v-if="!isCollapsed">
          <img src="/logo.png" style="width:36px;height:36px;border-radius:6px;object-fit:cover;" alt="Logo" />
          <div class="logo-info">
            <span class="logo-text">软件协会</span>
            <span class="logo-sub">管理平台</span>
          </div>
        </div>
        <img v-else src="/logo.png" style="width:28px;height:28px;border-radius:6px;object-fit:cover;" alt="Logo" />
      </div>

      <el-menu
        :default-active="currentRoute"
        :collapse="isCollapsed"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/member">
          <el-icon><User /></el-icon>
          <span>成员管理</span>
        </el-menu-item>
        <el-menu-item index="/study">
          <el-icon><Reading /></el-icon>
          <span>学习小组</span>
        </el-menu-item>
        <el-menu-item index="/note">
          <el-icon><Notebook /></el-icon>
          <span>软协笔记</span>
        </el-menu-item>
        <el-menu-item index="/activity">
          <el-icon><Calendar /></el-icon>
          <span>活动管理</span>
        </el-menu-item>
        <el-menu-item index="/finance">
          <el-icon><Wallet /></el-icon>
          <span>财务管理</span>
        </el-menu-item>
        <el-menu-item index="/join">
          <el-icon><UserFilled /></el-icon>
          <span>入会管理</span>
        </el-menu-item>
        <el-menu-item index="/log">
          <el-icon><Document /></el-icon>
          <span>日志管理</span>
        </el-menu-item>
        <el-menu-item v-if="isLeaderOrSuper" index="/analytics">
          <el-icon><TrendCharts /></el-icon>
          <span>流量统计</span>
        </el-menu-item>
        <el-menu-item v-if="isLeaderOrSuper" index="/app-release">
          <el-icon><Cellphone /></el-icon>
          <span>App 版本发布</span>
        </el-menu-item>
        <el-menu-item v-if="isLeaderOrSuper" index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="collapse-btn" @click="isCollapsed = !isCollapsed">
          <el-icon><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
        </div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main-area">
      <header class="top-bar">
        <div class="breadcrumb-area">
          <span class="current-title">{{ currentTitle }}</span>
        </div>
        <div class="user-area">
          <span class="user-name">{{ userName }}</span>
          <el-dropdown trigger="click">
            <div class="avatar-wrap">
              <el-avatar :size="32" :src="userAvatar" />
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content-area">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="$route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserInfo, logout as logoutApi } from '../api'
import { clearAuth } from '../utils/request'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)
const userName = ref('')
const userAvatar = ref('')
const userRoles = ref([])

const currentRoute = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '首页')
const isLeaderOrSuper = computed(() => userRoles.value.includes(0) || userRoles.value.includes(1))

onMounted(async () => {
  try {
    const res = await getUserInfo()
    userName.value = res.data.user.nickname || res.data.user.name
    userAvatar.value = res.data.user.avatar
    userRoles.value = (res.data.roles || []).map(Number)
  } catch (e) {
    // ignore
  }
})

const handleLogout = async () => {
  try {
    await logoutApi()
  } catch (e) {}
  clearAuth()
  ElMessage.success('已退出')
  router.push('/login')
}
</script>


