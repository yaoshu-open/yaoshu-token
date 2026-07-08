<script setup lang="ts">
/**
 * Step 3: 地区选择（多选 + 可用副本过滤）。
 * 地区列表由 Wizard 容器加载后透传。
 * getAvailableByLocation 由 useAvailableReplicas composable 提供。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DeploymentFormState } from '@/composables/deployment/useDeploymentForm'
import type { DeploymentLocation } from '@/api/deployment/types'

interface Props {
  form: DeploymentFormState
  locations: DeploymentLocation[]
  getAvailableByLocation: (locationId: number) => number
  loading?: boolean
}

const props = defineProps<Props>()
const { t } = useI18n()

/** 仅显示有可用副本的地区 */
const filteredLocations = computed(() =>
  props.locations.filter((loc) => {
    const id = Number(loc.id)
    return !Number.isNaN(id) && props.getAvailableByLocation(id) > 0
  })
)
</script>

<template>
  <el-form
    label-position="top"
    class="step-location"
  >
    <el-form-item :label="t('deployment.create.location.title')">
      <div class="location-hint">
        {{ t('deployment.create.location.hint') }}
      </div>
      <div
        v-if="props.loading"
        class="location-loading"
      >
        <i class="i-ep-loading" />
        {{ t('common.loading') }}
      </div>
      <el-checkbox-group
        v-else
        v-model="props.form.location_ids"
        class="location-group"
      >
        <el-checkbox
          v-for="loc in filteredLocations"
          :key="loc.id"
          :value="Number(loc.id)"
          class="location-item"
        >
          <span class="location-name">{{ loc.name }}</span>
          <el-tag
            size="small"
            type="success"
            class="location-available"
          >
            {{ t('deployment.create.location.available', { count: props.getAvailableByLocation(Number(loc.id)) }) }}
          </el-tag>
        </el-checkbox>
      </el-checkbox-group>
      <el-empty
        v-if="!props.loading && filteredLocations.length === 0"
        :description="t('deployment.create.location.empty')"
      />
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.step-location {
  .location-hint {
    margin-bottom: 12px;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  .location-loading {
    color: var(--el-text-color-secondary);
  }

  .location-group {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  .location-item {
    display: flex;
    align-items: center;
    height: auto;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-sm);
  }

  .location-name {
    margin-right: 8px;
  }

  .location-available {
    margin-left: auto;
  }
}
</style>
