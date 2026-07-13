<script setup lang="ts">
/**
 * 模型可用性诊断对话框。
 *
 * 检查指定模型在指定分组下的真实分发状态，帮助运营区分
 * "模型测试通过但 API 调用返回 503" 的根因。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { diagnoseModel, getEnabledModels } from '@/api/channel'
import { CHANNEL_STATUS_CONFIG } from '@/api/channel/constants'
import type { ModelRoutingDiagnoseResponse } from '@/api/channel/types'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const modelName = ref('')
const groupName = ref('default')
const loading = ref(false)
const result = ref<ModelRoutingDiagnoseResponse | null>(null)
const enabledModels = ref<string[]>([])

watch(visible, async (v) => {
  if (v && enabledModels.value.length === 0) {
    try {
      enabledModels.value = await getEnabledModels()
    } catch {
      // 获取失败不阻塞，用户可手动输入
    }
  }
  if (!v) {
    result.value = null
  }
})

async function handleDiagnose(): Promise<void> {
  if (!modelName.value.trim()) {
    ElMessage.warning('请输入模型名')
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await diagnoseModel(
      modelName.value.trim(),
      groupName.value.trim() || undefined
    )
  } catch {
    ElMessage.error('诊断请求失败')
  } finally {
    loading.value = false
  }
}

function statusLabel(status: number): string {
  const config = CHANNEL_STATUS_CONFIG[status as keyof typeof CHANNEL_STATUS_CONFIG]
  return config ? config.label : 'unknown'
}

function statusType(status: number): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const config = CHANNEL_STATUS_CONFIG[status as keyof typeof CHANNEL_STATUS_CONFIG]
  const variant = config?.variant ?? 'info'
  return variant === 'neutral' ? 'info' : variant
}
</script>

<template>
  <ElDialog
    v-model="visible"
    title="模型可用性诊断"
    width="720px"
    :close-on-click-modal="false"
  >
    <ElForm inline>
      <ElFormItem label="模型名">
        <ElAutocomplete
          v-model="modelName"
          :fetch-suggestions="(query: string, cb: (items: { value: string }[]) => void) => cb(enabledModels.filter(m => m.includes(query)).map(m => ({ value: m })))"
          placeholder="如 gpt-4o"
          style="width: 240px"
          @keyup.enter="handleDiagnose"
        />
      </ElFormItem>
      <ElFormItem label="分组">
        <ElInput
          v-model="groupName"
          placeholder="default"
          style="width: 160px"
          @keyup.enter="handleDiagnose"
        />
      </ElFormItem>
      <ElFormItem>
        <ElButton
          type="primary"
          :icon="Search"
          :loading="loading"
          @click="handleDiagnose"
        >
          诊断
        </ElButton>
      </ElFormItem>
    </ElForm>

    <template v-if="result">
      <ElAlert
        :type="result.available ? 'success' : 'error'"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      >
        <template #title>
          {{ result.available ? '模型可用' : '模型不可用' }}
        </template>
        <template v-if="result.reason" #default>
          <div style="font-size: 13px; line-height: 1.6">{{ result.reason }}</div>
        </template>
      </ElAlert>

      <ElAlert
        v-if="result.suggestion"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      >
        <template #title>操作建议</template>
        <template #default>
          <div style="font-size: 13px; line-height: 1.6">{{ result.suggestion }}</div>
        </template>
      </ElAlert>

      <template v-if="result.channels && result.channels.length > 0">
        <h4 style="margin: 0 0 8px; font-size: 14px; font-weight: 600">
          候选渠道列表（{{ result.channels.length }} 个）
        </h4>
        <ElTable :data="result.channels" border size="small">
          <ElTableColumn prop="channelId" label="ID" width="70" />
          <ElTableColumn prop="channelName" label="渠道名称" min-width="140" />
          <ElTableColumn label="状态" width="100">
            <template #default="{ row }">
              <ElTag size="small" :type="statusType(row.status)">
                {{ statusLabel(row.status) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="priority" label="优先级" width="80" />
          <ElTableColumn prop="weight" label="权重" width="70" />
          <ElTableColumn label="候选池" width="80">
            <template #default="{ row }">
              <ElTag
                size="small"
                :type="row.excluded ? 'danger' : 'success'"
                effect="plain"
              >
                {{ row.excluded ? '已排除' : '可用' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="excludeReason" label="排除原因" min-width="200" show-overflow-tooltip />
        </ElTable>
      </template>
    </template>

    <template v-else-if="!loading">
      <ElEmpty description="输入模型名和分组，点击诊断查看结果" />
    </template>
  </ElDialog>
</template>
