<script setup lang="ts">
import { ElButton, ElIcon, ElDivider } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useThemeStore } from '@/store/modules/theme'
import type { ThemeMode, SidebarVariant } from '@/store/modules/theme'
import { THEME_MODES, SIDEBAR_VARIANTS } from '@/components/layout/constants'
import Dialog from './Dialog.vue'

interface Props {
  modelValue: boolean
}

withDefaults(defineProps<Props>(), {
  modelValue: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const { t } = useI18n()
const themeStore = useThemeStore()
const { mode, sidebarVariant, compact } = storeToRefs(themeStore)

function setMode(next: ThemeMode) {
  themeStore.setMode(next)
}
function setSidebarVariant(next: SidebarVariant) {
  themeStore.setSidebarVariant(next)
}
function toggleCompact() {
  themeStore.setCompact(!compact.value)
}
function resetAll() {
  themeStore.reset()
}
function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <Dialog
    :model-value="modelValue"
    :title="t('theme.title')"
    width="440px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="config-drawer">
      <!-- 主题模式 -->
      <section class="config-drawer__section">
        <h4 class="config-drawer__label">
          {{ t('theme.mode.label') }}
        </h4>
        <div class="config-drawer__modes">
          <button
            v-for="m in THEME_MODES"
            :key="m"
            type="button"
            class="config-drawer__mode"
            :class="{ 'is-active': mode === m }"
            @click="setMode(m)"
          >
            <ElIcon>
              <i
                :class="{
                  'i-ep-sunny': m === 'light',
                  'i-ep-moon': m === 'dark',
                  'i-ep-monitor': m === 'system'
                }"
              />
            </ElIcon>
            <span>{{ t(`theme.mode.${m}`) }}</span>
          </button>
        </div>
      </section>

      <ElDivider />

      <!-- 侧边栏样式 -->
      <section class="config-drawer__section">
        <h4 class="config-drawer__label">
          {{ t('theme.sidebarVariant.label') }}
        </h4>
        <div class="config-drawer__variants">
          <button
            v-for="v in SIDEBAR_VARIANTS"
            :key="v"
            type="button"
            class="config-drawer__variant"
            :class="{ 'is-active': sidebarVariant === v }"
            @click="setSidebarVariant(v)"
          >
            {{ t(`theme.sidebarVariant.${v}`) }}
          </button>
        </div>
      </section>

      <ElDivider />

      <!-- 紧凑模式 -->
      <section class="config-drawer__section">
        <div class="config-drawer__row">
          <div>
            <h4 class="config-drawer__label">
              {{ t('theme.compact.label') }}
            </h4>
            <p class="config-drawer__hint">
              {{ t('theme.compact.description') }}
            </p>
          </div>
          <ElButton
            :type="compact ? 'primary' : 'default'"
            size="small"
            @click="toggleCompact"
          >
            {{ compact ? t('common.confirm') : t('common.cancel') }}
          </ElButton>
        </div>
      </section>

      <ElDivider />

      <div class="config-drawer__footer">
        <ElButton
          size="small"
          @click="resetAll"
        >
          {{ t('theme.reset') }}
        </ElButton>
        <ElButton
          type="primary"
          size="small"
          @click="close"
        >
          {{ t('common.confirm') }}
        </ElButton>
      </div>
    </div>
  </Dialog>
</template>

<style scoped lang="scss">
.config-drawer {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);

  &__section {
    padding: var(--ys-spacing-1) 0;
  }

  &__label {
    margin: 0 0 var(--ys-spacing-2);
    font-size: var(--el-font-size-base);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__hint {
    margin: var(--ys-spacing-1) 0 0;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__modes {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--ys-spacing-2);
  }

  &__mode {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
    align-items: center;
    padding: var(--ys-spacing-3) var(--ys-spacing-2);
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: var(--el-fill-color-light);
    border: 1px solid transparent;
    border-radius: var(--el-border-radius-base);
    transition: border-color 0.2s, color 0.2s;

    &:hover {
      border-color: var(--el-color-primary-light-5);
    }

    &.is-active {
      color: var(--el-color-primary);
      border-color: var(--el-color-primary);
    }
  }

  &__variants {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--ys-spacing-2);
  }

  &__variant {
    padding: var(--ys-spacing-2);
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: var(--el-fill-color-light);
    border: 1px solid transparent;
    border-radius: var(--el-border-radius-base);
    transition: border-color 0.2s, color 0.2s;

    &:hover {
      border-color: var(--el-color-primary-light-5);
    }

    &.is-active {
      color: var(--el-color-primary);
      border-color: var(--el-color-primary);
    }
  }

  &__row {
    display: flex;
    gap: var(--ys-spacing-4);
    align-items: center;
    justify-content: space-between;
  }

  &__footer {
    display: flex;
    gap: var(--ys-spacing-2);
    justify-content: flex-end;
  }
}
</style>
