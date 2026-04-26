<template>
  <div style="display: flex; flex-direction: column; min-height: 100vh;">
    <nav class="nav" :class="{ 'nav--hidden': navHidden, 'nav--gradient': true }">
      <!-- Hamburger (mobile only) -->
      <div class="nav__hamburger" @click="drawerOpen = true">
        <span></span><span></span><span></span>
      </div>

      <div class="nav__brand">
        <img src="/logo.png" class="nav__logo" alt="Logo" />
        <span class="nav__name">中南林业科技大学软件协会</span>
      </div>

      <div class="nav__links-wrap">
        <router-link to="/home" class="nav__link" :class="{ active: $route.path === '/home' }">首页</router-link>
        <router-link v-if="!isGuest" to="/study" class="nav__link" :class="{ active: $route.path.startsWith('/study') }">学习小组</router-link>
        <router-link to="/activities" class="nav__link" :class="{ active: $route.path === '/activities' }">软协活动</router-link>
        <router-link to="/notes" class="nav__link" :class="{ active: $route.path.startsWith('/notes') }">软协笔记</router-link>
        <router-link to="/message-board" class="nav__link" :class="{ active: $route.path === '/message-board' }">留言板</router-link>
      </div>

      <div class="nav__user" @click="userMenuOpen = !userMenuOpen" v-click-outside="() => userMenuOpen = false">
        <img :src="userStore.user?.avatar || '/default-avatar.png'" class="nav__avatar" />
        <span class="nav__username">{{ userStore.user?.nickname || '用户' }}</span>
        <span style="font-size:0.7rem; color: rgba(255,255,255,0.7);">▼</span>
        <div class="nav__dropdown-menu" :style="{
          opacity: userMenuOpen ? 1 : 0, pointerEvents: userMenuOpen ? 'auto' : 'none',
          top: 'calc(100% + 8px)', right: 0, left: 'auto', transform: 'none'
        }">
          <router-link to="/profile" class="nav__dropdown-item" @click="userMenuOpen = false">个人信息</router-link>
          <span class="nav__dropdown-item" @click="handleLogout" style="cursor:pointer; color:var(--error);">退出登录</span>
        </div>
      </div>
    </nav>

    <!-- Mobile Drawer -->
    <teleport to="body">
      <template v-if="drawerOpen">
        <div class="mobile-drawer-overlay" @click="drawerOpen = false"></div>
        <div class="mobile-drawer">
          <div class="mobile-drawer__header">
            <img src="/logo.png" alt="Logo" />
            <span>软件协会</span>
          </div>
          <div class="mobile-drawer__nav">
            <router-link to="/home" class="mobile-drawer__link" :class="{ active: $route.path === '/home' }" @click="drawerOpen = false">🏠 首页</router-link>
            <router-link v-if="!isGuest" to="/study" class="mobile-drawer__link" :class="{ active: $route.path.startsWith('/study') }" @click="drawerOpen = false">📚 学习小组</router-link>
            <router-link to="/activities" class="mobile-drawer__link" :class="{ active: $route.path === '/activities' }" @click="drawerOpen = false">🎉 软协活动</router-link>
            <router-link to="/notes" class="mobile-drawer__link" :class="{ active: $route.path.startsWith('/notes') }" @click="drawerOpen = false">📝 软协笔记</router-link>
            <router-link to="/message-board" class="mobile-drawer__link" :class="{ active: $route.path === '/message-board' }" @click="drawerOpen = false">💬 留言板</router-link>
          </div>
          <div class="mobile-drawer__user">
            <img :src="userStore.user?.avatar || '/default-avatar.png'" />
            <div class="mobile-drawer__user-info">
              <div class="mobile-drawer__user-name">{{ userStore.user?.nickname || '用户' }}</div>
            </div>
          </div>
          <div class="mobile-drawer__actions">
            <router-link to="/profile" class="mobile-drawer__action" @click="drawerOpen = false">👤 个人信息</router-link>
            <span class="mobile-drawer__action mobile-drawer__action--danger" @click="() => { handleLogout(); drawerOpen = false }" style="cursor:pointer;">🚪 退出登录</span>
          </div>
        </div>
      </template>
    </teleport>

    <main class="main-content"><router-view /></main>

    <footer class="footer">
      <div class="footer__text">
        <div class="footer__copyright">© 2018–{{ currentYear }} {{ footerSettings.footer_copyright || '中南林业科技大学软件协会' }}. All rights reserved.</div>
        <div v-if="footerSettings.footer_address">地址：{{ footerSettings.footer_address }}</div>
        <div v-if="footerSettings.footer_qq">官方 QQ：{{ footerSettings.footer_qq }}</div>
        <div v-if="footerSettings.footer_email">联系我们：<a :href="'mailto:' + footerSettings.footer_email">{{ footerSettings.footer_email }}</a></div>
      </div>
      <div class="qr-float" v-if="hasAnyQr">
        <div class="qr-float__item" v-if="footerSettings.qr_qq_group_url">
          <div class="qr-float__thumb">
            <img :src="footerSettings.qr_qq_group_url" alt="QQ群二维码" />
            <div class="qr-float__popup">
              <img :src="footerSettings.qr_qq_group_url" alt="QQ群二维码" />
              <span v-if="footerSettings.qr_qq_group_name">{{ footerSettings.qr_qq_group_name }}</span>
            </div>
          </div>
          <span class="qr-float__label" v-if="footerSettings.qr_qq_group_name">{{ footerSettings.qr_qq_group_name }}</span>
        </div>
        <div class="qr-float__item" v-if="footerSettings.qr_qq_account_url">
          <div class="qr-float__thumb">
            <img :src="footerSettings.qr_qq_account_url" alt="QQ号二维码" />
            <div class="qr-float__popup">
              <img :src="footerSettings.qr_qq_account_url" alt="QQ号二维码" />
              <span v-if="footerSettings.qr_qq_account_name">{{ footerSettings.qr_qq_account_name }}</span>
            </div>
          </div>
          <span class="qr-float__label" v-if="footerSettings.qr_qq_account_name">{{ footerSettings.qr_qq_account_name }}</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import axios from 'axios'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const userMenuOpen = ref(false)
const drawerOpen = ref(false)
const currentYear = ref(new Date().getFullYear())
const navHidden = ref(false)
const isGuest = computed(() => {
  const roles = userStore.roles || []
  return roles.length === 0 || (roles.includes(4) && !roles.some(r => r <= 3))
})

// 页脚设置
const footerSettings = reactive({
  footer_address: '', footer_qq: '', footer_email: '', footer_copyright: '',
  qr_qq_group_url: '', qr_qq_group_name: '',
  qr_qq_account_url: '', qr_qq_account_name: ''
})
const hasAnyQr = computed(() => !!(footerSettings.qr_qq_group_url || footerSettings.qr_qq_account_url))

async function loadFooterSettings() {
  try {
    const res = await axios.get('/api/setting/public')
    const d = res.data?.data || {}
    Object.keys(footerSettings).forEach(k => { if (d[k] !== undefined) footerSettings[k] = d[k] })
  } catch (e) {}
}

onMounted(async () => {
  try { await userStore.fetchUserInfo() } catch { router.push('/login') }
  loadFooterSettings()
})

function handleLogout() { userStore.logout(); userMenuOpen.value = false; router.push('/login') }

// Hero区域导航隐藏逻辑：仅在首页且在hero区域内隐藏
function checkNavVisibility() {
  if (route.path !== '/home') { navHidden.value = false; return }
  const heroEl = document.querySelector('.home-hero')
  if (!heroEl) { navHidden.value = false; return }
  const heroBottom = heroEl.getBoundingClientRect().bottom
  const navHeight = window.innerWidth <= 768 ? 56 : 72
  navHidden.value = heroBottom > navHeight // 导航栏高度，hero底部还在视口内就隐藏
}

let scrollHandler = null
onMounted(() => {
  scrollHandler = () => checkNavVisibility()
  window.addEventListener('scroll', scrollHandler, { passive: true })
  checkNavVisibility()
})
onUnmounted(() => { if (scrollHandler) window.removeEventListener('scroll', scrollHandler) })

watch(() => route.path, () => { setTimeout(checkNavVisibility, 50) })

const vClickOutside = {
  mounted(el, binding) { el.__h = (e) => { if (!el.contains(e.target)) binding.value() }; document.addEventListener('click', el.__h) },
  unmounted(el) { document.removeEventListener('click', el.__h) }
}
</script>

<style scoped>
.nav--hidden {
  transform: translateY(-100%);
  opacity: 0;
  pointer-events: none;
}
.nav {
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.main-content {
  flex: 1;
  padding-top: 72px;
}
@media (max-width: 768px) {
  .main-content { padding-top: 56px; }
}
.nav--gradient {
  background: linear-gradient(135deg, #1a9a8a 0%, #2e8bc5 40%, #4a76b5 65%, #6366b0 85%, #7c5ba8 100%) !important;
  box-shadow: 0 2px 16px rgba(26,154,138,0.18) !important;
}
.nav--gradient .nav__name { color: #fff !important; }
.nav--gradient .nav__username { color: rgba(255,255,255,0.9) !important; }
.nav--gradient .nav__avatar { border-color: rgba(255,255,255,0.3) !important; }
.nav__logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: cover;
}

/* 页脚布局 */
.footer {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.footer__text {
  text-align: center;
}
/* 二维码固定在页脚五分之三处 */
.qr-float {
  position: absolute;
  left: 75%;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: row;
  gap: 72px;
}
.qr-float__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.qr-float__thumb {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 14px;
  overflow: visible;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  background: #fff;
  transition: transform 0.2s;
}
.qr-float__thumb:hover {
  transform: scale(1.05);
}
.qr-float__thumb > img {
  width: 120px;
  height: 120px;
  border-radius: 14px;
  object-fit: cover;
}
.qr-float__popup {
  position: absolute;
  bottom: 0;
  right: calc(100% + 14px);
  width: 220px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.18);
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  opacity: 0;
  pointer-events: none;
  transform: translateX(8px);
  transition: opacity 0.25s, transform 0.25s;
  z-index: 1000;
}
.qr-float__thumb:hover .qr-float__popup {
  opacity: 1;
  pointer-events: auto;
  transform: translateX(0);
}
.qr-float__popup img {
  width: 196px;
  height: 196px;
  object-fit: contain;
  border-radius: 8px;
}
.qr-float__popup span {
  font-size: 12px;
  color: #333;
  font-weight: 600;
}
.qr-float__label {
  font-size: 10px;
  color: #666;
  max-width: 60px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .nav__logo {
    width: 28px;
    height: 28px;
  }
  .footer {
    flex-direction: column;
    gap: var(--s4);
  }
  .qr-float {
    display: none;
  }
}
</style>
