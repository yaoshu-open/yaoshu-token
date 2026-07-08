<script setup lang="ts">
/**
 * 模型顶部按钮组件。
 *
 * 职责：创建模型 / 更多操作（缺失扫描/同步上游/预填组/供应商管理）。
 */
import { useI18n } from 'vue-i18n'

defineEmits<{
  (e: 'add'): void
  (e: 'missing-models'): void
  (e: 'sync-upstream'): void
  (e: 'prefill-groups'): void
  (e: 'manage-vendors'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="models-primary-buttons">
    <el-button
      type="primary"
      size="small"
      @click="$emit('add')"
    >
      <el-icon><Plus /></el-icon>
      {{ t('model.actions.addModel') }}
    </el-button>

    <el-dropdown trigger="click">
      <el-button size="small">
        <el-icon><MoreFilled /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="$emit('missing-models')">
            <el-icon><WarningFilled /></el-icon>
            {{ t('model.actions.missingModels') }}
          </el-dropdown-item>
          <el-dropdown-item @click="$emit('sync-upstream')">
            <el-icon><Refresh /></el-icon>
            {{ t('model.actions.syncUpstream') }}
          </el-dropdown-item>
          <el-dropdown-item
            divided
            @click="$emit('prefill-groups')"
          >
            <el-icon><List /></el-icon>
            {{ t('model.actions.prefillGroups') }}
          </el-dropdown-item>
          <el-dropdown-item @click="$emit('manage-vendors')">
            <el-icon><OfficeBuilding /></el-icon>
            {{ t('model.actions.manageVendors') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script lang="ts">
import {
  Plus,
  MoreFilled,
  WarningFilled,
  Refresh,
  List,
  OfficeBuilding,
} from '@element-plus/icons-vue'
export default {
  components: { Plus, MoreFilled, WarningFilled, Refresh, List, OfficeBuilding },
}
</script>

<style scoped>
.models-primary-buttons {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}
</style>
