<script setup lang="ts">
/**
 * Step 1: 基本信息（名称查重 + 镜像 + 时长）。
 * 名称查重状态由 Wizard 容器的 useNameCheck composable 管理，透传到此处显示。
 */
import { useI18n } from 'vue-i18n'
import type { DeploymentFormState } from '@/composables/deployment/useDeploymentForm'
import type { NameCheckStatus } from '@/composables/deployment/useNameCheck'

interface Props {
  form: DeploymentFormState
  nameCheckStatus: NameCheckStatus
}

const props = defineProps<Props>()
const { t } = useI18n()
</script>

<template>
  <el-form
    label-position="top"
    class="step-basic-info"
  >
    <el-form-item :label="t('deployment.create.basicInfo.name')">
      <el-input
        v-model="props.form.resource_private_name"
        :placeholder="t('deployment.create.basicInfo.namePlaceholder')"
        clearable
      >
        <template #append>
          <span
            v-if="props.nameCheckStatus === 'checking'"
            class="status-checking"
          >
            <i class="i-ep-loading" />
            {{ t('deployment.create.basicInfo.nameChecking') }}
          </span>
          <span
            v-else-if="props.nameCheckStatus === 'available'"
            class="status-available"
          >
            <i class="i-ep-circle-check-filled" />
            {{ t('deployment.create.basicInfo.nameAvailable') }}
          </span>
          <span
            v-else-if="props.nameCheckStatus === 'taken'"
            class="status-taken"
          >
            <i class="i-ep-circle-close-filled" />
            {{ t('deployment.create.basicInfo.nameTaken') }}
          </span>
        </template>
      </el-input>
      <div class="form-hint">
        {{ t('deployment.create.basicInfo.nameHint') }}
      </div>
    </el-form-item>

    <el-form-item :label="t('deployment.create.basicInfo.imageUrl')">
      <el-input
        v-model="props.form.image_url"
        :placeholder="t('deployment.create.basicInfo.imageUrlPlaceholder')"
        clearable
      />
    </el-form-item>

    <el-form-item :label="t('deployment.create.basicInfo.duration')">
      <el-input-number
        v-model="props.form.duration_hours"
        :min="1"
        :max="720"
        :step="1"
      />
      <div class="form-hint">
        {{ t('deployment.create.basicInfo.durationHint') }}
      </div>
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.step-basic-info {
  .form-hint {
    margin-top: 4px;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  .status-checking {
    color: var(--el-text-color-secondary);
  }

  .status-available {
    color: var(--el-color-success);
  }

  .status-taken {
    color: var(--el-color-danger);
  }
}
</style>
