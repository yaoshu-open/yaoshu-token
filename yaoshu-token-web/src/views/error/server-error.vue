<script setup lang="ts">
// 500 服务器内部错误页
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElButton } from 'element-plus'

const router = useRouter()
const { t } = useI18n()
</script>

<template>
  <div class="error-page">
    <div class="error-page__code">
      500
    </div>
    <h2 class="error-page__title">
      {{ t('error.serverError.title') }}
    </h2>
    <p class="error-page__desc">
      {{ t('error.serverError.desc') }}
    </p>
    <div class="error-page__actions">
      <ElButton @click="router.back()">
        {{ t('error.back') }}
      </ElButton>
      <ElButton
        type="primary"
        @click="router.push('/')"
      >
        {{ t('error.goHome') }}
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;
}

.error-page__code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  color: var(--el-color-danger);
}

.error-page__title {
  margin: 0;
  font-size: var(--ys-font-size-2xl);
  font-weight: 600;
}

.error-page__desc {
  max-width: 400px;
  margin: 0;
  font-size: 15px;
  color: var(--el-text-color-secondary);
}

.error-page__actions {
  display: flex;
  gap: var(--ys-spacing-3);
  margin-top: var(--ys-spacing-2);
}
</style>
