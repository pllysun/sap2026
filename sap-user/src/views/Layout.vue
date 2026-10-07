<template>
  <div style="display: flex; flex-direction: column; min-height: 100vh;">
    <nav class="nav nav--association" aria-label="主导航">
      <!-- Hamburger (mobile only) -->
      <button class="nav__hamburger" aria-label="打开导航菜单" :aria-expanded="drawerOpen" @click="drawerOpen = true">
        <span></span><span></span><span></span>
      </button>

      <router-link to="/home" class="nav__brand">
        <img src="/logo.png" class="nav__logo" alt="Logo" />
        <span class="nav__name">软件协会<small>中南林业科技大学</small></span>
      </router-link>

      <div class="nav__links-wrap">
        <router-link to="/home" class="nav__link" :class="{ active: $route.path === '/home' }">首页</router-link>
        <router-link to="/study" class="nav__link" :class="{ active: $route.path.startsWith('/study') }">学习小组</router-link>
        <router-link to="/activities" class="nav__link" :class="{ active: $route.path === '/activities' }">软协活动</router-link>
        <router-link to="/oj" class="nav__link" :class="{ active: $route.path.startsWith('/oj') }">算法题库</router-link>
        <router-link to="/notes" class="nav__link" :class="{ active: $route.path.startsWith('/notes') }">软协笔记</router-link>
        <router-link to="/message-board" class="nav__link" :class="{ active: $route.path === '/message-board' }">留言板</router-link>
      </div>

      <div class="nav__user" v-click-outside="() => userMenuOpen = false">
        <button class="nav__user-toggle" @click="userMenuOpen = !userMenuOpen" :aria-expanded="userMenuOpen" aria-label="用户菜单" @keydown.esc="userMenuOpen = false">
          <UserAvatar :src="userStore.user?.avatar" :name="userName" />
          <span class="nav__username">{{ userName }}</span><UiIcon class="nav__chevron" name="chevron-down" :size="14" />
        </button>
        <div v-if="userMenuOpen" class="nav__dropdown-menu" :style="{
          opacity: userMenuOpen ? 1 : 0, pointerEvents: userMenuOpen ? 'auto' : 'none',
          top: 'calc(100% + 8px)', right: 0, left: 'auto', transform: 'none'
        }">
          <router-link to="/profile" class="nav__dropdown-item" @click="userMenuOpen = false">个人信息</router-link>
          <button class="nav__dropdown-item" @click="handleLogout" style="width:100%;text-align:left;color:var(--error);">退出登录</button>
        </div>
      </div>
    </nav>

    <!-- Mobile Drawer -->
    <teleport to="body">
      <template v-if="drawerOpen">
        <div class="mobile-drawer-overlay" @click="drawerOpen = false"></div>
        <div ref="drawerDialog" class="mobile-drawer" role="dialog" aria-modal="true" aria-label="导航菜单" tabindex="-1" @keydown.esc="drawerOpen = false">
          <div class="mobile-drawer__header">
            <img src="/logo.png" alt="Logo" />
            <span>软件协会</span>
            <button class="drawer-close" aria-label="关闭导航菜单" @click="drawerOpen = false">×</button>
          </div>
          <div class="mobile-drawer__nav">
            <router-link to="/home" class="mobile-drawer__link" :class="{ active: $route.path === '/home' }" @click="drawerOpen = false">🏠 首页</router-link>
            <router-link to="/study" class="mobile-drawer__link" :class="{ active: $route.path.startsWith('/study') }" @click="drawerOpen = false">📚 学习小组</router-link>
            <router-link to="/activities" class="mobile-drawer__link" :class="{ active: $route.path === '/activities' }" @click="drawerOpen = false">🎉 软协活动</router-link>
            <router-link to="/oj" class="mobile-drawer__link" :class="{ active: $route.path.startsWith('/oj') }" @click="drawerOpen = false">💻 算法题库</router-link>
            <router-link to="/notes" class="mobile-drawer__link" :class="{ active: $route.path.startsWith('/notes') }" @click="drawerOpen = false">📝 软协笔记</router-link>
            <router-link to="/message-board" class="mobile-drawer__link" :class="{ active: $route.path === '/message-board' }" @click="drawerOpen = false">💬 留言板</router-link>
          </div>
          <div class="mobile-drawer__user">
            <UserAvatar :src="userStore.user?.avatar" :name="userName" :size="36" />
            <div class="mobile-drawer__user-info">
              <div class="mobile-drawer__user-name">{{ userName }}</div>
            </div>
          </div>
          <div class="mobile-drawer__actions">
            <router-link to="/profile" class="mobile-drawer__action" @click="drawerOpen = false">👤 个人信息</router-link>
            <button class="mobile-drawer__action mobile-drawer__action--danger" @click="() => { handleLogout(); drawerOpen = false }">🚪 退出登录</button>
          </div>
        </div>
      </template>
    </teleport>

    <main class="main-content"><router-view /></main>

    <footer class="footer">
      <div class="footer__text">
        <p class="footer__motto">万维网连接五大洲，二进制写尽天下事</p>
        <div class="footer__copyright">© 2018–{{ currentYear }} {{ footerSettings.footer_copyright || '中南林业科技大学软件协会' }}. All rights reserved.</div>
        <div v-if="footerSettings.footer_address">地址：{{ footerSettings.footer_address }}</div>
        <div v-if="footerSettings.footer_qq">官方 QQ：{{ footerSettings.footer_qq }}</div>
        <div v-if="footerSettings.footer_email">联系我们：<a :href="'mailto:' + footerSettings.footer_email">{{ footerSettings.footer_email }}</a></div>
      </div>
      <div class="qr-float" v-if="hasAnyQr">
        <div class="qr-float__item" v-if="footerSettings.qr_qq_group_url">
          <div class="qr-float__thumb" role="button" tabindex="0" aria-label="查看 QQ 群二维码" @click="qrPreview = { url: footerSettings.qr_qq_group_url, name: footerSettings.qr_qq_group_name || 'QQ 群' }" @keydown.enter.prevent="$event.currentTarget.click()" @keydown.space.prevent="$event.currentTarget.click()">
            <img :src="footerSettings.qr_qq_group_url" alt="QQ群二维码" />
            <div class="qr-float__popup">
              <img :src="footerSettings.qr_qq_group_url" alt="QQ群二维码" />
              <span v-if="footerSettings.qr_qq_group_name">{{ footerSettings.qr_qq_group_name }}</span>
            </div>
          </div>
          <span class="qr-float__label" v-if="footerSettings.qr_qq_group_name">{{ footerSettings.qr_qq_group_name }}</span>
        </div>
        <div class="qr-float__item" v-if="footerSettings.qr_qq_account_url">
          <div class="qr-float__thumb" role="button" tabindex="0" aria-label="查看官方 QQ 二维码" @click="qrPreview = { url: footerSettings.qr_qq_account_url, name: footerSettings.qr_qq_account_name || '官方 QQ' }" @keydown.enter.prevent="$event.currentTarget.click()" @keydown.space.prevent="$event.currentTarget.click()">
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
    <Teleport to="body"><div v-if="qrPreview" class="contact-overlay" @click.self="qrPreview = null"><div ref="contactDialog" class="contact-dialog" role="dialog" aria-modal="true" aria-labelledby="contact-title" tabindex="-1"><button class="contact-dialog__close" aria-label="关闭二维码预览" @click="qrPreview = null">×</button><h2 id="contact-title">{{ qrPreview.name }}</h2><img :src="qrPreview.url" :alt="qrPreview.name + '二维码'" /><p>使用 QQ 扫码，或保存图片后识别</p></div></div></Teleport>
  </div>
</template>

<script setup>
import UiIcon from '@/components/UiIcon.vue'
import { ref, computed, onMounted, watch, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import UserAvatar from '@/components/UserAvatar.vue'
import { useDialog } from '@/utils/useDialog'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const userMenuOpen = ref(false)
const drawerOpen = ref(false)
const drawerDialog = ref(null), contactDialog = ref(null), qrPreview = ref(null)
useDialog(drawerOpen, drawerDialog, () => { drawerOpen.value = false })
useDialog(computed(() => !!qrPreview.value), contactDialog, () => { qrPreview.value = null })
const currentYear = ref(new Date().getFullYear())
const userName = computed(() => userStore.user?.nickname?.trim() || userStore.user?.name?.trim() || '软协同学')

// 页脚设置
const footerSettings = reactive({
  footer_address: '', footer_qq: '', footer_email: '', footer_copyright: '',
  qr_qq_group_url: '', qr_qq_group_name: '',
  qr_qq_account_url: '', qr_qq_account_name: ''
})
const hasAnyQr = computed(() => !!(footerSettings.qr_qq_group_url || footerSettings.qr_qq_account_url))

async function loadFooterSettings() {
  try {
    const res = await request.get('/api/setting/public')
    const d = res.data || {}
    Object.keys(footerSettings).forEach(k => { if (d[k] !== undefined) footerSettings[k] = d[k] })
  } catch (e) {}
}

onMounted(async () => {
  try { await userStore.fetchUserInfo() } catch { router.push('/login') }
  loadFooterSettings()
})

function handleLogout() { userStore.logout(); userMenuOpen.value = false; router.push('/login') }

watch(() => route.path, () => { drawerOpen.value = false; userMenuOpen.value = false })

const vClickOutside = {
  mounted(el, binding) { el.__h = (e) => { if (!el.contains(e.target)) binding.value() }; document.addEventListener('click', el.__h) },
  unmounted(el) { document.removeEventListener('click', el.__h) }
}
</script>

<style scoped>
.nav--association{background:#f8fafbed;backdrop-filter:blur(18px);box-shadow:none;border-bottom:1px solid #dde3e9;padding-inline:clamp(20px,5vw,180px);gap:24px}
.nav--association .nav__name{color:#222f3c;font-size:17px;letter-spacing:2px;line-height:1.3}.nav__name small{display:block;font-size:9px;letter-spacing:1.5px;color:#5d7791;font-weight:400;margin-top:4px}
.nav--association .nav__links-wrap{position:static;transform:none;background:none;gap:5px;padding:0;margin-left:auto;margin-right:auto}.nav--association .nav__link{font-size:13px;color:#536b83;padding:9px 17px;border-radius:6px}.nav--association .nav__link:hover{color:#24384b;background:#e8edf2}.nav--association .nav__link.active{color:#24384b;background:#dfe7ee;box-shadow:none}.nav--association .nav__username{color:#394959;font-size:12px;max-width:100px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.nav__user-toggle{display:flex;align-items:center;gap:9px}.nav__chevron{color:#577089;font-size:14px}.nav--association .nav__user:hover{background:#e8edf2}.nav--association .nav__hamburger span{background:#354453}.nav--association :is(a,button):focus-visible{outline:2px solid #39638d;outline-offset:3px}.drawer-close{margin-left:auto;font-size:27px;padding:0 7px;color:#51687f}
.main-content {
  flex: 1;
  padding-top: 72px;
}
@media (max-width: 768px) {
  .main-content { padding-top: 56px; }
}
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
  gap:60px;
  margin-top:0;
  background:#eef7ff;
  border-top:1px solid var(--border-blue);
  color:var(--ink-500);
  padding:45px 30px;
}
.footer__motto{font-size:16px;letter-spacing:2px;color:var(--ink-700);margin-bottom:17px}
.footer .footer__copyright{color:var(--ink-500)}
.footer a{color:var(--primary)}
.footer__text {
  text-align: center;
}
/* 二维码固定在页脚五分之三处 */
.qr-float {
  position: relative;
  display: flex;
  flex-direction: row;
  gap: 20px;
}
.qr-float__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.qr-float__thumb {
  position: relative;
  width: 82px;
  height: 82px;
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
  width: 82px;
  height: 82px;
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
  color: var(--ink-500);
  max-width: 100px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .nav--association{padding-inline:12px;gap:10px}.nav--association .nav__links-wrap{display:none}.nav--association .nav__name{font-size:14px}.nav__name small{font-size:8px}.nav--association .nav__brand{margin-right:auto;gap:8px}.nav--association .nav__user{padding-inline:3px}.nav--association .nav__username{display:none}.footer__motto{font-size:12px;letter-spacing:1px}.footer__copyright{font-size:10px}
  .nav__logo {
    width: 28px;
    height: 28px;
  }
  .footer {
    flex-direction: column;
    gap: var(--s4);
  }
  .qr-float {
    display: flex; justify-content: center; flex-wrap: wrap;
  }
}
.footer__text { min-width: 0; overflow-wrap: anywhere; }.qr-float__thumb { cursor: pointer; }.mobile-drawer { max-width: calc(100vw - 32px); overflow-y: auto; overscroll-behavior: contain; padding-bottom: env(safe-area-inset-bottom); }.mobile-drawer__action { text-align: left; }
.contact-overlay { position: fixed; inset: 0; z-index: 1500; display: grid; place-items: center; padding: 20px; background: #17345066; backdrop-filter: blur(5px); }.contact-dialog { width: min(380px, 100%); padding: 30px; position: relative; background: #fff; border: 1px solid var(--border-blue); border-radius: 20px; text-align: center; max-height: calc(100dvh - 40px); overflow: auto; }.contact-dialog h2 { font-size: 20px; padding: 10px 15px 20px; overflow-wrap: anywhere; }.contact-dialog img { width: 100%; height: auto; object-fit: contain; }.contact-dialog p { font-size: 12px; color: var(--ink-500); margin-top: 17px; }.contact-dialog__close { position: absolute; top: 9px; right: 11px; width: 34px; height: 34px; font-size: 23px; color: var(--ink-500); }
@media (hover: none) { .qr-float__popup { display: none; } }
</style>
