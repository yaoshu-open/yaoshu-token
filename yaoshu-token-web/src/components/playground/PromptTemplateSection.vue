<script setup lang="ts">
/**
 * PromptTemplateSection — 左侧面板模板区。
 *
 * 保存当前配置为模板 / 点击加载模板 / 删除模板。
 * 模板数据由 usePromptTemplates 管理（localStorage 持久化）。
 */
import { useI18n } from 'vue-i18n'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Plus, Delete, Document } from '@element-plus/icons-vue'
import type { PromptTemplate } from '@/composables/playground/usePromptTemplates'

interface Props {
  templates: PromptTemplate[]
}

defineProps<Props>()
const emit = defineEmits<{
  save: [name: string]
  load: [id: string]
  delete: [id: string]
}>()

const { t } = useI18n()

async function handleSave(): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(
      t('playground.templates.savePrompt'),
      t('playground.templates.saveTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputPlaceholder: t('playground.templates.namePlaceholder')
      }
    )
    if (value?.trim()) {
      emit('save', value.trim())
      ElMessage.success(t('playground.templates.saved'))
    }
  } catch {
    // 用户取消
  }
}

async function handleDelete(id: string): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('playground.templates.deleteConfirm'),
      t('playground.templates.deleteTitle'),
      {
        type: 'warning',
        confirmButtonText: t('playground.actions.delete'),
        cancelButtonText: t('common.cancel')
      }
    )
    emit('delete', id)
    ElMessage.success(t('playground.templates.deleted'))
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="prompt-template-section">
    <el-button
      size="small"
      :icon="Plus"
      plain
      class="prompt-template-section__save-btn"
      @click="handleSave"
    >
      {{ t('playground.templates.save') }}
    </el-button>

    <div
      v-if="templates.length > 0"
      class="prompt-template-section__list"
    >
      <div
        v-for="tpl in templates"
        :key="tpl.id"
        class="prompt-template-section__item"
        @click="emit('load', tpl.id)"
      >
        <el-icon class="prompt-template-section__icon"><Document /></el-icon>
        <span class="prompt-template-section__name">{{ tpl.name }}</span>
        <button
          type="button"
          class="prompt-template-section__delete"
          :title="t('playground.actions.delete')"
          @click.stop="handleDelete(tpl.id)"
        >
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <p
      v-else
      class="prompt-template-section__empty"
    >
      {{ t('playground.templates.empty') }}
    </p>
  </div>
</template>

<style scoped lang="scss">
.prompt-template-section {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);

  &__save-btn {
    width: 100%;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
    max-height: 200px;
    overflow-y: auto;
  }

  &__item {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    padding: var(--ys-spacing-2) 10px;
    cursor: pointer;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
    transition: all 0.2s;

    &:hover {
      background: var(--el-color-primary-light-9);
      border-color: var(--el-color-primary);
    }
  }

  &__icon {
    flex-shrink: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }

  &__delete {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    padding: 0;
    color: var(--el-text-color-placeholder);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--el-border-radius-small);
    transition: all 0.2s;

    &:hover {
      color: var(--el-color-danger);
      background: var(--el-color-danger-light-9);
    }
  }

  &__empty {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
    text-align: center;
  }
}
</style>
