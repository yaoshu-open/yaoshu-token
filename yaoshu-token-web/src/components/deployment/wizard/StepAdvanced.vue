<script setup lang="ts">
/**
 * Step 4: 高级选项（环境变量 + entrypoint + args + traffic_port + registry）。
 * 所有字段可选，无强制校验。
 */
import { useI18n } from 'vue-i18n'
import type { DeploymentFormState, EnvVarItem } from '@/composables/deployment/useDeploymentForm'

interface Props {
  form: DeploymentFormState
}

const props = defineProps<Props>()
const { t } = useI18n()

function addEnvVar(list: EnvVarItem[]): void {
  list.push({ key: '', value: '' })
}

function removeEnvVar(list: EnvVarItem[], index: number): void {
  list.splice(index, 1)
}
</script>

<template>
  <el-form
    label-position="top"
    class="step-advanced"
  >
    <el-form-item :label="t('deployment.create.advanced.envVars')">
      <div class="env-list">
        <div
          v-for="(item, idx) in props.form.env_variables"
          :key="idx"
          class="env-row"
        >
          <el-input
            v-model="item.key"
            :placeholder="t('deployment.create.advanced.keyPlaceholder')"
            class="env-key"
          />
          <el-input
            v-model="item.value"
            :placeholder="t('deployment.create.advanced.valuePlaceholder')"
            class="env-value"
          />
          <el-button
            type="danger"
            text
            @click="removeEnvVar(props.form.env_variables, idx)"
          >
            <i class="i-ep-delete" />
            {{ t('deployment.create.advanced.removeEnvVar') }}
          </el-button>
        </div>
      </div>
      <el-button
        type="primary"
        text
        @click="addEnvVar(props.form.env_variables)"
      >
        <i class="i-ep-plus" />
        {{ t('deployment.create.advanced.addEnvVar') }}
      </el-button>
    </el-form-item>

    <el-form-item :label="t('deployment.create.advanced.secretEnvVars')">
      <div class="env-list">
        <div
          v-for="(item, idx) in props.form.secret_env_variables"
          :key="idx"
          class="env-row"
        >
          <el-input
            v-model="item.key"
            :placeholder="t('deployment.create.advanced.keyPlaceholder')"
            class="env-key"
          />
          <el-input
            v-model="item.value"
            :placeholder="t('deployment.create.advanced.valuePlaceholder')"
            type="password"
            show-password
            class="env-value"
          />
          <el-button
            type="danger"
            text
            @click="removeEnvVar(props.form.secret_env_variables, idx)"
          >
            <i class="i-ep-delete" />
            {{ t('deployment.create.advanced.removeEnvVar') }}
          </el-button>
        </div>
      </div>
      <el-button
        type="primary"
        text
        @click="addEnvVar(props.form.secret_env_variables)"
      >
        <i class="i-ep-plus" />
        {{ t('deployment.create.advanced.addEnvVar') }}
      </el-button>
    </el-form-item>

    <el-form-item :label="t('deployment.create.advanced.entrypoint')">
      <el-input
        v-model="props.form.entrypoint"
        :placeholder="t('deployment.create.advanced.entrypointPlaceholder')"
        clearable
      />
    </el-form-item>

    <el-form-item :label="t('deployment.create.advanced.args')">
      <el-input
        v-model="props.form.args"
        :placeholder="t('deployment.create.advanced.argsPlaceholder')"
        clearable
      />
    </el-form-item>

    <el-form-item :label="t('deployment.create.advanced.trafficPort')">
      <el-input-number
        v-model="props.form.traffic_port"
        :min="1"
        :max="65535"
        :step="1"
      />
    </el-form-item>

    <el-form-item :label="t('deployment.create.advanced.registryUsername')">
      <el-input
        v-model="props.form.registry_username"
        clearable
      />
    </el-form-item>

    <el-form-item :label="t('deployment.create.advanced.registrySecret')">
      <el-input
        v-model="props.form.registry_secret"
        type="password"
        show-password
        clearable
      />
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.step-advanced {
  .env-list {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    width: 100%;
    margin-bottom: 8px;
  }

  .env-row {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  .env-key {
    flex: 0 0 200px;
  }

  .env-value {
    flex: 1;
  }
}
</style>
