<template>
  <ElCard
    class="profile-header"
    shadow="never"
    body-style="padding: 0;"
  >
    <template v-if="loading">
      <div class="profile-header__top">
        <ElSkeleton
          :rows="0"
          animated
        >
          <template #template>
            <div class="profile-header__skeleton-top">
              <ElSkeletonItem
                variant="rect"
                style="width: 64px; height: 64px; border-radius: var(--ys-radius-xl);"
              />
              <div class="profile-header__skeleton-info">
                <ElSkeletonItem
                  variant="text"
                  style="width: 200px; height: 32px;"
                />
                <ElSkeletonItem
                  variant="text"
                  style="width: 160px; height: 16px; margin-top: var(--ys-spacing-3);"
                />
              </div>
            </div>
          </template>
        </ElSkeleton>
      </div>
      <ElSkeleton
        :rows="3"
        animated
        class="profile-header__skeleton-stats"
      />
    </template>
    <template v-else-if="profile">
      <div class="profile-header__top">
        <ElAvatar
          :size="64"
          class="profile-header__avatar"
        >
          {{ initials }}
        </ElAvatar>
        <div class="profile-header__info">
          <div class="profile-header__name-row">
            <h1 class="profile-header__name">
              {{ displayName }}
            </h1>
            <ElTag
              type="info"
              effect="plain"
              size="small"
            >
              {{ roleLabel }}
            </ElTag>
            <ElTag
              type="primary"
              effect="plain"
              size="small"
            >
              {{ t('profile.userId') }} {{ profile.id }}
            </ElTag>
          </div>
          <div class="profile-header__meta">
            <span class="profile-header__meta-item">@{{ profile.username }}</span>
            <span
              v-if="profile.email"
              class="profile-header__meta-item"
            >{{ profile.email }}</span>
            <span
              v-if="profile.group && !groupHidden"
              class="profile-header__meta-item"
            >{{ profile.group }}</span>
          </div>
        </div>
      </div>
      <div class="profile-header__stats">
        <div
          v-for="item in stats"
          :key="item.label"
          class="profile-header__stat"
        >
          <div class="profile-header__stat-label">
            <ElIcon :size="14">
              <component :is="item.icon" />
            </ElIcon>
            <span>{{ item.label }}</span>
          </div>
          <div class="profile-header__stat-value">
            {{ item.value }}
          </div>
          <div class="profile-header__stat-desc">
            {{ item.description }}
          </div>
        </div>
      </div>
    </template>
    <ElEmpty
      v-else
      :description="t('profile.noData')"
    />
  </ElCard>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Wallet, DataLine, Timer } from '@element-plus/icons-vue'
import { formatQuotaWithCurrency } from '@/utils/currency'
import { getRoleLabel } from '@/utils/roles'
import { getDisplayName, getUserInitials } from '@/utils/profile'
import { isFeatureHidden } from '@/plugins/spi/registry'
import type { UserProfile } from '@/api/profile/types'

const props = defineProps<{
  profile: UserProfile | null
  loading: boolean
}>()

const { t } = useI18n()

// PD-03：商业版无倍率/分组概念，隐藏 ProfileHeader 中的分组字段显示
const groupHidden = isFeatureHidden('group-ratio')

const displayName = computed(() => getDisplayName(props.profile))
const initials = computed(() => getUserInitials(props.profile))
// roleLabel 返回 i18n key（common/admin/root），需组合 t('roles.<key>') 渲染
const roleLabel = computed(() => {
  const key = getRoleLabel(props.profile?.role)
  return key ? t(`roles.${key}`) : ''
})

const stats = computed(() => {
  if (!props.profile) return []
  return [
    {
      label: t('profile.currentBalance'),
      value: formatQuotaWithCurrency(props.profile.quota ?? 0),
      description: t('profile.remainingQuota'),
      icon: Wallet,
    },
    {
      label: t('profile.totalUsage'),
      value: formatQuotaWithCurrency(props.profile.usedQuota ?? 0),
      description: t('profile.totalConsumed'),
      icon: DataLine,
    },
    {
      label: t('profile.apiRequests'),
      value: String(props.profile.requestCount ?? 0),
      description: t('profile.totalRequests'),
      icon: Timer,
    },
  ]
})
</script>

<style scoped>
.profile-header__top {
  display: flex;
  gap: var(--ys-spacing-4);
  align-items: center;
  padding: var(--ys-spacing-5);
}

.profile-header__avatar {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
}

.profile-header__info {
  flex: 1;
  min-width: 0;
}

.profile-header__name-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.profile-header__name {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--ys-font-size-2xl);
  font-weight: 600;
  white-space: nowrap;
}

.profile-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-4);
  align-items: center;
  margin-top: var(--ys-spacing-2);
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}

.profile-header__meta-item {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-header__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--el-border-color);
}

.profile-header__stat {
  padding: var(--ys-spacing-4) var(--ys-spacing-5);
  border-right: 1px solid var(--el-border-color);
}

.profile-header__stat:last-child {
  border-right: none;
}

.profile-header__stat-label {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.profile-header__stat-value {
  margin-top: var(--ys-spacing-2);
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--el-font-family-mono, monospace);
  font-size: 22px;
  font-weight: 700;
  white-space: nowrap;
}

.profile-header__stat-desc {
  margin-top: var(--ys-spacing-1);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.profile-header__skeleton-top {
  display: flex;
  gap: var(--ys-spacing-4);
  align-items: center;
  padding: var(--ys-spacing-5);
}

.profile-header__skeleton-info {
  flex: 1;
}

.profile-header__skeleton-stats {
  padding: var(--ys-spacing-5);
}

@media (width <= 640px) {
  .profile-header__top {
    flex-direction: column;
    text-align: center;
  }

  .profile-header__meta {
    justify-content: center;
  }

  .profile-header__stat-value {
    font-size: 18px;
  }
}
</style>
