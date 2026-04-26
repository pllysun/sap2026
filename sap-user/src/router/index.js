import { createRouter, createWebHistory } from 'vue-router'

const routes = [
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
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('sap_token')
  if (to.path !== '/login' && to.path !== '/register' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
