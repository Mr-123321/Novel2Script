import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: 'Novel2Script — 落墨成戏' }
  },
  {
    path: '/scripts',
    name: 'scripts',
    component: () => import('@/views/ScriptListView.vue'),
    meta: { title: '剧本列表' }
  },
  {
    path: '/scripts/new',
    name: 'config',
    component: () => import('@/views/ConfigView.vue'),
    meta: { title: '生成配置' }
  },
  {
    path: '/scripts/:id',
    name: 'editor',
    component: () => import('@/views/ScriptEditorView.vue'),
    meta: { title: '剧本编辑' }
  },
  {
    path: '/scripts/:id/characters',
    name: 'characters',
    component: () => import('@/views/CharacterManagerView.vue'),
    meta: { title: '人物管理' }
  },
  {
    path: '/scripts/:id/yaml',
    name: 'yaml',
    component: () => import('@/views/YamlPreviewView.vue'),
    meta: { title: 'YAML 预览' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.afterEach((to) => {
  document.title = (to.meta.title as string) || 'Novel2Script'
})

export default router
