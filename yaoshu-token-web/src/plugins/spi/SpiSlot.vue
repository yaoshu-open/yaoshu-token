<script setup lang="ts">
import { resolveComponent } from './registry'
import type { Component } from 'vue'

const props = defineProps<{
  /** 插槽名称，对应注册表中的 slot key */
  name: string
  /** 默认组件（扩展未覆盖时渲染） */
  fallback?: Component
}>()

// 注册表在应用启动时同步完成注册，渲染时解析即可
const resolved = resolveComponent(props.name)
</script>

<template>
  <component
    :is="resolved ?? fallback"
    v-bind="$attrs"
  />
</template>
