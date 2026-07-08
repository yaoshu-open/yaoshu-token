<script setup lang="ts">
/**
 * 部署详情 - 部署位置 Card。
 */
import { useI18n } from 'vue-i18n'
import type { DeploymentLocation } from '@/api/deployment/types'

defineProps<{ locations: DeploymentLocation[] }>()

const { t } = useI18n()
</script>

<template>
  <ElCard
    shadow="never"
    class="deployment-card"
  >
    <template #header>
      <div class="deployment-card__header">
        <i class="i-ep-location" />
        <span>{{ t('deployment.details.location') }}</span>
      </div>
    </template>
    <div class="deployment-card__tags">
      <el-tag
        v-for="loc in locations"
        :key="loc.id"
        type="warning"
        size="default"
        class="deployment-card__tag"
      >
        <i class="i-ep-location" />
        {{ loc.name }} ({{ loc.iso2 }})
      </el-tag>
    </div>
  </ElCard>
</template>

<style scoped lang="scss">
.deployment-card {
  border: 1px solid var(--el-border-color-lighter);

  &__header {
    display: flex;
    gap: 6px;
    align-items: center;
    font-weight: 500;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__tag {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }
}
</style>
