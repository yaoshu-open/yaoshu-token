<script setup lang="ts">
// 关于页：从后端 /api/about 获取内容，按类型渲染（URL/HTML/Markdown/空态）
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElSkeleton, ElEmpty } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { getAboutContent } from '@/api/about'

const { t } = useI18n()
const md = new MarkdownIt({ html: false, linkify: true, typographer: true })

const rawContent = ref<string>('')
const loading = ref(true)

function isValidUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function isLikelyHtml(value: string): boolean {
  return /<\/?[a-z][\s\S]*>/i.test(value)
}

const content = computed(() => {
  const trimmed = rawContent.value.trim()
  if (!trimmed) return { type: 'empty' as const }
  if (isValidUrl(trimmed)) return { type: 'url' as const, url: trimmed }
  if (isLikelyHtml(trimmed)) return { type: 'html' as const, html: trimmed }
  return { type: 'markdown' as const, rendered: md.render(trimmed) }
})

onMounted(async () => {
  try {
    rawContent.value = (await getAboutContent()) ?? ''
  } catch {
    rawContent.value = ''
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="about-page">
    <div class="about-page__hero">
      <h1 class="about-page__title">
        {{ t('nav.about') }}
      </h1>
    </div>

    <div class="about-page__content">
      <ElSkeleton
        v-if="loading"
        :rows="5"
        animated
      />

      <!-- URL：iframe 嵌入 -->
      <iframe
        v-else-if="content.type === 'url'"
        :src="content.url"
        class="about-page__iframe"
        frameborder="0"
      />

      <!-- HTML：直接渲染 -->
      <div
        v-else-if="content.type === 'html'"
        class="about-page__html"
        v-html="content.html"
      />

      <!-- Markdown：渲染为 HTML -->
      <div
        v-else-if="content.type === 'markdown'"
        class="about-page__markdown markdown-body"
        v-html="content.rendered"
      />

      <!-- 空态 -->
      <div
        v-else
        class="about-page__empty"
      >
        <ElEmpty
          :description="t('about.empty')"
          :image-size="100"
        />
        <p class="about-page__repo">
          <a
            href="https://github.com/yaoshu-open/yaoshu-token"
            target="_blank"
            rel="noopener"
          >
            GitHub
          </a>
        </p>
        <p class="about-page__copyright">
          Yaoshu Token &copy; {{ new Date().getFullYear() }} Yaoshu Token Community
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.about-page {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-6);
  max-width: 800px;
  padding: var(--ys-spacing-12) var(--ys-spacing-6);
  margin: 0 auto;

  &__hero {
    text-align: center;
  }

  &__title {
    margin: 0;
    font-size: 32px;
    font-weight: 700;
  }

  &__content {
    min-height: 300px;
  }

  &__iframe {
    width: 100%;
    min-height: calc(100vh - 200px);
    border: none;
  }

  &__html,
  &__markdown {
    line-height: 1.8;
    color: var(--el-text-color-primary);

    :deep(h1),
    :deep(h2),
    :deep(h3) {
      margin-top: 1.5em;
      margin-bottom: 0.5em;
    }

    :deep(a) {
      color: var(--el-color-primary);
    }

    :deep(code) {
      padding: 2px 6px;
      font-size: 0.9em;
      background: var(--el-fill-color-light);
      border-radius: var(--ys-radius-sm);
    }

    :deep(pre) {
      padding: var(--ys-spacing-4);
      overflow-x: auto;
      background: var(--el-fill-color-darker);
      border-radius: var(--ys-radius-md);

      code {
        padding: 0;
        background: none;
      }
    }
  }

  &__empty {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
    align-items: center;
  }

  &__repo a {
    font-size: var(--ys-font-size-base);
    color: var(--el-color-primary);
  }

  &__copyright {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }
}
</style>
