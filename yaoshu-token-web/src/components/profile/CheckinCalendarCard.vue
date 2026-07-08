<template>
  <ElCard shadow="never">
    <template #header>
      <div class="checkin-card__header">
        <ElIcon :size="18">
          <Calendar />
        </ElIcon>
        <span>{{ t('profile.checkinCalendar') }}</span>
      </div>
    </template>

    <div
      v-loading="loading"
      class="checkin-card__body"
    >
      <!-- 功能未启用：业务状态非错误，渲染占位而非空白日历 -->
      <div
        v-if="featureDisabled"
        class="checkin-card__disabled"
      >
        <ElIcon
          :size="32"
          class="checkin-card__disabled-icon"
        >
          <Clock />
        </ElIcon>
        <p class="checkin-card__disabled-text">
          {{ t('profile.checkinFeatureDisabled') }}
        </p>
      </div>

      <template v-else>
        <!-- 月份切换 + 签到按钮 -->
        <div class="checkin-card__toolbar">
          <div class="checkin-card__month-nav">
            <ElButton
              size="small"
              :icon="ArrowLeft"
              @click="prevMonth"
            />
            <span class="checkin-card__month-label">{{ currentMonthLabel }}</span>
            <ElButton
              size="small"
              :icon="ArrowRight"
              @click="nextMonth"
            />
          </div>
          <ElButton
            type="primary"
            :loading="checking"
            :disabled="!canCheckin"
            @click="handleCheckin"
          >
            {{ canCheckin ? t('profile.checkinNow') : t('profile.checkedInToday') }}
          </ElButton>
        </div>

        <!-- 签到统计 -->
        <div class="checkin-card__stats">
          <div class="checkin-card__stat">
            <span class="checkin-card__stat-value">{{ status?.stats?.totalCheckins ?? 0 }}</span>
            <span class="checkin-card__stat-label">{{ t('profile.totalCheckins') }}</span>
          </div>
          <div class="checkin-card__stat">
            <span class="checkin-card__stat-value">{{ formatQuotaShort(status?.stats?.totalQuota ?? 0) }}</span>
            <span class="checkin-card__stat-label">{{ t('profile.totalAwarded') }}</span>
          </div>
        </div>

        <!-- 日历 -->
        <div class="checkin-card__calendar">
          <div class="checkin-card__weekdays">
            <span
              v-for="day in weekdays"
              :key="day"
              class="checkin-card__weekday"
            >{{ day }}</span>
          </div>
          <div class="checkin-card__days">
            <div
              v-for="day in calendarDays"
              :key="day.key"
              class="checkin-card__day"
              :class="{
                'checkin-card__day--other': !day.currentMonth,
                'checkin-card__day--today': day.isToday,
                'checkin-card__day--checked': day.isChecked,
              }"
            >
              <span class="checkin-card__day-num">{{ day.day }}</span>
              <ElIcon
                v-if="day.isChecked"
                :size="12"
                class="checkin-card__day-icon"
              >
                <Check />
              </ElIcon>
            </div>
          </div>
        </div>
      </template>
    </div>
  </ElCard>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Calendar, ArrowLeft, ArrowRight, Check, Clock } from '@element-plus/icons-vue'
import { useCheckin } from '@/composables/profile/useCheckin'
import { formatQuotaShort } from '@/utils/wallet/format'

const { t } = useI18n()
const { status, loading, checking, featureDisabled, currentMonth, fetchStatus, checkin, changeMonth } =
  useCheckin()

const weekdays = computed(() => [
  t('profile.weekdays.sun'),
  t('profile.weekdays.mon'),
  t('profile.weekdays.tue'),
  t('profile.weekdays.wed'),
  t('profile.weekdays.thu'),
  t('profile.weekdays.fri'),
  t('profile.weekdays.sat'),
])

const currentMonthLabel = computed(() => {
  const [year, month] = currentMonth.value.split('-')
  return `${year}.${month}`
})

const canCheckin = computed(() => !status.value?.stats?.checkedInToday)

// 日历天数计算
const calendarDays = computed(() => {
  const [year, month] = currentMonth.value.split('-').map(Number)
  const firstDay = new Date(year, month - 1, 1)
  const lastDay = new Date(year, month, 0)
  const startWeekday = firstDay.getDay()
  const daysInMonth = lastDay.getDate()
  const prevMonthLastDay = new Date(year, month - 1, 0).getDate()
  const today = new Date()
  const todayStr = formatDate(today)

  const checkedDates = new Set(
    (status.value?.stats?.records ?? []).map((r: { checkinDate: string; quotaAwarded: number }) => r.checkinDate)
  )

  const days: Array<{
    key: string
    day: number
    currentMonth: boolean
    isToday: boolean
    isChecked: boolean
  }> = []

  // 上月填充
  for (let i = startWeekday - 1; i >= 0; i--) {
    const day = prevMonthLastDay - i
    days.push({
      key: `prev-${day}`,
      day,
      currentMonth: false,
      isToday: false,
      isChecked: false,
    })
  }

  // 当月
  for (let day = 1; day <= daysInMonth; day++) {
    const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    days.push({
      key: `cur-${day}`,
      day,
      currentMonth: true,
      isToday: dateStr === todayStr,
      isChecked: checkedDates.has(dateStr),
    })
  }

  // 下月填充至 6 行
  const remaining = 42 - days.length
  for (let day = 1; day <= remaining; day++) {
    days.push({
      key: `next-${day}`,
      day,
      currentMonth: false,
      isToday: false,
      isChecked: false,
    })
  }

  return days
})

function formatDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function prevMonth(): void {
  const [year, month] = currentMonth.value.split('-').map(Number)
  const prev = new Date(year, month - 2, 1)
  const newMonth = `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}`
  changeMonth(newMonth)
}

function nextMonth(): void {
  const [year, month] = currentMonth.value.split('-').map(Number)
  const next = new Date(year, month, 1)
  const newMonth = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`
  changeMonth(newMonth)
}

async function handleCheckin(): Promise<void> {
  await checkin()
  ElMessage.success(t('profile.checkinSuccess'))
}

onMounted(() => {
  fetchStatus()
})
</script>

<style scoped>
.checkin-card__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-weight: 600;
}

.checkin-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
}

.checkin-card__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.checkin-card__month-nav {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;
}

.checkin-card__month-label {
  min-width: 70px;
  font-size: var(--ys-font-size-lg);
  font-weight: 600;
  text-align: center;
}

.checkin-card__stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ys-spacing-3);
}

.checkin-card__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--ys-spacing-3);
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-md);
}

.checkin-card__stat-value {
  font-family: var(--el-font-family-mono, monospace);
  font-size: var(--ys-font-size-xl);
  font-weight: 700;
}

.checkin-card__stat-label {
  margin-top: var(--ys-spacing-1);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.checkin-card__calendar {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-md);
}

.checkin-card__weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: var(--el-fill-color-light);
}

.checkin-card__weekday {
  padding: var(--ys-spacing-2) 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
  text-align: center;
}

.checkin-card__days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.checkin-card__day {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: var(--ys-spacing-2) 0;
  border-right: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.checkin-card__day:nth-child(7n) {
  border-right: none;
}

.checkin-card__day--other {
  color: var(--el-text-color-placeholder);
}

.checkin-card__day--today {
  background: var(--el-color-primary-light-9);
}

.checkin-card__day-num {
  font-size: var(--ys-font-size-sm);
}

.checkin-card__day--today .checkin-card__day-num {
  font-weight: 700;
  color: var(--el-color-primary);
}

.checkin-card__day--checked {
  background: var(--el-color-success-light-9);
}

.checkin-card__day--checked .checkin-card__day-num {
  color: var(--el-color-success);
}

.checkin-card__day-icon {
  color: var(--el-color-success);
}

.checkin-card__disabled {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: center;
  padding: var(--ys-spacing-8) var(--ys-spacing-4);
  text-align: center;
}

.checkin-card__disabled-icon {
  color: var(--el-text-color-placeholder);
}

.checkin-card__disabled-text {
  margin: 0;
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}
</style>
