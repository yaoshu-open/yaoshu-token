import { EXCLUDED_GROUPS, QUOTA_TYPE_VALUES } from '@/api/pricing/constants'
import type { PricingModel } from '@/api/pricing/types'

/** 获取模型可用的分组列表 */
export function getAvailableGroups(
  model: PricingModel,
  usableGroup: Record<string, { desc: string; ratio: number }>
): string[] {
  const modelEnableGroups = Array.isArray(model.enableGroup)
    ? model.enableGroup
    : []
  return Object.keys(usableGroup)
    .filter((g) => !EXCLUDED_GROUPS.includes(g))
    .filter((g) => modelEnableGroups.includes(g))
}

/** 替换端点路径中的模型占位符 */
export function replaceModelInPath(path: string, modelName: string): string {
  return path.replace(/\{model\}/g, modelName)
}

/** 判断是否为 token 计费模型 */
export function isTokenBasedModel(model: PricingModel): boolean {
  return model.quotaType === QUOTA_TYPE_VALUES.TOKEN
}
