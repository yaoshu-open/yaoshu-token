<script setup lang="ts">
import { computed } from 'vue'
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { Locale } from 'vue-i18n'

interface Props {
  size?: 'small' | 'default' | 'large'
}

withDefaults(defineProps<Props>(), {
  size: 'default'
})

const { t, locale, availableLocales } = useI18n()

// localStorage 持久化键（与 plugins/i18n.ts 初始化读取一致）
const STORAGE_KEY = 'yaoshu_locale'

// M1-B（M1-A-Judge-T2 闭环）：原 i-ep-languege 为无效图标名（ep 集无 language 图标）
// 改用 lucide Languages（遵循 U1/U3 铁律：装 @iconify-json/lucide + 验证图标名有效性）
const currentIcon = computed(() => 'i-lucide-languages')

function setLocale(loc: Locale) {
  locale.value = loc
  try {
    localStorage.setItem(STORAGE_KEY, loc)
  } catch {
    /* 隐私模式：忽略持久化失败 */
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = loc
  }
}
</script>

<template>
  <ElDropdown trigger="click">
    <button
      type="button"
      class="language-switcher"
      :class="`language-switcher--${size}`"
      :aria-label="t('layout.header.language')"
      :title="t('layout.header.language')"
    >
      <i :class="currentIcon" />
    </button>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem
          v-for="loc in availableLocales"
          :key="loc"
          :class="{ 'is-active': locale === loc }"
          @click="setLocale(loc)"
        >
          {{ t(`language.${loc}`) }}
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>

<style scoped lang="scss">
.language-switcher {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--el-border-radius-base);
  transition: background-color 0.2s, color 0.2s;

  &:hover {
    color: var(--el-text-color-primary);
    background: var(--el-fill-color-light);
  }

  &--small {
    width: 28px;
    height: 28px;
    font-size: var(--ys-font-size-lg);
  }

  &--default {
    width: 32px;
    height: 32px;
    font-size: 18px;
  }

  &--large {
    width: 36px;
    height: 36px;
    font-size: var(--ys-font-size-xl);
  }
}
</style>
