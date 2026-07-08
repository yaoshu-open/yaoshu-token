// 纯函数工具库，无框架依赖。解析聊天预设配置 + 构造第三方聊天客户端 URL

export type ChatLinkType = 'web' | 'custom-protocol' | 'fluent'

export interface ChatPreset {
  id: number
  name: string
  url: string
  type: ChatLinkType
}

export interface ResolveChatUrlParams {
  template: string
  apiKey?: string
  serverAddress: string
}

// Base64 编码（浏览器/Node 兼容）
function toBase64(str: string): string {
  const globalRef = globalThis as typeof globalThis & { Buffer?: { from(input: string, encoding: string): { toString(encoding: string): string } } }
  if (typeof globalRef.btoa === 'function') {
    return globalRef.btoa(unescape(encodeURIComponent(str)))
  }
  if (typeof globalRef.Buffer !== 'undefined') {
    return globalRef.Buffer.from(str, 'utf-8').toString('base64')
  }
  return ''
}

// 判断聊天链接类型
export function detectChatLinkType(url: string): ChatLinkType {
  if (/^https?:\/\//i.test(url)) return 'web'
  if (url.startsWith('fluent')) return 'fluent'
  return 'custom-protocol'
}

// 检测模板是否需要 API Key
export function chatLinkRequiresApiKey(url: string): boolean {
  return /\{key\}|\{cherryConfig\}|\{aionuiConfig\}|\{deepChatConfig\}/.test(url)
}

// 将原始配置解析为预设数组
// 支持格式：'[{"ChatGPT":"https://..."}]' 或 '{"name":"url"}'
export function parseChatConfig(raw: unknown): ChatPreset[] {
  if (!raw) return []

  let config: Record<string, string> | Array<Record<string, string>> | null = null

  if (typeof raw === 'string') {
    try {
      config = JSON.parse(raw)
    } catch {
      return []
    }
  } else if (typeof raw === 'object') {
    config = raw as Record<string, string> | Array<Record<string, string>>
  }

  if (!config) return []

  const entries: Array<[string, string]> = Array.isArray(config)
    ? config.flatMap((item) => Object.entries(item))
    : Object.entries(config)

  return entries
    .filter(([name, url]) => name && url && typeof url === 'string')
    .map(([name, url], index) => ({
      id: index,
      name,
      url,
      type: detectChatLinkType(url)
    }))
}

// 核心：将模板 URL 中的占位符替换为实际值
export function resolveChatUrl({ template, apiKey, serverAddress }: ResolveChatUrlParams): string {
  const address = encodeURIComponent(serverAddress)

  if (template.includes('{cherryConfig}')) {
    const payload = JSON.stringify({ id: 'yaoshu-token', baseUrl: serverAddress, apiKey: apiKey ?? '' })
    return template.replace('{cherryConfig}', encodeURIComponent(toBase64(payload)))
  }

  if (template.includes('{aionuiConfig}')) {
    const payload = JSON.stringify({ platform: 'yaoshu-token', baseUrl: serverAddress, apiKey: apiKey ?? '' })
    return template.replace('{aionuiConfig}', encodeURIComponent(toBase64(payload)))
  }

  if (template.includes('{deepChatConfig}')) {
    const payload = JSON.stringify({ id: 'yaoshu-token', baseUrl: serverAddress, apiKey: apiKey ?? '' })
    return template.replace('{deepChatConfig}', encodeURIComponent(toBase64(payload)))
  }

  let result = template.replace('{address}', address)

  if (apiKey) {
    // 规范化 API Key（自动补 sk- 前缀）
    const normalizedKey = apiKey.startsWith('sk-') ? apiKey : `sk-${apiKey}`
    result = result.replace('{key}', normalizedKey)
  }

  return result
}
