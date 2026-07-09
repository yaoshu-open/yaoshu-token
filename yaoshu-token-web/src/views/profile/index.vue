<script setup lang="ts">
// 个人资料页：M2 profile 模块全量编辑功能
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useProfile } from '@/composables/profile/useProfile'
import { isFeatureHidden } from '@/plugins/spi/registry'
import ProfileHeader from '@/components/profile/ProfileHeader.vue'
import ProfileSettingsCard from '@/components/profile/ProfileSettingsCard.vue'
import ProfileSecurityCard from '@/components/profile/ProfileSecurityCard.vue'
import TwoFACard from '@/components/profile/TwoFACard.vue'
import PasskeyCard from '@/components/profile/PasskeyCard.vue'
import LanguagePreferencesCard from '@/components/profile/LanguagePreferencesCard.vue'
import SidebarModulesCard from '@/components/profile/SidebarModulesCard.vue'
import CheckinCalendarCard from '@/components/profile/CheckinCalendarCard.vue'

const { t } = useI18n()
const { profile, loading, fetchProfile, refreshProfile } = useProfile()

// SPI 功能开关：通过 SPI 扩展点隐藏 PasskeyCard
const passkeyHidden = isFeatureHidden('passkey')
// SPI 功能开关：通过 SPI 扩展点隐藏侧边栏模块设置卡片
const sidebarModulesHidden = isFeatureHidden('sidebar-modules')

onMounted(() => {
  fetchProfile()
})
</script>

<template>
  <div class="profile-page">
    <div class="profile-page__hero">
      <h1 class="profile-page__title">
        {{ t('nav.profile') }}
      </h1>
    </div>

    <ProfileHeader
      :profile="profile"
      :loading="loading"
    />

    <div class="profile-page__grid">
      <div class="profile-page__main">
        <ProfileSettingsCard
          :profile="profile"
          :loading="loading"
          @profile-update="refreshProfile"
        />
        <ProfileSecurityCard
          :profile="profile"
          @update="refreshProfile"
        />
        <TwoFACard />
        <PasskeyCard v-if="!passkeyHidden" />
      </div>

      <div class="profile-page__side">
        <LanguagePreferencesCard
          :profile="profile"
          @profile-update="refreshProfile"
        />
        <SidebarModulesCard v-if="!sidebarModulesHidden" :profile="profile" />
        <CheckinCalendarCard />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.profile-page {
  display: flex;
  flex-direction: column;
  gap: $spacing-6;
  max-width: 1200px;
  padding: $spacing-8 $spacing-6;
  margin: 0 auto;

  &__hero {
    display: flex;
    flex-direction: column;
    gap: $spacing-2;
  }

  &__title {
    margin: 0;
    font-size: $font-size-2xl;
    font-weight: $font-weight-semibold;
    letter-spacing: -0.025em;
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 380px;
    gap: $spacing-6;
    align-items: start;
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: $spacing-6;
  }

  &__side {
    position: sticky;
    top: $spacing-6;
    display: flex;
    flex-direction: column;
    gap: $spacing-6;
  }
}

@media (width <= 1024px) {
  .profile-page {
    &__grid {
      grid-template-columns: 1fr;
    }

    &__side {
      position: static;
    }
  }
}

@media (width <= 640px) {
  .profile-page {
    gap: $spacing-4;
    padding: $spacing-4 $spacing-3;

    &__title {
      font-size: $font-size-xl;
    }
  }
}
</style>
