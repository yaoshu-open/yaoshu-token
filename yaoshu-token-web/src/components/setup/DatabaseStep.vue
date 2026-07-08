<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ElButton, ElCard, ElIcon, ElTag } from 'element-plus'
import { CircleCheckFilled } from '@element-plus/icons-vue'

defineProps<{ databaseType: string }>()
const emit = defineEmits<{ next: [] }>()
const { t } = useI18n()
</script>

<template>
  <div class="setup-step">
    <el-card>
      <div class="setup-db">
        <el-icon class="setup-db__icon">
          <CircleCheckFilled />
        </el-icon>
        <h3 class="setup-db__title">
          {{ t('setup.database.connected') }}
        </h3>
        <p class="setup-db__row">
          {{ t('setup.database.type') }}:
          <el-tag>{{ databaseType }}</el-tag>
        </p>
        <p class="setup-db__hint">
          {{ t('setup.database.hint') }}
        </p>
      </div>
    </el-card>
    <div class="setup-step__actions">
      <el-button
        type="primary"
        @click="emit('next')"
      >
        {{ t('common.next') }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.setup-step__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

.setup-db {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  align-items: center;
  padding: var(--ys-spacing-6) 0;
  text-align: center;
}

.setup-db__icon {
  font-size: 48px;
  color: var(--el-color-success);
}

.setup-db__title {
  margin: 0;
  font-size: 18px;
}

.setup-db__row {
  margin: 0;
  font-size: var(--ys-font-size-base);
}

.setup-db__hint {
  margin: 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
