<script setup lang="ts">
/**
 * 通用文档渲染组件。
 *
 * 三态渲染：URL（链接卡片）/ HTML（DOMPurify 清洗）/ Markdown（renderMarkdown）。
 * localStorage 缓存策略：先渲染缓存，后台拉取最新，失败保留缓存。
 *
 * 安全红线：HTML 与 Markdown 渲染前必须经 DOMPurify 清洗。
 */
import { computed, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { request } from '@/utils/request'
import { renderMarkdown } from '@/utils/markdown'

interface Props {
  apiEndpoint: string
  title: string
  cacheKey: string
  emptyMessage?: string
}

const props = withDefaults(defineProps<Props>(), {
  emptyMessage: ''
})

const { t } = useI18n()

const content = ref('')
const loading = ref(true)
const error = ref<Error | null>(null)

const isUrl = (text: string): boolean => {
  try {
    new URL(text.trim())
    return true
  } catch {
    return false
  }
}

const isHtml = (text: string): boolean => {
  if (!text) return false
  return /<\/?[a-z][\s\S]*>/i.test(text)
}

const contentType = computed<'url' | 'html' | 'markdown' | 'empty'>(() => {
  const text = content.value.trim()
  if (!text) return 'empty'
  if (isUrl(text)) return 'url'
  if (isHtml(text)) return 'html'
  return 'markdown'
})

const htmlContent = computed(() => {
  if (contentType.value !== 'html') return ''
  return DOMPurify.sanitize(content.value, {
    ALLOW_DATA_ATTR: false
  })
})

const markdownHtml = computed(() => {
  if (contentType.value !== 'markdown') return ''
  return renderMarkdown(content.value)
})

const urlHref = computed(() => content.value.trim())

async function loadContent(): Promise<void> {
  const cached = localStorage.getItem(props.cacheKey) || ''
  if (cached) {
    content.value = cached
    loading.value = false
  }

  try {
    const data = await fetchDocument()
    if (data) {
      content.value = data
      localStorage.setItem(props.cacheKey, data)
    } else {
      // 空内容：清空缓存 + 由 contentType 判断为 'empty'（不视为 error）
      content.value = ''
      localStorage.removeItem(props.cacheKey)
    }
  } catch (e) {
    if (!cached) {
      error.value = e as Error
      ElMessage.error(props.emptyMessage || t('legal.loadFailed', { title: props.title }))
    }
  } finally {
    loading.value = false
  }
}

async function fetchDocument(): Promise<string> {
  return request.get<string>(props.apiEndpoint)
}

onMounted(loadContent)
</script>

<template>
  <div class="document-renderer">
    <div
      v-if="loading"
      class="document-renderer__loading"
    >
      <i class="i-ep-loading document-renderer__spin" />
      <span>{{ t('common.loading') }}</span>
    </div>

    <div
      v-else-if="error && !content"
      class="document-renderer__error"
    >
      <i class="i-ep-document-delete document-renderer__error-icon" />
      <p class="document-renderer__error-text">
        {{ emptyMessage || t('legal.loadFailed', { title }) }}
      </p>
    </div>

    <div
      v-else-if="contentType === 'empty'"
      class="document-renderer__empty"
    >
      <i class="i-ep-document-delete document-renderer__empty-icon" />
      <p class="document-renderer__empty-text">
        {{ t('legal.empty', { title }) }}
      </p>
    </div>

    <div
      v-else-if="contentType === 'url'"
      class="document-renderer__url-card"
    >
      <h2 class="document-renderer__title">
        {{ title }}
      </h2>
      <p class="document-renderer__url-hint">
        {{ t('legal.externalLinkHint') }}
      </p>
      <a
        :href="urlHref"
        target="_blank"
        rel="noopener noreferrer"
        class="document-renderer__url-link"
      >
        <i class="i-ep-link" />
        {{ t('legal.visit', { title }) }}
      </a>
    </div>

    <div
      v-else
      class="document-renderer__content"
    >
      <h2 class="document-renderer__title">
        {{ title }}
      </h2>
      <!-- markdownHtml 与 htmlContent 均经 DOMPurify 清洗，安全可信 -->
      <div
        v-if="contentType === 'markdown'"
        class="document-renderer__markdown prose"
        v-html="markdownHtml"
      />
      <div
        v-else
        class="document-renderer__html prose"
        v-html="htmlContent"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.document-renderer {
  min-height: 60vh;
  padding: var(--ys-spacing-8) var(--ys-spacing-4);

  &__loading,
  &__error,
  &__empty {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: center;
    min-height: 400px;
    color: var(--el-text-color-secondary);
  }

  &__spin {
    font-size: var(--ys-font-size-2xl);
    animation: rotate 1.4s linear infinite;
  }

  &__error-icon,
  &__empty-icon {
    font-size: 48px;
    color: var(--el-text-color-placeholder);
  }

  &__error-text,
  &__empty-text {
    margin: 0;
    text-align: center;
  }

  &__url-card {
    max-width: 640px;
    padding: var(--ys-spacing-8);
    margin: 0 auto;
    text-align: center;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }

  &__url-hint {
    margin: var(--ys-spacing-3) 0 var(--ys-spacing-6);
    color: var(--el-text-color-secondary);
  }

  &__url-link {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    padding: 10px var(--ys-spacing-6);
    color: #fff;
    text-decoration: none;
    background: var(--el-color-primary);
    border-radius: var(--ys-radius-base);
    transition: opacity 0.2s;

    &:hover {
      opacity: 0.9;
    }
  }

  &__content {
    max-width: 800px;
    padding: var(--ys-spacing-8);
    margin: 0 auto;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }

  &__title {
    margin: 0 0 var(--ys-spacing-6);
    font-size: var(--ys-font-size-2xl);
    font-weight: 600;
    text-align: center;
  }

  &__markdown,
  &__html {
    line-height: 1.7;
    overflow-wrap: anywhere;

    :deep(h1) { margin: var(--ys-spacing-6) 0 var(--ys-spacing-4); font-size: 22px; font-weight: 600; }
    :deep(h2) { margin: var(--ys-spacing-5) 0 var(--ys-spacing-3); font-size: var(--ys-font-size-xl); font-weight: 600; }
    :deep(h3) { margin: 18px 0 10px; font-size: 18px; font-weight: 500; }
    :deep(p) { margin: var(--ys-spacing-3) 0; }
    :deep(ul), :deep(ol) { padding-left: 24px; margin: var(--ys-spacing-3) 0; }
    :deep(li) { margin: 6px 0; }
    :deep(a) { color: var(--el-color-primary); text-decoration: underline; }

    :deep(blockquote) {
      padding: var(--ys-spacing-2) var(--ys-spacing-4);
      margin: var(--ys-spacing-4) 0;
      color: var(--el-text-color-secondary);
      background: var(--el-fill-color-light);
      border-left: 4px solid var(--el-border-color);
    }

    :deep(code) {
      padding: 2px 6px;
      font-family: var(--el-font-family-mono);
      font-size: 0.9em;
      background: var(--el-fill-color);
      border-radius: var(--ys-radius-sm);
    }

    :deep(pre) {
      padding: var(--ys-spacing-4);
      margin: var(--ys-spacing-4) 0;
      overflow-x: auto;
      background: var(--el-fill-color-darker);
      border-radius: var(--ys-radius-base);
    }

    :deep(table) {
      width: 100%;
      margin: var(--ys-spacing-4) 0;
      border-collapse: collapse;
    }

    :deep(th), :deep(td) {
      padding: var(--ys-spacing-2) var(--ys-spacing-3);
      border: 1px solid var(--el-border-color);
    }

    :deep(th) {
      font-weight: 500;
      background: var(--el-fill-color-light);
    }
  }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
