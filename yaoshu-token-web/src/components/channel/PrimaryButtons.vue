<script setup lang="ts">
/**
 * 渠道主操作按钮组。
 *
 * 职责：添加渠道 / 测试全部 / 更新余额 / 修复能力 / 删除禁用。
 */
import { Plus, Connection, Wallet, Tools, Delete, ArrowDown } from '@element-plus/icons-vue'

defineEmits<{
  (e: 'add'): void
  (e: 'test-all'): void
  (e: 'update-all-balance'): void
  (e: 'fix-abilities'): void
  (e: 'delete-disabled'): void
}>()
</script>

<template>
  <div class="channel-primary-buttons">
    <el-button
      type="primary"
      :icon="Plus"
      @click="$emit('add')"
    >
      {{ $t('channel.actions.add') }}
    </el-button>

    <el-button
      :icon="Connection"
      @click="$emit('test-all')"
    >
      {{ $t('channel.actions.testAll') }}
    </el-button>

    <el-button
      :icon="Wallet"
      @click="$emit('update-all-balance')"
    >
      {{ $t('channel.actions.updateAllBalance') }}
    </el-button>

    <el-dropdown trigger="click">
      <el-button :icon="Tools">
        {{ $t('channel.actions.moreOperations') }}
        <el-icon class="el-icon--right">
          <ArrowDown />
        </el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="$emit('fix-abilities')">
            {{ $t('channel.actions.fixAbilities') }}
          </el-dropdown-item>
          <el-dropdown-item @click="$emit('delete-disabled')">
            <el-icon><Delete /></el-icon>
            {{ $t('channel.actions.deleteDisabled') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.channel-primary-buttons {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}
</style>
