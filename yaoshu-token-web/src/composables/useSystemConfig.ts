import { storeToRefs } from 'pinia'
import { useSystemConfigStore } from '@/store/modules/system-config'

/**
 *
 * 仅做读侧派生：把 system-config store 的 ref 暴露为 storeToRefs 形式给消费方解构。
 * 写侧（setConfig/fetch）由调用方直接拿 store 实例使用，避免双入口。
 */
export function useSystemConfig() {
  const store = useSystemConfigStore()
  const refs = storeToRefs(store)

  return {
    systemName: refs.systemName,
    logo: refs.logo,
    footerHtml: refs.footerHtml,
    demoSiteEnabled: refs.demoSiteEnabled,
    displayTokenStatEnabled: refs.displayTokenStatEnabled,
    currency: refs.currency,
    rawStatus: refs.rawStatus,
    isCurrencyDisplay: refs.isCurrencyDisplay,
    loading: refs.loading,
    lastError: refs.lastError,
    // action：直接透传 store 引用，调用方需用 store.fetch() 而非解构（Pinia setup store 行为）
    setConfig: store.setConfig,
    fetch: store.fetch
  }
}
