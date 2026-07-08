<script setup lang="ts">
import { Search, Close } from '@element-plus/icons-vue'

const props = defineProps<{
  value: string
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:value', v: string): void
  (e: 'clear'): void
}>()

function handleInput(v: string) {
  emit('update:value', v)
}
</script>

<template>
  <div class="search-bar">
    <el-input
      :model-value="value"
      :placeholder="placeholder"
      :prefix-icon="Search"
      clearable
      @update:model-value="handleInput"
      @clear="emit('clear')"
    >
      <template
        v-if="value"
        #suffix
      >
        <el-icon
          class="search-bar__clear"
          @click="emit('clear')"
        >
          <Close />
        </el-icon>
      </template>
    </el-input>
  </div>
</template>

<style scoped lang="scss">
.search-bar {
  max-width: 560px;
  margin: 0 auto;

  &__clear {
    color: var(--el-text-color-secondary);
    cursor: pointer;

    &:hover {
      color: var(--el-color-primary);
    }
  }
}
</style>
