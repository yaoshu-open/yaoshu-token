<script setup lang="ts">
/**
 * 渠道编辑抽屉容器（T-CH-04 第二批）。
 *
 * 职责：表单 composable 持有 / provide 注入 / 抽屉骨架 / 提交流程（含缺失模型确认）。
 * 不含：多密钥批量（第四批）/ Codex（第四批）/ 参数覆盖编辑器（第三批）/ 状态码风险守卫（第三批）。
 */
import { computed, provide, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElDrawer, ElForm, ElMessage, ElMessageBox } from 'element-plus'
import { useChannelMutateForm, CHANNEL_MUTATE_FORM_KEY } from '@/composables/channel/useChannelMutateForm'
import type { Channel } from '@/api/channel/types'
import ChannelBasicSection from './sections/ChannelBasicSection.vue'
import ChannelAuthSection from './sections/ChannelAuthSection.vue'
import ChannelModelsSection from './sections/ChannelModelsSection.vue'
import ChannelApiAccessSection from './sections/ChannelApiAccessSection.vue'
import ChannelAdvancedSection from './sections/ChannelAdvancedSection.vue'
import ChannelEditorLoadingState from './sections/ChannelEditorLoadingState.vue'
import MissingModelsDialog from './dialogs/MissingModelsDialog.vue'
import StatusCodeRiskDialog from './dialogs/StatusCodeRiskDialog.vue'

const props = defineProps<{
  modelValue: boolean
  /** 编辑模式时传入渠道 ID；创建模式传 null */
  channelId: number | null
  /** 可选：直接传入渠道对象（跳过 API 拉取） */
  channel?: Channel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', mode: 'create' | 'update'): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// ============================================================================
// 表单 composable
// ============================================================================

const formCtx = useChannelMutateForm({
  onSuccess: (mode) => {
    emit('success', mode)
    ElMessage.success(
      mode === 'create'
        ? t('channel.edit.success.create')
        : t('channel.edit.success.update')
    )
    visible.value = false
  }
})

provide(CHANNEL_MUTATE_FORM_KEY, formCtx)

const { form, isCreateMode, initLoading, submitting, initCreate, initEdit, initEditWithChannel, submit, forceSubmit } = formCtx

// ============================================================================
// 缺失模型确认
// ============================================================================

const missingDialogVisible = ref(false)
const missingModels = ref<string[]>([])

// 状态码风险守卫（提交时检测高危重定向，确认后才继续）
const statusCodeRiskOpen = ref(false)
const statusCodeRiskItems = ref<string[]>([])

// ============================================================================
// 抽屉标题
// ============================================================================

const drawerTitle = computed(() =>
  isCreateMode.value
    ? t('channel.edit.title.create')
    : t('channel.edit.title.edit')
)

// ============================================================================
// 打开时初始化
// ============================================================================

watch(visible, async (v) => {
  if (!v) return
  if (props.channelId != null) {
    if (props.channel) {
      initEditWithChannel(props.channel)
    } else {
      await initEdit(props.channelId)
    }
  } else {
    initCreate()
  }
})

// ============================================================================
// 提交
// ============================================================================

async function handleSubmit(): Promise<void> {
  const result = await submit()
  if (!result.success) {
    if (result.statusCodeRisk && result.statusCodeRisk.length > 0) {
      statusCodeRiskItems.value = result.statusCodeRisk
      statusCodeRiskOpen.value = true
    } else if (result.missingModels && result.missingModels.length > 0) {
      missingModels.value = result.missingModels
      missingDialogVisible.value = true
    } else {
      ElMessage.warning(t('channel.edit.validate.fixErrors'))
    }
  }
}

// 风险确认后跳过守卫继续提交（可能再遇到缺失模型检测）
async function handleStatusCodeRiskConfirm(): Promise<void> {
  const result = await submit({ skipRiskGuard: true })
  if (!result.success) {
    if (result.missingModels && result.missingModels.length > 0) {
      missingModels.value = result.missingModels
      missingDialogVisible.value = true
    } else {
      ElMessage.warning(t('channel.edit.validate.fixErrors'))
    }
  }
}

async function handleForceSubmit(): Promise<void> {
  const result = await forceSubmit()
  if (!result.success) {
    ElMessage.warning(t('channel.edit.validate.fixErrors'))
  }
}

async function handleClose(): Promise<void> {
  // 提交中不允许关闭
  if (submitting.value) return

  // 表单有数据时确认关闭
  if (form.name || form.key || form.models) {
    try {
      await ElMessageBox.confirm(
        t('channel.edit.closeConfirm'),
        t('channel.edit.closeConfirmTitle'),
        {
          confirmButtonText: t('channel.edit.closeConfirmOk'),
          cancelButtonText: t('channel.edit.closeConfirmCancel'),
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }
  visible.value = false
}
</script>

<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="640px"
    :title="drawerTitle"
    :before-close="handleClose"
    :close-on-click-modal="false"
  >
    <div class="channel-edit-drawer">
      <!-- 加载态 -->
      <ChannelEditorLoadingState v-if="initLoading" />

      <!-- 表单 -->
      <el-form
        v-else
        :model="form"
        label-position="top"
        class="channel-edit-drawer__form"
      >
        <ChannelBasicSection />
        <ChannelAuthSection />
        <ChannelModelsSection />
        <ChannelApiAccessSection />
        <ChannelAdvancedSection />
      </el-form>
    </div>

    <!-- 底部操作栏 -->
    <template #footer>
      <div class="channel-edit-drawer__footer">
        <el-button
          :disabled="submitting"
          @click="handleClose"
        >
          {{ t('channel.edit.actions.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ isCreateMode ? t('channel.edit.actions.create') : t('channel.edit.actions.save') }}
        </el-button>
      </div>
    </template>

    <!-- 缺失模型确认 -->
    <MissingModelsDialog
      v-model="missingDialogVisible"
      :missing-models="missingModels"
      @confirm="handleForceSubmit"
    />

    <!-- 状态码风险守卫（提交时检测高危重定向） -->
    <StatusCodeRiskDialog
      v-model="statusCodeRiskOpen"
      :detail-items="statusCodeRiskItems"
      @confirm="handleStatusCodeRiskConfirm"
    />
  </el-drawer>
</template>

<style scoped>
.channel-edit-drawer {
  height: 100%;
  overflow-y: auto;
}

.channel-edit-drawer__form {
  padding: 0 var(--ys-spacing-1);
}

.channel-edit-drawer__footer {
  display: flex;
  gap: var(--ys-spacing-3);
  justify-content: flex-end;
}
</style>
