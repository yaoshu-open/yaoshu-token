<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  CircleCheck,
  CircleClose,
  Key,
  Promotion,
  CopyDocument,
  ArrowRight,
} from '@element-plus/icons-vue'
import { useSystemConfigStore } from '@/store/modules/system-config'
import { useUserPermissions } from '@/composables/useUserPermissions'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'
import { getToken } from '@/api/token'
import type { UserInfo } from '@/api/user/types'
import type { Token } from '@/api/token/types'

interface SetupGuideProps {
  userInfo: UserInfo | null
  preferredKey: Token | null
  availableModels: string[]
  requestCount: number
  remainQuota: number
  usedQuota: number
  expanded: boolean
}

const props = defineProps<SetupGuideProps>()
const emit = defineEmits<{ (e: 'toggle', expanded: boolean): void }>()
const { t } = useI18n()
const router = useRouter()
const { isAdmin } = useUserPermissions()
const { copy } = useCopyToClipboard()
const systemConfig = useSystemConfigStore()

const expanded = ref(props.expanded)
watch(() => props.expanded, (v) => { expanded.value = v })
watch(expanded, (v) => emit('toggle', v))

function toggleExpanded() {
  expanded.value = !expanded.value
}

interface Step {
  key: string
  title: string
  done: boolean
  path: string
}

const steps = computed<Step[]>(() => [
  {
    key: 'create-key',
    title: t('dashboard.setup.createKey'),
    done: !!props.preferredKey,
    path: '/tokens',
  },
  {
    key: 'topup',
    title: t('dashboard.setup.topup'),
    done: props.remainQuota > 0 || props.usedQuota > 0,
    path: '/wallet',
  },
  {
    key: 'first-request',
    title: t('dashboard.setup.firstRequest'),
    done: props.requestCount > 0,
    path: '/playground',
  },
])

const allDone = computed(() => steps.value.every((s) => s.done))

const apiBaseUrl = computed(() => {
  const apiInfo = systemConfig.rawStatus?.apiInfo as Array<{ url?: string }> | undefined
  return apiInfo?.[0]?.url || window.location.origin
})

const curlModel = computed(() => props.availableModels[0] || 'gpt-4o-mini')

const curlPreview = computed(() => {
  const keyHint = props.preferredKey
    ? `sk-${String(props.preferredKey.key || '').slice(0, 4)}...${String(props.preferredKey.key || '').slice(-4)}`
    : 'sk-xxxx...xxxx'
  return `curl ${apiBaseUrl.value}/v1/chat/completions \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer ${keyHint}" \\
  -d '{
    "model": "${curlModel.value}",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'`
})

async function copyCurl() {
  if (!props.preferredKey) {
    router.push('/tokens')
    return
  }
  try {
    const fullToken = await getToken(props.preferredKey.id)
    const realCurl = curlPreview.value.replace(
      /sk-xxxx\.\.\..*?(?=")/,
      fullToken.key || '',
    )
    await copy(realCurl)
  } catch {
    await copy(curlPreview.value)
  }
}

const quickActions = computed(() => {
  const actions = [
    { label: 'API Keys', path: '/tokens', icon: Key },
    { label: t('nav.usageLogs'), path: '/usage-logs/common', icon: Promotion },
    { label: t('nav.pricing'), path: '/pricing', icon: ArrowRight },
  ]
  if (isAdmin.value) {
    actions.splice(1, 0, { label: t('nav.channels'), path: '/channels', icon: ArrowRight })
  }
  return actions
})

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="setup-guide">
    <div
      class="setup-guide__header"
      @click="toggleExpanded"
    >
      <div class="setup-guide__title-row">
        <span class="setup-guide__title">{{ t('dashboard.setup.title') }}</span>
        <ElTag
          v-if="allDone"
          type="success"
          size="small"
          effect="light"
        >
          {{ t('dashboard.setup.allDone') }}
        </ElTag>
      </div>
      <ElIcon
        class="setup-guide__toggle"
        :class="{ 'is-expanded': expanded }"
      >
        <ArrowRight />
      </ElIcon>
    </div>

    <ElCollapseTransition>
      <div
        v-show="expanded"
        class="setup-guide__body"
      >
        <div class="setup-guide__steps">
          <div
            v-for="(step, idx) in steps"
            :key="step.key"
            class="setup-guide__step"
            :class="{ 'is-done': step.done }"
            @click="go(step.path)"
          >
            <ElIcon class="setup-guide__step-icon">
              <CircleCheck v-if="step.done" />
              <CircleClose v-else />
            </ElIcon>
            <div class="setup-guide__step-content">
              <span class="setup-guide__step-index">{{ idx + 1 }}</span>
              <span class="setup-guide__step-title">{{ step.title }}</span>
            </div>
            <ElIcon class="setup-guide__step-arrow">
              <ArrowRight />
            </ElIcon>
          </div>
        </div>

        <div class="setup-guide__curl">
          <div class="setup-guide__curl-header">
            <span class="setup-guide__curl-title">{{ t('dashboard.setup.curlPreview') }}</span>
            <ElButton
              size="small"
              :icon="CopyDocument"
              @click="copyCurl"
            >
              {{ props.preferredKey ? t('common.copy') : t('dashboard.setup.createKey') }}
            </ElButton>
          </div>
          <pre class="setup-guide__curl-code">{{ curlPreview }}</pre>
        </div>

        <div class="setup-guide__actions">
          <ElButton
            v-for="action in quickActions"
            :key="action.path"
            :icon="action.icon"
            size="small"
            plain
            @click="go(action.path)"
          >
            {{ action.label }}
          </ElButton>
        </div>
      </div>
    </ElCollapseTransition>
  </div>
</template>

<style scoped lang="scss">
.setup-guide {
  overflow: hidden;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-lg);

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-4) var(--ys-spacing-5);
    cursor: pointer;
    user-select: none;

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__title-row {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__toggle {
    color: var(--el-text-color-placeholder);
    transition: transform 0.2s;

    &.is-expanded {
      transform: rotate(90deg);
    }
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
    padding: 0 var(--ys-spacing-5) var(--ys-spacing-5);
  }

  &__steps {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__step {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
    padding: var(--ys-spacing-3);
    cursor: pointer;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
    transition: all 0.2s;

    &:hover {
      background: var(--el-color-primary-light-9);
      border-color: var(--el-color-primary-light-5);
    }

    &.is-done {
      opacity: 0.7;
    }
  }

  &__step-icon {
    font-size: 18px;

    .is-done & {
      color: var(--el-color-success);
    }

    :not(.is-done) & {
      color: var(--el-text-color-placeholder);
    }
  }

  &__step-content {
    display: flex;
    flex: 1;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__step-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    color: var(--el-text-color-placeholder);
    border: 1px solid var(--el-border-color);
    border-radius: 50%;
  }

  &__step-title {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-primary);
  }

  &__step-arrow {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-placeholder);
  }

  &__curl {
    overflow: hidden;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__curl-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    background: var(--el-fill-color-light);
  }

  &__curl-title {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__curl-code {
    padding: var(--ys-spacing-3);
    margin: 0;
    overflow-x: auto;
    font-family: var(--el-font-family-mono, monospace);
    font-size: var(--ys-font-size-xs);
    line-height: 1.6;
    color: var(--el-text-color-primary);
    white-space: pre;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
  }
}
</style>
