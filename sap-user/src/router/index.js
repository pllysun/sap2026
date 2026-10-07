import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/schedule-app', name: 'ScheduleAppIntro', component: () => import('@/views/ScheduleAppIntro.vue') },
  { path: '/forgot-password', name: 'ForgotPassword', component: () => import('@/views/ForgotPassword.vue') },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue')
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/home',
    children: [
      { path: 'oj', name: 'OjProblems', component: () => import('@/views/oj/ProblemList.vue') },
      { path: 'oj/sets/:setId', name: 'OjProblemSet', component: () => import('@/views/oj/SetDetail.vue') },
      { path: 'oj/sets/:setId/items/:itemId', name: 'OjSetWorkspace', component: () => import('@/views/oj/SetWorkspace.vue') },
      { path: 'oj/:id', name: 'OjWorkspace', component: () => import('@/views/oj/OjWorkspace.vue') },
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue')
      },
      {
        path: 'study',
        name: 'StudyGroup',
        component: () => import('@/views/StudyGroup.vue')
      },
      {
        path: 'activities',
        name: 'Activities',
        component: () => import('@/views/Activities.vue')
      },
      {
        path: 'notes',
        name: 'NoteList',
        component: () => import('@/views/NoteList.vue')
      },
      {
        path: 'notes/:id',
        name: 'NoteDetail',
        component: () => import('@/views/NoteDetail.vue')
      },
      {
        path: 'message-board',
        name: 'MessageBoard',
        component: () => import('@/views/MessageBoard.vue')
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue')
      },
      {
        path: 'join',
        name: 'Join',
        component: () => import('@/views/JoinPage.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.path === from.path && to.path.startsWith('/oj') && !to.hash) return false
    if (to.hash) return { el: to.hash, top: 84 }
    return { top: 0 }
  },
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('sap_token')
  if (!['/login', '/register', '/forgot-password'].includes(to.path) && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
