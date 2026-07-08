<script setup lang="ts">
/**
 * 系统初始化向导（interactive 模式）。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_公共与系统.md §1.1
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElSteps, ElStep } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { setupSystem } from '@/api/setup'
import { useSetupStatus } from '@/composables/useSetupStatus'
import type { SetupFormData } from './types'
import DatabaseStep from '@/components/setup/DatabaseStep.vue'
import AdminStep from '@/components/setup/AdminStep.vue'
import UsageModeStep from '@/components/setup/UsageModeStep.vue'
import CompleteStep from '@/components/setup/CompleteStep.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { setupStatus, fetchSetupStatus } = useSetupStatus()

const currentStep = ref(0)
const submitting = ref(false)

const formData = reactive<SetupFormData>({
  username: '',
  password: '',
  confirmPassword: '',
  usageMode: 'external',
  demoSiteEnabled: false
})

const rootInit = computed(() => setupStatus.value?.rootInit === true)
const databaseType = computed(() => setupStatus.value?.databaseType || 'mysql')

// rootInit=true 时跳过管理员账号步骤（已存在 root 用户）
const steps = computed(() => {
  const all = [
    { key: 'database', title: t('setup.steps.database') },
    { key: 'admin', title: t('setup.steps.admin') },
    { key: 'usageMode', title: t('setup.steps.usageMode') },
    { key: 'complete', title: t('setup.steps.complete') }
  ] as const
  return rootInit.value ? all.filter((s) => s.key !== 'admin') : all
})

const currentStepKey = computed(() => steps.value[currentStep.value]?.key)

function handleNext(): void {
  if (currentStep.value < steps.value.length - 1) currentStep.value++
}
function handleBack(): void {
  if (currentStep.value > 0) currentStep.value--
}

async function handleSubmit(): Promise<void> {
  submitting.value = true
  try {
    await setupSystem({
      username: formData.username,
      password: formData.password,
      confirmPassword: formData.confirmPassword,
      selfUseModeEnabled: formData.usageMode === 'self-use',
      demoSiteEnabled: formData.demoSiteEnabled
    })
    // 成功：强制刷新缓存（标记为已初始化）+ 跳登录页用新账号登录
    await fetchSetupStatus(true)
    ElMessage.success(t('setup.complete.success'))
    router.push('/sign-in')
  } catch {
    // 拦截器已弹 ElMessage（如"系统已经初始化完成"），刷新状态后跳首页
    await fetchSetupStatus(true)
    router.push('/')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 直接访问 /setup 时确保 setupStatus 已拉取；守卫已拉则走缓存
  await fetchSetupStatus()
  // 已初始化系统访问 /setup：跳首页（与守卫一致的防御）
  if (setupStatus.value?.status === true && route.path === '/setup') {
    router.replace('/')
  }
})
</script>

<template>
  <div class="setup-wizard">
    <div class="setup-wizard__inner">
      <h1 class="setup-wizard__title">
        {{ t('setup.title') }}
      </h1>
      <el-steps
        :active="currentStep"
        align-center
        class="setup-wizard__steps"
      >
        <el-step
          v-for="step in steps"
          :key="step.key"
          :title="step.title"
        />
      </el-steps>

      <div class="setup-wizard__content">
        <database-step
          v-if="currentStepKey === 'database'"
          :database-type="databaseType"
          @next="handleNext"
        />
        <admin-step
          v-else-if="currentStepKey === 'admin'"
          :form-data="formData"
          @next="handleNext"
          @back="handleBack"
        />
        <usage-mode-step
          v-else-if="currentStepKey === 'usageMode'"
          :form-data="formData"
          @next="handleNext"
          @back="handleBack"
        />
        <complete-step
          v-else-if="currentStepKey === 'complete'"
          :form-data="formData"
          :submitting="submitting"
          @submit="handleSubmit"
          @back="handleBack"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.setup-wizard {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: 100vh;
  padding: var(--ys-spacing-12) var(--ys-spacing-4);
  background-color: var(--el-bg-color-page);
}

.setup-wizard__inner {
  width: 100%;
  max-width: 640px;
}

.setup-wizard__title {
  margin: 0 0 var(--ys-spacing-8);
  font-size: var(--ys-font-size-2xl);
  text-align: center;
}

.setup-wizard__steps {
  margin-bottom: var(--ys-spacing-8);
}

.setup-wizard__content {
  background-color: var(--el-bg-color);
  border-radius: var(--ys-radius-md);
}
</style>
