<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  API_DEMOS,
  CYCLE_INTERVAL,
  TRANSITION_MS,
  tokenizeJsonLine,
  tokenizeResponseLine,
  type ApiDemoConfig,
  type Token
} from './terminal-demos'

interface Props {
  class?: string
}
const props = defineProps<Props>()

// 组件内状态：零 Pinia 依赖
const activeIndex = ref(0)
const transitioning = ref(false)
let intervalId: ReturnType<typeof setInterval> | undefined
let timeoutId: ReturnType<typeof setTimeout> | undefined

const demo = computed<ApiDemoConfig>(() => API_DEMOS[activeIndex.value])

// request 行预计算 tokenize 结果（随 activeIndex 响应式更新）
const requestTokenLines = computed<Token[][]>(() =>
  demo.value.request.map((line) => tokenizeJsonLine(line))
)
const responseTokenLines = computed<Token[][]>(() =>
  demo.value.response.map((line) => tokenizeResponseLine(line, demo.value))
)

function switchTo(index: number): void {
  transitioning.value = true
  timeoutId = setTimeout(() => {
    activeIndex.value = index
    transitioning.value = false
  }, TRANSITION_MS)
}

function handleSelect(index: number): void {
  if (index === activeIndex.value) return
  if (intervalId) clearInterval(intervalId)
  if (timeoutId) clearTimeout(timeoutId)
  switchTo(index)
}

onMounted(() => {
  const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
  if (mq.matches) return

  intervalId = setInterval(() => {
    transitioning.value = true
    timeoutId = setTimeout(() => {
      activeIndex.value = (activeIndex.value + 1) % API_DEMOS.length
      transitioning.value = false
    }, TRANSITION_MS)
  }, CYCLE_INTERVAL)
})

onUnmounted(() => {
  if (intervalId) clearInterval(intervalId)
  if (timeoutId) clearTimeout(timeoutId)
})
</script>

<template>
  <div :class="['hero-terminal', props.class]">
    <div class="hero-terminal__card">
      <!-- Tab strip -->
      <div class="hero-terminal__tabs">
        <button
          v-for="(item, index) in API_DEMOS"
          :key="item.id"
          :class="[
            'hero-terminal__tab',
            `hero-terminal__tab--${item.accent}`,
            { 'hero-terminal__tab--active': index === activeIndex }
          ]"
          type="button"
          @click="handleSelect(index)"
        >
          {{ item.label }}
        </button>
        <div class="hero-terminal__status">
          <span class="hero-terminal__status-dot" />
          <span class="hero-terminal__status-text">200 OK</span>
        </div>
      </div>

      <!-- Endpoint row -->
      <div class="hero-terminal__endpoint">
        <span :class="['hero-terminal__method', `hero-terminal__method--${demo.accent}`]">
          {{ demo.method }}
        </span>
        <code
          class="hero-terminal__endpoint-path"
          :style="{ opacity: transitioning ? 0 : 1 }"
        >
          {{ demo.endpoint }}
        </code>
      </div>

      <!-- Body: fixed 400px height grid -->
      <div class="hero-terminal__body">
        <!-- Request -->
        <div class="hero-terminal__request">
          <span class="hero-terminal__section-label">Request</span>
          <div
            class="hero-terminal__code-block"
            :style="{ opacity: transitioning ? 0 : 1 }"
          >
            <div class="hero-terminal__code-line">
              <span class="t-command">curl</span>
              <span class="t-flag">-X</span>
              <span class="t-flag">POST</span>
              <span class="t-string">"{{ demo.endpoint }}"</span>
              <span class="t-muted">\</span>
            </div>
            <div
              v-for="(header, hi) in demo.headers"
              :key="hi"
              class="hero-terminal__code-line hero-terminal__code-line--indent-2"
            >
              <span class="t-flag">-H</span>
              <span class="t-string">{{ header }}</span>
              <span class="t-muted">\</span>
            </div>
            <div class="hero-terminal__code-line hero-terminal__code-line--indent-2">
              <span class="t-flag">-d</span>
              <span class="t-string">'{'</span>
            </div>
            <div
              v-for="(tokens, ri) in requestTokenLines"
              :key="ri"
              class="hero-terminal__code-line hero-terminal__code-line--indent-4"
            >
              <span
                v-for="(token, ti) in tokens"
                :key="ti"
                :class="`t-${token.type}`"
                :style="token.type === 'accent' ? { color: `var(--accent-${demo.accent})` } : undefined"
              >{{ token.text }}</span>
            </div>
            <div class="hero-terminal__code-line hero-terminal__code-line--indent-2">
              <span class="t-string">'}'</span>
            </div>
          </div>
        </div>

        <!-- Response -->
        <div class="hero-terminal__response">
          <span class="hero-terminal__section-label">Response</span>
          <div
            class="hero-terminal__code-block"
            :style="{ opacity: transitioning ? 0 : 1 }"
          >
            <div
              v-for="(tokens, ri) in responseTokenLines"
              :key="ri"
              class="hero-terminal__code-line"
            >
              <span
                v-for="(token, ti) in tokens"
                :key="ti"
                :class="`t-${token.type}`"
                :style="token.type === 'accent' ? { color: `var(--accent-${demo.accent})` } : undefined"
              >{{ token.text }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer metrics -->
      <div class="hero-terminal__footer">
        <div class="hero-terminal__metrics">
          <span class="hero-terminal__metric">
            <span class="hero-terminal__metric-value">{{ demo.latency }}</span>
            <span class="hero-terminal__metric-label">ms</span>
          </span>
          <span class="hero-terminal__metric-sep" />
          <span class="hero-terminal__metric">
            <span class="hero-terminal__metric-value">{{ demo.tokens }}</span>
            <span class="hero-terminal__metric-label">tokens</span>
          </span>
          <span class="hero-terminal__metric-sep" />
          <span class="hero-terminal__metric">
            <span class="hero-terminal__metric-label">cost</span>
            <span class="hero-terminal__metric-value">${{ (demo.tokens * 0.00003).toFixed(5) }}</span>
          </span>
        </div>
        <span class="hero-terminal__stream">stream · sse</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.hero-terminal {
  --accent-emerald: oklch(55% 0.15 155deg);
  --accent-amber: oklch(60% 0.14 60deg);
  --accent-blue: oklch(55% 0.2 250deg);
  --accent-violet: oklch(55% 0.2 300deg);

  width: 100%;
  max-width: 672px;
  margin: 0 auto;

  :global(html.dark) & {
    --accent-emerald: oklch(72% 0.17 155deg);
    --accent-amber: oklch(75% 0.15 65deg);
    --accent-blue: oklch(72% 0.18 250deg);
    --accent-violet: oklch(72% 0.2 300deg);
  }
}

.hero-terminal__card {
  overflow: hidden;
  background: rgb(255 255 255 / 95%);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--ys-radius-xl);
  box-shadow: 0 20px 50px -25px rgb(15 23 42 / 18%);
  backdrop-filter: blur(4px);

  :global(html.dark) & {
    background: rgb(11 15 23 / 95%);
    border-color: rgb(255 255 255 / 6%);
    box-shadow: 0 20px 60px -25px rgb(0 0 0 / 70%);
  }
}

// Tab strip
.hero-terminal__tabs {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  @media (width >= 640px) {
    gap: 6px;
    padding: 0 12px;
  }

  :global(html.dark) & {
    border-bottom-color: rgb(255 255 255 / 5%);
  }
}

.hero-terminal__tab {
  position: relative;
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 10px;
  margin-bottom: -1px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.025em;
  cursor: pointer;
  background: none;
  border-top: none;
  border-right: none;
  border-bottom: 2px solid transparent;
  border-left: none;
  transition: color 0.25s, border-bottom-color 0.25s;

  @media (width >= 640px) {
    padding: 10px 12px;
    font-size: 12px;
  }

  &:hover {
    color: var(--el-text-color-primary);
  }

  &--active {
    &.hero-terminal__tab--emerald {
      color: var(--accent-emerald);
      border-bottom-color: var(--accent-emerald);
    }

    &.hero-terminal__tab--amber {
      color: var(--accent-amber);
      border-bottom-color: var(--accent-amber);
    }

    &.hero-terminal__tab--blue {
      color: var(--accent-blue);
      border-bottom-color: var(--accent-blue);
    }

    &.hero-terminal__tab--violet {
      color: var(--accent-violet);
      border-bottom-color: var(--accent-violet);
    }
  }
}

.hero-terminal__status {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-right: 12px;
  margin-left: auto;
}

.hero-terminal__status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--accent-emerald);
  border-radius: 50%;
  box-shadow: 0 0 8px rgb(16 185 129 / 45%);
}

.hero-terminal__status-text {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 10px;
  color: var(--el-text-color-disabled);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

// Endpoint row
.hero-terminal__endpoint {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid var(--el-border-color-extra-light);

  :global(html.dark) & {
    border-bottom-color: rgb(255 255 255 / 4%);
  }
}

.hero-terminal__method {
  padding: 2px 6px;
  font-family: var(--el-font-family-mono, monospace);
  font-size: 10px;
  font-weight: 600;
  color: var(--accent-emerald);
  letter-spacing: 0.05em;
  background: color-mix(in srgb, var(--accent-emerald) 10%, transparent);
  border-radius: var(--ys-radius-sm);

  &--amber {
    color: var(--accent-amber);
    background: color-mix(in srgb, var(--accent-amber) 10%, transparent);
  }

  &--blue {
    color: var(--accent-blue);
    background: color-mix(in srgb, var(--accent-blue) 10%, transparent);
  }

  &--violet {
    color: var(--accent-violet);
    background: color-mix(in srgb, var(--accent-violet) 10%, transparent);
  }
}

.hero-terminal__endpoint-path {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  transition: opacity 0.2s;
}

// Body grid: fixed 400px height
.hero-terminal__body {
  display: grid;
  grid-template-rows: 235px minmax(0, 1fr);
  height: 400px;
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12.5px;
  line-height: 1.55;
}

.hero-terminal__request {
  position: relative;
  padding: 16px 20px;
  overflow: hidden;
}

.hero-terminal__response {
  position: relative;
  padding: 16px 20px;
  background: var(--el-fill-color-lighter);
  border-top: 1px solid var(--el-border-color-extra-light);

  :global(html.dark) & {
    background: rgb(255 255 255 / 1.5%);
    border-top-color: rgb(255 255 255 / 5%);
  }
}

.hero-terminal__section-label {
  display: block;
  font-family: var(--el-font-family);
  font-size: 10px;
  font-weight: 600;
  color: var(--el-text-color-placeholder);
  text-transform: uppercase;
  letter-spacing: 0.18em;
}

.hero-terminal__code-block {
  margin-top: 8px;
  transition: opacity 0.2s;
}

.hero-terminal__code-line {
  overflow-wrap: anywhere;
  white-space: pre-wrap;

  &--indent-2::before {
    white-space: pre;
    content: '  ';
  }

  &--indent-4::before {
    white-space: pre;
    content: '    ';
  }
}

// Token type colors
.t-command {
  font-weight: 500;
  color: oklch(55% 0.15 155deg);

  :global(html.dark) & {
    color: oklch(72% 0.17 155deg);
  }
}

.t-flag {
  color: oklch(55% 0.2 250deg);

  :global(html.dark) & {
    color: oklch(72% 0.18 250deg);
  }
}

.t-key {
  color: oklch(45% 0.14 240deg);

  :global(html.dark) & {
    color: oklch(72% 0.13 230deg);
  }
}

.t-string {
  color: oklch(50% 0.13 65deg);

  :global(html.dark) & {
    color: oklch(78% 0.14 70deg);
  }
}

.t-number {
  font-weight: 500;
  color: oklch(55% 0.2 300deg);

  :global(html.dark) & {
    color: oklch(75% 0.18 300deg);
  }
}

.t-muted {
  color: var(--el-text-color-disabled);
}

// Footer metrics
.hero-terminal__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--el-fill-color-lighter);
  border-top: 1px solid var(--el-border-color-extra-light);

  :global(html.dark) & {
    background: rgb(255 255 255 / 2%);
    border-top-color: rgb(255 255 255 / 5%);
  }
}

.hero-terminal__metrics {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-disabled);
}

.hero-terminal__metric {
  display: flex;
  gap: 4px;
  align-items: center;
}

.hero-terminal__metric-value {
  font-family: var(--el-font-family-mono, monospace);
}

.hero-terminal__metric-label {
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.hero-terminal__metric-sep {
  display: inline-block;
  width: 4px;
  height: 4px;
  background: var(--el-text-color-disabled);
  border-radius: 50%;
  opacity: 0.4;
}

.hero-terminal__stream {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 10px;
  color: var(--el-text-color-placeholder);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
</style>
