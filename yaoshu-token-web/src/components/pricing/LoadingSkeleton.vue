<script setup lang="ts">
import { VIEW_MODES } from '@/api/pricing/constants'

defineProps<{
  viewMode?: string
}>()
</script>

<template>
  <div class="loading-skeleton">
    <div class="loading-skeleton__header">
      <el-skeleton-item
        variant="text"
        style="width: 200px; height: 32px;"
      />
      <el-skeleton-item
        variant="text"
        style="width: 300px; height: 16px;"
      />
    </div>
    <div class="loading-skeleton__toolbar">
      <el-skeleton-item
        variant="rect"
        style="width: 120px; height: 32px;"
      />
      <el-skeleton-item
        variant="rect"
        style="width: 120px; height: 32px;"
      />
      <el-skeleton-item
        variant="rect"
        style="width: 120px; height: 32px;"
      />
    </div>
    <template v-if="viewMode === VIEW_MODES.CARD">
      <div class="loading-skeleton__grid">
        <el-skeleton-item
          v-for="i in 8"
          :key="i"
          variant="rect"
          style="height: 140px;"
        />
      </div>
    </template>
    <template v-else>
      <div class="loading-skeleton__table">
        <el-skeleton-item
          v-for="i in 10"
          :key="i"
          variant="rect"
          style="height: 40px; margin-bottom: 4px;"
        />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.loading-skeleton {
  &__header {
    margin-bottom: 24px;
    text-align: center;

    :first-child {
      margin: 0 auto var(--ys-spacing-2);
    }
  }

  &__toolbar {
    display: flex;
    gap: var(--ys-spacing-3);
    margin-bottom: 16px;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: var(--ys-spacing-3);
  }

  &__table {
    width: 100%;
  }
}
</style>
