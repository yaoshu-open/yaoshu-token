<script setup lang="ts">
// 鉴权态公共布局：由 M0 RouterView 占位升级为 AuthenticatedLayout 骨架
// M1-A 完整接入：Sidebar + Header + Main + Footer + theme store 驱动
import AuthenticatedLayout from '@/components/layout/AuthenticatedLayout.vue'
</script>

<template>
  <AuthenticatedLayout>
    <RouterView v-slot="{ Component, route }">
      <template v-if="route.meta.keepAlive">
        <keep-alive>
          <component :is="Component" :key="route.path" />
        </keep-alive>
      </template>
      <component v-else :is="Component" :key="route.path" />
    </RouterView>
  </AuthenticatedLayout>
</template>
