<script setup lang="ts">
// 鉴权态公共布局：由 M0 RouterView 占位升级为 AuthenticatedLayout 骨架
// M1-A 完整接入：Sidebar + Header + Main + Footer + theme store 驱动
import { computed, ref, watch } from 'vue'
import AuthenticatedLayout from '@/components/layout/AuthenticatedLayout.vue'
import { useAuthStore } from '@/store/modules/auth'

// 用户切换时 keep-alive 子组件 key 变化，触发组件重建，避免旧用户数据残留。
// 关键：退出登录时不触发 key 变化——退出瞬间 token 变空会引发组件重建发请求导致批量 401。
// 退出后路由跳 /sign-in（公开路由，不在本布局下），整个 default.vue 随之卸载，
// 无需依靠 key 变化来清缓存。仅在登录态下记录 userId，登出后保持上一个值不动。
const authStore = useAuthStore()
const stableUserKey = ref<string>('guest')
watch(
  () => [authStore.isLoggedIn, authStore.userInfo?.id] as const,
  ([loggedIn, userId]) => {
    // 仅在已登录且拿到 userId 时更新 key（触发用户切换时的组件重建）
    if (loggedIn && userId !== undefined) {
      stableUserKey.value = String(userId)
    }
    // 未登录时不更新 key——退出登录后 default.vue 会被路由卸载，key 不需要变化
  },
  { immediate: true }
)
const userKey = computed(() => stableUserKey.value)
</script>

<template>
  <AuthenticatedLayout>
    <RouterView v-slot="{ Component, route }">
      <template v-if="route.meta.keepAlive">
        <keep-alive>
          <component :is="Component" :key="`${route.path}:${userKey}`" />
        </keep-alive>
      </template>
      <component v-else :is="Component" :key="route.path" />
    </RouterView>
  </AuthenticatedLayout>
</template>
