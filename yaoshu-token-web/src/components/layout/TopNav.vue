<script setup lang="ts">
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton } from 'element-plus'
import type { TopNavLink } from './types'
import NavLinkItem from './NavLinkItem.vue'

interface Props {
  links: TopNavLink[]
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  className: ''
})
</script>

<template>
  <div class="top-nav">
    <!-- 移动端下拉：lg 断点以下 -->
    <div class="top-nav--mobile lg:hidden">
      <ElDropdown trigger="click">
        <ElButton
          size="small"
          variant="outline"
          class="top-nav__trigger"
        >
          <i class="i-ep-menu" />
        </ElButton>
        <template #dropdown>
          <ElDropdownMenu>
            <ElDropdownItem
              v-for="(link, idx) in links"
              :key="`${link.title}-${link.href}-${idx}`"
              :disabled="link.disabled"
            >
              <NavLinkItem :link="link" />
            </ElDropdownItem>
          </ElDropdownMenu>
        </template>
      </ElDropdown>
    </div>

    <!-- 桌面端横排：lg 断点以上 -->
    <nav
      class="top-nav--desktop"
      :class="className"
    >
      <NavLinkItem
        v-for="(link, idx) in links"
        :key="`${link.title}-${link.href}-${idx}`"
        :link="link"
        class="top-nav__item"
      />
    </nav>
  </div>
</template>

<style scoped lang="scss">
.top-nav {
  display: flex;
  align-items: center;

  &--desktop {
    display: none;
    gap: var(--ys-spacing-4);

    @media (width >= 1024px) {
      display: flex;
    }
  }

  &__trigger {
    width: 28px;
    height: 28px;
    padding: 0;
  }

  &__item {
    font-size: var(--el-font-size-base);
    font-weight: 500;
    transition: color 0.2s;

    &:hover {
      color: var(--el-color-primary);
    }

    &--active {
      color: var(--el-text-color-primary);
    }
  }
}
</style>
