/**
 * 前端路由配置。
 *
 * @author Focus
 * @date 2026-06-03
 */
import { createRouter, createWebHistory } from 'vue-router'
import { getHomeRoute, isMobile } from '@/utils/device'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', noAuth: true }
  },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'materials',
        name: 'Materials',
        component: () => import('@/views/Materials.vue'),
        meta: { title: '物料与基础数据' }
      },
      {
        path: 'inbound-outbound',
        name: 'InboundOutbound',
        component: () => import('@/views/InboundOutbound.vue'),
        meta: { title: '入库与出库管理' }
      },
      {
        path: 'stock-report',
        name: 'StockReport',
        component: () => import('@/views/StockReport.vue'),
        meta: { title: '库存报表与风险预警' }
      },
      {
        path: 'ai-report',
        name: 'AiReport',
        component: () => import('@/views/AiReport.vue'),
        meta: { title: 'AI 风险预测与智能报告' }
      },
      {
        path: 'inventory-trace',
        name: 'InventoryTrace',
        component: () => import('@/views/InventoryTrace.vue'),
        meta: { title: '库存与看板监控' }
      },
      {
        path: 'inbound-history',
        name: 'InboundHistory',
        component: () => import('@/views/InboundHistory.vue'),
        meta: { title: '入库历史' }
      },
      {
        path: 'scan',
        name: 'Scan',
        component: () => import('@/views/Scan.vue'),
        meta: { title: '扫码操作' }
      },
      {
        path: 'freeze',
        name: 'FreezeManagement',
        component: () => import('@/views/FreezeManagement.vue'),
        meta: { title: '封存管理' }
      }
    ]
  },
  {
    // PDA 手持端
    path: '/pda',
    component: () => import('@/components/MobileLayout.vue'),
    meta: { noAuth: false },
    children: [
      {
        path: '',
        name: 'PdaHome',
        component: () => import('@/views/MobileHome.vue'),
        meta: { title: 'PDA 扫码作业' }
      },
      {
        path: 'scan/:mode',
        name: 'PdaScanner',
        component: () => import('@/views/MobileScanner.vue'),
        meta: { title: 'PDA 扫码' },
        props: true
      }
    ]
  },
  {
    // 未匹配路由重定向
    path: '/:pathMatch(.*)*',
    redirect: () => getHomeRoute()
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局路由守卫：鉴权 + 移动端/桌面端路由分流。
 */
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')

  // 1. 未登录 → 强制跳转登录页
  if (!to.meta.noAuth && !token) {
    next('/login')
    return
  }

  // 2. 已登录访问登录/注册页 → 按设备类型跳转首页
  if ((to.path === '/login' || to.path === '/register') && token) {
    next(getHomeRoute())
    return
  }

  // 3. PDA/移动端访问桌面路由 → 重定向到 PDA 首页
  if (token && isMobile()
      && to.path !== '/pda' && !to.path.startsWith('/pda/')
      && to.path !== '/login' && to.path !== '/register') {
    next('/pda')
    return
  }

  next()
})

export default router
