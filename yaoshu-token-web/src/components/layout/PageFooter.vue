<script setup lang="ts">
import { computed, provide, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSystemConfigStore } from '@/store/modules/system-config'
import type { FooterColumn } from './types'
import { getExtraFooterColumns } from '@/plugins/spi/registry'

interface Props {
  copyright?: string
  columns?: FooterColumn[]
}

const props = withDefaults(defineProps<Props>(), {
  copyright: undefined,
  columns: () => []
})

const { t } = useI18n()
const systemConfig = useSystemConfigStore()
const currentYear = new Date().getFullYear()

// 版权文案优先级：调用方显式传入 > 后端 footerHtml 配置 > 默认 i18n（站点名 + 年份）
// footerHtml 由管理员在"系统设置→站点设置"配置，为可信内容，可用 v-html 渲染
const useFooterHtml = computed(() => !props.copyright && !!systemConfig.footerHtml)
const copyrightText = computed(
  () =>
    props.copyright ??
    t('layout.footer.copyright', {
      year: currentYear,
      name: systemConfig.systemName
    })
)

// 合并 SPI 注入的 footer 列
const allColumns = computed<FooterColumn[]>(() => [
  ...props.columns,
  ...getExtraFooterColumns()
])
const portalEl = ref<HTMLElement | null>(null)
provide('pageFooterPortal', portalEl)
</script>

<template>
  <footer class="page-footer">
    <div
      v-if="allColumns.length"
      class="page-footer__columns"
    >
      <div
        v-for="(col, idx) in allColumns"
        :key="idx"
        class="page-footer__column"
      >
        <h4 class="page-footer__col-title">
          {{ col.title }}
        </h4>
        <ul class="page-footer__col-links">
          <li
            v-for="(link, lIdx) in col.links"
            :key="lIdx"
          >
            <router-link
              v-if="!link.external"
              :to="link.href"
              class="page-footer__link"
            >{{ link.label }}</router-link>
            <a
              v-else
              :href="link.href"
              target="_blank"
              rel="noopener noreferrer"
              class="page-footer__link"
            >{{ link.label }}</a>
          </li>
        </ul>
      </div>
    </div>

    <!-- 业务页 Portal 注入点 -->
    <div
      ref="portalEl"
      class="page-footer__portal"
    />

    <div class="page-footer__bottom">
      <span v-if="useFooterHtml" v-html="systemConfig.footerHtml" />
      <span v-else>{{ copyrightText }}</span>
    </div>
  </footer>
</template>

<style scoped lang="scss">
.page-footer {
  padding: var(--ys-spacing-4) var(--ys-spacing-6);
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);

  &__columns {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-8);
    margin-bottom: 16px;
  }

  &__column {
    min-width: 120px;
  }

  &__col-title {
    margin: 0 0 var(--ys-spacing-2);
    font-size: var(--el-font-size-base);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__col-links {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__link {
    display: inline-block;
    padding: 2px 0;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
    text-decoration: none;
    transition: color 0.2s;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  &__portal {
    min-height: 0;

    &:empty {
      display: none;
    }
  }

  &__bottom {
    padding-top: 8px;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}
</style>
