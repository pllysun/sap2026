import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'member',
        name: 'Member',
        component: () => import('../views/MemberView.vue'),
        meta: { title: '成员管理' }
      },
      {
        path: 'activity',
        name: 'Activity',
        component: () => import('../views/ActivityView.vue'),
        meta: { title: '活动管理' }
      },
      {
        path: 'finance',
        name: 'Finance',
        component: () => import('../views/FinanceView.vue'),
        meta: { title: '财务管理' }
      },
      {
        path: 'study',
        name: 'Study',
        component: () => import('../views/StudyView.vue'),
        meta: { title: '学习小组' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/SettingsView.vue'),
        meta: { title: '系统设置' }
      },
      {
        path: 'log',
        name: 'Log',
        component: () => import('../views/LogView.vue'),
        meta: { title: '日志管理' }
      },
      {
        path: 'join',
        name: 'Join',
        component: () => import('../views/JoinView.vue'),
        meta: { title: '入会管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || '管理'} · 软件协会`
  const token = localStorage.getItem('sap-token')
  if (!to.meta.noAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
