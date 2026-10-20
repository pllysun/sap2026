import {createRouter,createWebHistory} from 'vue-router'
const router=createRouter({history:createWebHistory(import.meta.env.BASE_URL),routes:[
  {path:'/login',component:()=>import('@/views/Login.vue'),meta:{public:true,title:'登录'}},
  {path:'/',component:()=>import('@/layouts/AdminLayout.vue'),redirect:'/oj',children:[
    {path:'oj',component:()=>import('@/views/OjView.vue'),meta:{title:'题库管理'}},
    {path:'users',component:()=>import('@/views/Users.vue'),meta:{title:'账号管理'}},
    {path:'settings',component:()=>import('@/views/Settings.vue'),meta:{title:'系统设置'}},
    {path:'storage',component:()=>import('@/views/Storage.vue'),meta:{title:'题包归档'}},
    {path:'audit',component:()=>import('@/views/Audit.vue'),meta:{title:'操作审计'}}
  ]},{path:'/:pathMatch(.*)*',redirect:'/oj'}
]})
router.beforeEach(to=>{document.title=`${to.meta.title||'管理'} · SAP OJ`;if(!to.meta.public&&!localStorage.getItem('sap-token'))return{path:'/login',query:{redirect:to.fullPath}}})
export default router
