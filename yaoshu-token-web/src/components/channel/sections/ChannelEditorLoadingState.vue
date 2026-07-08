<script setup lang="ts">
/**
 * 渠道编辑抽屉加载态骨架屏。
 */
import { ElSkeleton } from 'element-plus'
</script>

<template>
  <div class="channel-editor-loading">
    <el-skeleton
      :rows="3"
      animated
    />
    <el-skeleton
      :rows="4"
      animated
      style="margin-top: 24px"
    />
    <el-skeleton
      :rows="3"
      animated
      style="margin-top: 24px"
    />
  </div>
</template>

<style scoped>
.channel-editor-loading {
  padding: var(--ys-spacing-2) 0;
}
</style>
