<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { NavCollapsible, NavChatPresets, NavLink, NavGroup as NavGroupProps } from './types'
import { checkIsActive } from './lib/url-utils'

interface Props {
  id?: string
  title: string
  items: NavGroupProps['items']
}

const props = defineProps<Props>()
const route = useRoute()
const router = useRouter()
const collapsedOpenMap = ref<Record<string, boolean>>({})

watch(
  () => route.path,
  () => {
    for (const item of props.items) {
      if ('items' in item && item.items) {
        const active = checkIsActive(route.path, item)
        if (active) {
          collapsedOpenMap.value[item.title] = true
        }
      }
    }
  },
  { immediate: true }
)

function navigate(url: string) {
  router.push(url)
}

function toggleCollapsible(title: string) {
  collapsedOpenMap.value[title] = !collapsedOpenMap.value[title]
}

// 类型守卫：判定 nav 项变体
function isCollapsible(item: NavGroupProps['items'][number]): item is NavCollapsible {
  return 'items' in item && !!item.items
}
function isChatPresets(item: NavGroupProps['items'][number]): item is NavChatPresets {
  return 'type' in item && item.type === 'chat-presets'
}
function isLink(item: NavGroupProps['items'][number]): item is NavLink {
  return !isCollapsible(item) && !isChatPresets(item)
}
</script>

<template>
  <div class="nav-group">
    <div class="nav-group__label">
      {{ title }}
    </div>
    <ul class="nav-group__menu">
      <template
        v-for="(item, idx) in items"
        :key="`${item.title}-${item.url || item.type}-${idx}`"
      >
        <!-- chat-presets 类型：跳转至 /chat2link，触发 chat 预设重定向逻辑 -->
        <li
          v-if="isChatPresets(item)"
          class="nav-group__item nav-group__item--chat"
        >
          <button
            type="button"
            class="nav-group__link"
            @click="navigate(item.url || '/chat2link')"
          >
            <i
              v-if="item.icon"
              :class="typeof item.icon === 'string' ? item.icon : ''"
            />
            <span class="nav-group__title">{{ item.title }}</span>
            <span
              v-if="item.badge"
              class="nav-group__badge"
            >{{ item.badge }}</span>
          </button>
        </li>

        <!-- 普通 link：点击 router.push -->
        <li
          v-else-if="isLink(item)"
          class="nav-group__item"
        >
          <button
            type="button"
            class="nav-group__link"
            :class="{ 'nav-group__link--active': checkIsActive(route.path, item) }"
            @click="navigate(item.url)"
          >
            <i
              v-if="item.icon"
              :class="typeof item.icon === 'string' ? item.icon : ''"
            />
            <span class="nav-group__title">{{ item.title }}</span>
            <span
              v-if="item.badge"
              class="nav-group__badge"
            >{{ item.badge }}</span>
          </button>
        </li>

        <!-- collapsible 组：展开/折叠 + 子项 -->
        <li
          v-else-if="isCollapsible(item)"
          class="nav-group__item nav-group__item--collapsible"
        >
          <button
            type="button"
            class="nav-group__link"
            :class="{ 'nav-group__link--active': checkIsActive(route.path, item) }"
            @click="toggleCollapsible(item.title)"
          >
            <i
              v-if="item.icon"
              :class="typeof item.icon === 'string' ? item.icon : ''"
            />
            <span class="nav-group__title">{{ item.title }}</span>
            <i
              class="nav-group__chevron i-ep-arrow-right"
              :class="{ 'nav-group__chevron--open': collapsedOpenMap[item.title] }"
            />
          </button>
          <ul
            v-show="collapsedOpenMap[item.title]"
            class="nav-group__sub"
          >
            <li
              v-for="sub in item.items"
              :key="sub.title"
              class="nav-group__sub-item"
            >
              <button
                type="button"
                class="nav-group__sub-link"
                :class="{ 'nav-group__sub-link--active': checkIsActive(route.path, sub) }"
                @click="navigate(sub.url)"
              >
                <i
                  v-if="sub.icon"
                  :class="typeof sub.icon === 'string' ? sub.icon : ''"
                />
                <span>{{ sub.title }}</span>
              </button>
            </li>
          </ul>
        </li>
      </template>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.nav-group {
  padding: var(--ys-spacing-1) var(--ys-spacing-2);

  &__label {
    padding: var(--ys-spacing-2) var(--ys-spacing-3) var(--ys-spacing-1);
    font-size: 11px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  &__menu {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__item {
    margin: 3px 0;
  }

  &__link {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    width: 100%;
    padding: 8px var(--ys-spacing-3);
    color: var(--el-text-color-regular);
    text-align: left;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--ys-radius-base);
    transition: background-color 0.2s, color 0.2s, box-shadow 0.2s;

    i {
      flex-shrink: 0;
      width: 18px;
      height: 18px;
      font-size: 18px;
      color: var(--el-text-color-secondary);
      transition: color 0.2s;
    }

    &:hover {
      background: var(--ys-bg-brand-soft);
      box-shadow: inset 0 0 0 1px var(--el-color-primary-light-8);

      i {
        color: var(--el-text-color-primary);
      }
    }

    &--active {
      font-weight: 500;
      color: var(--ys-color-primary);
      background-color: var(--ys-bg-brand-soft);
      background-image: linear-gradient(
        90deg,
        var(--ys-bg-brand-soft) 0%,
        transparent 100%
      );

      i {
        color: var(--ys-color-primary);
      }
    }
  }

  &__title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--el-font-size-base);
    white-space: nowrap;
  }

  &__badge {
    flex-shrink: 0;
    padding: 0 var(--ys-spacing-1);
    font-size: var(--el-font-size-extra-small);
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    border-radius: var(--el-border-radius-small);
  }

  &__chevron {
    flex-shrink: 0;
    font-size: var(--ys-font-size-xs);
    transition: transform 0.2s;

    &--open {
      transform: rotate(90deg);
    }
  }

  &__sub {
    padding: 0 0 0 var(--ys-spacing-6);
    margin: 0;
    list-style: none;
  }

  &__sub-link {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    width: 100%;
    padding: var(--ys-spacing-1) var(--ys-spacing-2);
    font-size: var(--el-font-size-base);
    color: var(--el-text-color-regular);
    text-align: left;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--ys-radius-base);
    transition: background-color 0.2s, color 0.2s;

    i {
      flex-shrink: 0;
      width: 18px;
      height: 18px;
      font-size: 18px;
      color: var(--el-text-color-secondary);
      transition: color 0.2s;
    }

    &:hover {
      background: var(--ys-bg-brand-soft);

      i {
        color: var(--el-text-color-primary);
      }
    }

    &--active {
      color: var(--ys-color-primary);
      background: var(--ys-bg-brand-soft);

      i {
        color: var(--ys-color-primary);
      }
    }
  }
}
</style>
