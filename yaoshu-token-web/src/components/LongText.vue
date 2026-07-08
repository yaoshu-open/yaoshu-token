<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElTooltip } from 'element-plus'

withDefaults(
  defineProps<{
    content?: string
    contentClassName?: string
  }>(),
  {
    content: undefined,
    contentClassName: undefined,
  },
)

const textRef = ref<HTMLElement>()
const isOverflown = ref(false)

function checkOverflow(el: HTMLElement | undefined): boolean {
  if (!el) return false
  return el.offsetWidth < el.scrollWidth || el.offsetHeight < el.scrollHeight
}

onMounted(async () => {
  await nextTick()
  isOverflown.value = checkOverflow(textRef.value)
})
</script>

<template>
  <div
    v-if="!isOverflown"
    ref="textRef"
    class="long-text"
  >
    <slot>{{ content }}</slot>
  </div>

  <template v-else>
    <!-- 桌面端：Tooltip -->
    <div class="long-text long-text--desktop">
      <ElTooltip
        :content="content ?? ''"
        placement="top"
        :show-after="200"
      >
        <template #default>
          <div
            ref="textRef"
            class="long-text__trigger"
          >
            <slot>{{ content }}</slot>
          </div>
        </template>
      </ElTooltip>
    </div>
  </template>
</template>

<style scoped lang="scss">
.long-text {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &__trigger {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
