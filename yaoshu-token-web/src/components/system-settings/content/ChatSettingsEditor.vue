<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'

interface ChatEntry {
  name: string
  url: string
}

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
const { t } = useI18n()

const activeTab = ref<'visual' | 'json'>('visual')
const searchText = ref('')
const dialogOpen = ref(false)
const editingEntry = ref<ChatEntry | null>(null)
const inputName = ref('')
const inputUrl = ref('')

const chats = computed<ChatEntry[]>(() => {
  try {
    const parsed = JSON.parse(props.modelValue || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item): item is Record<string, string> =>
        typeof item === 'object' && item !== null && !Array.isArray(item) && Object.keys(item).length === 1,
      )
      .map((item) => {
        const [name, url] = Object.entries(item)[0]
        return { name, url }
      })
  } catch {
    return []
  }
})

const filteredChats = computed(() => {
  if (!searchText.value.trim()) return chats.value
  const kw = searchText.value.trim().toLowerCase()
  return chats.value.filter(
    (c) => c.name.toLowerCase().includes(kw) || c.url.toLowerCase().includes(kw),
  )
})

const jsonText = ref('')
watch(
  () => props.modelValue,
  (val) => { jsonText.value = val || '[]' },
  { immediate: true },
)

function syncToJson(entries: ChatEntry[]) {
  const arr = entries.map((e) => ({ [e.name]: e.url }))
  emit('update:modelValue', JSON.stringify(arr, null, 2))
}

function handleAdd() {
  editingEntry.value = null
  inputName.value = ''
  inputUrl.value = ''
  dialogOpen.value = true
}

function handleEdit(entry: ChatEntry) {
  editingEntry.value = { ...entry }
  inputName.value = entry.name
  inputUrl.value = entry.url
  dialogOpen.value = true
}

function handleDelete(name: string) {
  syncToJson(chats.value.filter((c) => c.name !== name))
}

function handleSave() {
  if (!inputName.value.trim() || !inputUrl.value.trim()) return
  const entry: ChatEntry = { name: inputName.value.trim(), url: inputUrl.value.trim() }
  if (editingEntry.value) {
    const updated = chats.value.map((c) => (c.name === editingEntry.value!.name ? entry : c))
    syncToJson(updated)
  } else {
    syncToJson([...chats.value, entry])
  }
  dialogOpen.value = false
}

function syncJsonToModel() {
  try {
    JSON.parse(jsonText.value || '[]')
    emit('update:modelValue', jsonText.value)
  } catch {
    // 无效 JSON 不同步
  }
}
</script>

<template>
  <div class="chat-editor">
    <ElTabs v-model="activeTab">
      <ElTabPane
        :label="t('systemSettings.content.chatVisual')"
        name="visual"
      >
        <div class="chat-editor__toolbar">
          <ElInput
            v-model="searchText"
            :placeholder="t('systemSettings.content.chatSearch')"
            :prefix-icon="Search"
            clearable
            size="small"
            style="width: 240px"
          />
          <ElButton
            type="primary"
            size="small"
            :icon="Plus"
            @click="handleAdd"
          >
            {{ t('systemSettings.content.chatAdd') }}
          </ElButton>
        </div>

        <ElEmpty
          v-if="filteredChats.length === 0"
          :description="searchText ? t('systemSettings.content.chatNoResults') : t('systemSettings.content.chatEmpty')"
          :image-size="60"
        />

        <ElTable
          v-else
          :data="filteredChats"
          size="small"
          max-height="360"
        >
          <ElTableColumn
            :label="t('systemSettings.content.chatColumnName')"
            prop="name"
            min-width="160"
          />
          <ElTableColumn
            :label="t('systemSettings.content.chatColumnUrl')"
            prop="url"
            min-width="240"
            show-overflow-tooltip
          />
          <ElTableColumn
            :label="t('common.actions')"
            width="120"
            fixed="right"
          >
            <template #default="{ row }">
              <ElButton
                size="small"
                text
                type="primary"
                :icon="Edit"
                @click="handleEdit(row as ChatEntry)"
              />
              <ElButton
                size="small"
                text
                type="danger"
                :icon="Delete"
                @click="handleDelete((row as ChatEntry).name)"
              />
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>

      <ElTabPane
        :label="t('systemSettings.content.chatJson')"
        name="json"
      >
        <ElInput
          v-model="jsonText"
          type="textarea"
          :rows="12"
          :placeholder="`[{ &quot;ChatGPT&quot;: &quot;https://chat.openai.com&quot; }]`"
          @blur="syncJsonToModel"
        />
        <p class="chat-editor__hint">
          {{ t('systemSettings.content.chatJsonHint') }}
        </p>
      </ElTabPane>
    </ElTabs>

    <ElDialog
      v-model="dialogOpen"
      :title="editingEntry ? t('systemSettings.content.chatEdit') : t('systemSettings.content.chatAdd')"
      width="460px"
    >
      <ElForm label-width="100px">
        <ElFormItem :label="t('systemSettings.content.chatColumnName')">
          <ElInput
            v-model="inputName"
            placeholder="ChatGPT"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.content.chatColumnUrl')">
          <ElInput
            v-model="inputUrl"
            placeholder="https://chat.openai.com"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogOpen = false">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          @click="handleSave"
        >
          {{ t('common.save') }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.chat-editor {
  &__toolbar {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--ys-spacing-3);
  }

  &__hint {
    margin-top: var(--ys-spacing-2);
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }
}
</style>
