<script setup lang="ts">
/**
 * 管理员侧用户订阅查看/管理弹窗。
 * 管理员输入用户 ID 后查看该用户的订阅列表，可创建/作废/删除订阅。
 */
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUserSubscriptions,
  createUserSubscription,
  invalidateUserSubscription,
  deleteUserSubscription,
  getAdminPlans,
} from '@/api/subscription'
import type {
  UserSubscriptionRecord,
  PlanRecord,
  CreateUserSubscriptionRequest,
} from '@/api/subscription/types'
import { formatTimestamp } from '@/utils/subscription-format'
import { formatQuotaWithCurrency, formatPlanPrice } from '@/utils/currency'

interface Props {
  visible: boolean
  userId?: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()

// 对话框可见状态双向绑定
const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v),
})

// 用户 ID 输入与查询
const inputUserId = ref<string>('')
const queryUserId = ref<number>()
const loading = ref(false)
const subscriptions = ref<UserSubscriptionRecord[]>([])

// 套餐列表与创建订阅
const plans = ref<PlanRecord[]>([])
const plansLoading = ref(false)
const selectedPlanId = ref<number>()
const creating = ref(false)

// 行内操作 loading 状态
const actionLoading = ref<Record<number, boolean>>({})

// 状态徽章配置：active 绿色 / expired 灰色 / cancelled 橙色 / invalid 红色
const statusConfig = computed<Record<string, { type: 'success' | 'info' | 'warning' | 'danger'; label: string }>>(() => ({
  active: { type: 'success', label: t('subscription.admin.status.active') },
  expired: { type: 'info', label: t('subscription.admin.status.expired') },
  cancelled: { type: 'warning', label: t('subscription.admin.status.cancelled') },
  invalid: { type: 'danger', label: t('subscription.admin.status.invalid') },
  inactive: { type: 'danger', label: t('subscription.admin.status.inactive') },
}))

function getStatusInfo(status: string) {
  return statusConfig.value[status] ?? { type: 'info' as const, label: status }
}

// 获取用户订阅列表
async function loadSubscriptions() {
  const uid = queryUserId.value
  if (!uid) {
    ElMessage.warning(t('subscription.admin.userIdRequired'))
    return
  }
  loading.value = true
  try {
    const res = await getUserSubscriptions(uid)
    subscriptions.value = res ?? []
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('subscription.admin.loadListFailed'))
    subscriptions.value = []
  } finally {
    loading.value = false
  }
}

// 获取套餐列表（用于创建订阅时选择）
async function loadPlans() {
  plansLoading.value = true
  try {
    const res = await getAdminPlans()
    plans.value = res ?? []
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('subscription.admin.loadPlansFailed'))
  } finally {
    plansLoading.value = false
  }
}

// 查询按钮点击
function handleQuery() {
  const uid = Number(inputUserId.value)
  if (!uid || isNaN(uid)) {
    ElMessage.warning(t('subscription.admin.userIdInvalid'))
    return
  }
  queryUserId.value = uid
  loadSubscriptions()
}

// 创建订阅
async function handleCreate() {
  const uid = queryUserId.value
  if (!uid) {
    ElMessage.warning(t('subscription.admin.queryFirst'))
    return
  }
  if (!selectedPlanId.value) {
    ElMessage.warning(t('subscription.admin.planRequired'))
    return
  }
  creating.value = true
  try {
    const data: CreateUserSubscriptionRequest = {
      planId: selectedPlanId.value,
    }
    await createUserSubscription(uid, data)
    ElMessage.success(t('subscription.admin.createSuccess'))
    selectedPlanId.value = undefined
    await loadSubscriptions()
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('subscription.admin.createFailed'))
  } finally {
    creating.value = false
  }
}

// 作废订阅
async function handleInvalidate(id: number) {
  try {
    await ElMessageBox.confirm(
      t('subscription.admin.invalidateConfirmMsg'),
      t('subscription.admin.invalidateConfirmTitle'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return // 用户取消
  }
  actionLoading.value[id] = true
  try {
    await invalidateUserSubscription(id)
    ElMessage.success(t('subscription.admin.invalidateSuccess'))
    await loadSubscriptions()
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('subscription.admin.invalidateFailed'))
  } finally {
    actionLoading.value[id] = false
  }
}

// 删除订阅
async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm(
      t('subscription.admin.deleteConfirmMsg'),
      t('subscription.admin.deleteConfirmTitle'),
      { type: 'error', confirmButtonText: t('subscription.admin.deleteConfirmBtn'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return // 用户取消
  }
  actionLoading.value[id] = true
  try {
    await deleteUserSubscription(id)
    ElMessage.success(t('subscription.admin.deleteSuccess'))
    await loadSubscriptions()
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('subscription.admin.deleteFailed'))
  } finally {
    actionLoading.value[id] = false
  }
}

// 对话框打开/关闭时初始化与重置
watch(dialogVisible, (v) => {
  if (v) {
    // 传入 userId 时自动填充并查询
    if (props.userId) {
      inputUserId.value = String(props.userId)
      queryUserId.value = props.userId
      loadSubscriptions()
    } else {
      inputUserId.value = ''
      queryUserId.value = undefined
      subscriptions.value = []
    }
    loadPlans()
  } else {
    // 关闭时重置状态
    subscriptions.value = []
    selectedPlanId.value = undefined
    inputUserId.value = ''
    queryUserId.value = undefined
  }
})
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="t('subscription.admin.title')"
    width="960px"
    append-to-body
  >
    <!-- 顶部：用户 ID 输入 + 查询 -->
    <div class="query-bar">
      <ElInput
        v-model="inputUserId"
        :placeholder="t('subscription.admin.userIdPlaceholder')"
        type="number"
        style="width: 240px"
        @keyup.enter="handleQuery"
      />
      <ElButton
        type="primary"
        :loading="loading"
        @click="handleQuery"
      >
        {{ t('subscription.admin.query') }}
      </ElButton>
    </div>

    <!-- 订阅列表表格 -->
    <ElTable
      v-loading="loading"
      :data="subscriptions"
      stripe
      border
      style="width: 100%; margin-top: 16px"
      :empty-text="t('subscription.admin.emptyText')"
    >
      <ElTableColumn
        :label="t('subscription.admin.columns.planId')"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          {{ row.subscription.planId }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('subscription.admin.columns.status')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <ElTag
            :type="getStatusInfo(row.subscription.status).type"
            size="small"
          >
            {{ getStatusInfo(row.subscription.status).label }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('subscription.admin.columns.startTime')"
        width="170"
      >
        <template #default="{ row }">
          {{ formatTimestamp(row.subscription.startTime) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('subscription.admin.columns.endTime')"
        width="170"
      >
        <template #default="{ row }">
          {{ formatTimestamp(row.subscription.endTime) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('subscription.admin.columns.totalQuota')"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQuotaWithCurrency(row.subscription.amountTotal) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('subscription.admin.columns.usedQuota')"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQuotaWithCurrency(row.subscription.amountUsed) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('subscription.admin.columns.nextReset')"
        width="170"
      >
        <template #default="{ row }">
          {{ formatTimestamp(row.subscription.nextResetTime ?? 0) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('subscription.admin.columns.actions')"
        width="160"
        fixed="right"
      >
        <template #default="{ row }">
          <ElButton
            size="small"
            text
            type="warning"
            :disabled="
              row.subscription.status === 'invalid' ||
                row.subscription.status === 'inactive'
            "
            :loading="actionLoading[row.subscription.id]"
            @click="handleInvalidate(row.subscription.id)"
          >
            {{ t('subscription.admin.invalidate') }}
          </ElButton>
          <ElButton
            size="small"
            text
            type="danger"
            :loading="actionLoading[row.subscription.id]"
            @click="handleDelete(row.subscription.id)"
          >
            {{ t('subscription.admin.delete') }}
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 创建订阅区域 -->
    <div class="create-section">
      <span class="create-section__label">{{ t('subscription.admin.createSubscription') }}</span>
      <ElSelect
        v-model="selectedPlanId"
        :placeholder="t('subscription.admin.planPlaceholder')"
        :loading="plansLoading"
        style="width: 300px"
      >
        <ElOption
          v-for="item in plans"
          :key="item.plan.id"
          :label="`${item.plan.title}（${formatPlanPrice(item.plan.priceAmount, item.plan.currency)}）`"
          :value="item.plan.id"
        />
      </ElSelect>
      <ElButton
        type="primary"
        :loading="creating"
        :disabled="!queryUserId || !selectedPlanId"
        @click="handleCreate"
      >
        {{ t('subscription.admin.createBtn') }}
      </ElButton>
    </div>
  </ElDialog>
</template>

<style scoped>
.query-bar {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
}

.create-section {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.create-section__label {
  font-size: var(--ys-font-size-base);
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}
</style>
