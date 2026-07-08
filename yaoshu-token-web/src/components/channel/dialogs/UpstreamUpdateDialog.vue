<script setup lang="ts">
/**
 * 上游模型更新对话框：双 Tab（add/remove）+ 搜索 + 部分提交确认。
 *
 * 纯展示组件，用户选择后 emit confirm({addModels, removeModels})。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import StatusBadge from '@/components/StatusBadge.vue'

const props = defineProps<{
  modelValue: boolean
  addModels: readonly string[]
  removeModels: readonly string[]
  preferredTab: 'add' | 'remove'
  confirmLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', data: { addModels: string[]; removeModels: string[] }): void
  (e: 'cancel'): void
}>()

const { t } = useI18n()

const activeTab = ref<'add' | 'remove'>(props.preferredTab)
const searchAdd = ref('')
const searchRemove = ref('')
const selectedAdd = ref<Set<string>>(new Set(props.addModels))
const selectedRemove = ref<Set<string>>(new Set(props.removeModels))
const partialConfirmOpen = ref(false)

// props 变化时同步初始状态
watch(
  () => props.preferredTab,
  (val) => {
    activeTab.value = val
  }
)
watch(
  () => props.addModels,
  (val) => {
    selectedAdd.value = new Set(val)
  }
)
watch(
  () => props.removeModels,
  (val) => {
    selectedRemove.value = new Set(val)
  }
)

const filteredAdd = computed(() => {
  if (!searchAdd.value.trim()) return props.addModels
  const kw = searchAdd.value.trim().toLowerCase()
  return props.addModels.filter((m) => m.toLowerCase().includes(kw))
})

const filteredRemove = computed(() => {
  if (!searchRemove.value.trim()) return props.removeModels
  const kw = searchRemove.value.trim().toLowerCase()
  return props.removeModels.filter((m) => m.toLowerCase().includes(kw))
})

function toggleModel(
  model: string,
  set: Set<string>,
  setter: (s: Set<string>) => void
): void {
  const next = new Set(set)
  if (next.has(model)) next.delete(model)
  else next.add(model)
  setter(next)
}

function toggleAllVisible(
  models: readonly string[],
  set: Set<string>,
  setter: (s: Set<string>) => void
): void {
  const allSelected = models.every((m) => set.has(m))
  const next = new Set(set)
  if (allSelected) {
    models.forEach((m) => next.delete(m))
  } else {
    models.forEach((m) => next.add(m))
  }
  setter(next)
}

function handleConfirm(): void {
  const hasAdd = props.addModels.length > 0
  const hasRemove = props.removeModels.length > 0
  const selectedAddArr = Array.from(selectedAdd.value)
  const selectedRemoveArr = Array.from(selectedRemove.value)
  const anyAdd = selectedAddArr.length > 0
  const anyRemove = selectedRemoveArr.length > 0

  // 双方都有但只选了一方 → 部分确认
  if (hasAdd && hasRemove && anyAdd !== anyRemove) {
    partialConfirmOpen.value = true
    return
  }

  emit('confirm', {
    addModels: selectedAddArr,
    removeModels: selectedRemoveArr
  })
}

function handlePartialConfirm(): void {
  partialConfirmOpen.value = false
  emit('confirm', {
    addModels: Array.from(selectedAdd.value),
    removeModels: Array.from(selectedRemove.value)
  })
}

function handleCancel(): void {
  emit('cancel')
  emit('update:modelValue', false)
}

const confirmDisabled = computed(
  () =>
    props.confirmLoading ||
    (props.addModels.length === 0 && props.removeModels.length === 0)
)
</script>

<template>
  <div>
    <ElDialog
      :model-value="modelValue"
      :title="t('channel.upstream.title')"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
      @update:model-value="(v: boolean) => !v && handleCancel()"
    >
      <div class="upstream-update">
        <p class="upstream-update__hint">
          {{ t('channel.upstream.selectHint') }}
        </p>

        <ElTabs v-model="activeTab">
          <!-- Add Models Tab -->
          <ElTabPane name="add">
            <template #label>
              <span class="upstream-update__tab-label">
                {{ t('channel.upstream.addModels') }}
                <StatusBadge
                  :label="`${selectedAdd.size}/${addModels.length}`"
                  variant="neutral"
                />
              </span>
            </template>

            <div class="upstream-update__pane">
              <ElInput
                v-model="searchAdd"
                :placeholder="t('channel.upstream.searchModels')"
                clearable
              >
                <template #prefix>
                  <i class="i-ep-search" />
                </template>
              </ElInput>

              <div
                v-if="filteredAdd.length > 0"
                class="upstream-update__select-all"
              >
                <ElCheckbox
                  :model-value="filteredAdd.every((m) => selectedAdd.has(m))"
                  @change="toggleAllVisible(filteredAdd, selectedAdd, (v) => (selectedAdd = v))"
                >
                  {{ t('channel.upstream.selectAllVisible') }}
                </ElCheckbox>
              </div>

              <div class="upstream-update__list">
                <template v-if="filteredAdd.length > 0">
                  <label
                    v-for="model in filteredAdd"
                    :key="model"
                    class="upstream-update__model-item"
                  >
                    <ElCheckbox
                      :model-value="selectedAdd.has(model)"
                      @change="toggleModel(model, selectedAdd, (v) => (selectedAdd = v))"
                    />
                    <span class="upstream-update__model-name">{{ model }}</span>
                  </label>
                </template>
                <p
                  v-else
                  class="upstream-update__empty"
                >
                  {{ addModels.length === 0 ? t('channel.upstream.noModelsToAdd') : t('channel.upstream.noMatch') }}
                </p>
              </div>
            </div>
          </ElTabPane>

          <!-- Remove Models Tab -->
          <ElTabPane name="remove">
            <template #label>
              <span class="upstream-update__tab-label">
                {{ t('channel.upstream.removeModels') }}
                <StatusBadge
                  :label="`${selectedRemove.size}/${removeModels.length}`"
                  variant="neutral"
                />
              </span>
            </template>

            <div class="upstream-update__pane">
              <ElInput
                v-model="searchRemove"
                :placeholder="t('channel.upstream.searchModels')"
                clearable
              >
                <template #prefix>
                  <i class="i-ep-search" />
                </template>
              </ElInput>

              <div
                v-if="filteredRemove.length > 0"
                class="upstream-update__select-all"
              >
                <ElCheckbox
                  :model-value="filteredRemove.every((m) => selectedRemove.has(m))"
                  @change="toggleAllVisible(filteredRemove, selectedRemove, (v) => (selectedRemove = v))"
                >
                  {{ t('channel.upstream.selectAllVisible') }}
                </ElCheckbox>
              </div>

              <div class="upstream-update__list">
                <template v-if="filteredRemove.length > 0">
                  <label
                    v-for="model in filteredRemove"
                    :key="model"
                    class="upstream-update__model-item"
                  >
                    <ElCheckbox
                      :model-value="selectedRemove.has(model)"
                      @change="toggleModel(model, selectedRemove, (v) => (selectedRemove = v))"
                    />
                    <span class="upstream-update__model-name">{{ model }}</span>
                  </label>
                </template>
                <p
                  v-else
                  class="upstream-update__empty"
                >
                  {{ removeModels.length === 0 ? t('channel.upstream.noModelsToRemove') : t('channel.upstream.noMatch') }}
                </p>
              </div>
            </div>
          </ElTabPane>
        </ElTabs>
      </div>

      <template #footer>
        <ElButton @click="handleCancel">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          :loading="confirmLoading"
          :disabled="confirmDisabled"
          @click="handleConfirm"
        >
          {{ t('common.confirm') }}
        </ElButton>
      </template>
    </ElDialog>

    <!-- 部分提交确认 -->
    <ElDialog
      v-model="partialConfirmOpen"
      :title="t('channel.upstream.partialTitle')"
      width="420px"
      append-to-body
    >
      <p>{{ t('channel.upstream.partialDesc') }}</p>
      <template #footer>
        <ElButton @click="partialConfirmOpen = false">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          @click="handlePartialConfirm"
        >
          {{ t('common.confirm') }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.upstream-update {
  &__hint {
    margin: 0 0 var(--ys-spacing-3);
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__tab-label {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__pane {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__select-all {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__list {
    max-height: 280px;
    padding: var(--ys-spacing-1);
    overflow-y: auto;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
  }

  &__model-item {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    padding: var(--ys-spacing-1) var(--ys-spacing-2);
    cursor: pointer;
    border-radius: var(--el-border-radius-small);
    transition: background-color 0.2s;

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__model-name {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--el-font-size-small);
    white-space: nowrap;
  }

  &__empty {
    margin: var(--ys-spacing-8) 0;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}
</style>
