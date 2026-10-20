import { createRouter, createWebHistory } from 'vue-router'
const routes = [
  {path:'/login',component:()=>import('@/views/Auth.vue'),meta:{public:true}},
  {path:'/register',component:()=>import('@/views/Auth.vue'),meta:{public:true}},
  {path:'/',component:()=>import('@/views/Layout.vue'),redirect:'/oj',children:[
    {path:'oj',name:'OjProblems',component:()=>import('@/views/oj/ProblemList.vue')},
    {path:'oj/learning',name:'OjLearning',component:()=>import('@/views/oj/LearningHub.vue')},
    {path:'oj/sets/:setId',name:'OjProblemSet',component:()=>import('@/views/oj/SetDetail.vue')},
    {path:'oj/sets/:setId/items/:itemId',name:'OjSetWorkspace',component:()=>import('@/views/oj/SetWorkspace.vue')},
    {path:'oj/:id',name:'OjWorkspace',component:()=>import('@/views/oj/OjWorkspace.vue')},
    {path:'profile',component:()=>import('@/views/Profile.vue')}
  ]},
  {path:'/:pathMatch(.*)*',redirect:'/oj'}
]
const router=createRouter({history:createWebHistory(),routes,scrollBehavior(to,from,saved){if(saved)return saved;if(to.path===from.path&&to.path.startsWith('/oj')&&!to.hash)return false;return to.hash?{el:to.hash,top:76}:{top:0}}})
router.beforeEach(to=>{document.title='SAP OJ';if(!to.meta.public&&!localStorage.getItem('sap_token'))return{path:'/login',query:{redirect:to.fullPath}}})
export default router
