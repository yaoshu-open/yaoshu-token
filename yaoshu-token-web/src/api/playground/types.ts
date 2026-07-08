/**
 * Playground API 与状态类型声明。
 * 字段命名遵循 OpenAI 兼容 / 后端 /pg/chat/completions 契约。
 */

// 消息角色
export type MessageRole = 'user' | 'assistant' | 'system'

// 消息状态机
export type MessageStatus = 'loading' | 'streaming' | 'complete' | 'error'

// 消息版本（单版本，versions[0] 即为当前内容）
export interface MessageVersion {
  id: string
  content: string
}

// 单条消息
export interface Message {
  key: string
  from: MessageRole
  versions: MessageVersion[]
  sources?: { href: string; title: string }[]
  reasoning?: {
    content: string
    duration: number
  }
  isReasoningStreaming?: boolean
  isReasoningComplete?: boolean
  isContentComplete?: boolean
  status?: MessageStatus
  errorCode?: string | null
}

// Chat Completion 多模态 ContentPart
export interface ContentPart {
  type: 'text' | 'image_url'
  text?: string
  image_url?: { url: string }
}

// Chat Completion Message（API 入参）
export interface ChatCompletionMessage {
  role: MessageRole
  content: string | ContentPart[]
}

// Chat Completion Request（API 入参）
export interface ChatCompletionRequest {
  model: string
  messages: ChatCompletionMessage[]
  stream: boolean
  temperature?: number
  top_p?: number
  max_tokens?: number
  frequency_penalty?: number
  presence_penalty?: number
  seed?: number
}

// Chat Completion Chunk（SSE 单条事件，OpenAI 兼容）
export interface ChatCompletionChunk {
  id: string
  object: string
  created: number
  model: string
  choices: Array<{
    index: number
    delta: {
      role?: MessageRole
      content?: string
      reasoning_content?: string
    }
    finish_reason: string | null
  }>
  // 流式最后一个 chunk 可能携带 usage（需 stream_options.include_usage）
  usage?: {
    prompt_tokens: number
    completion_tokens: number
    total_tokens: number
  }
}

// Chat Completion Response（非流式响应）
export interface ChatCompletionResponse {
  id: string
  object: string
  created: number
  model: string
  choices: Array<{
    index: number
    message: {
      role: MessageRole
      content: string
      reasoning_content?: string
    }
    finish_reason: string
  }>
  usage?: {
    prompt_tokens: number
    completion_tokens: number
    total_tokens: number
  }
}

// Playground 配置（持久化）
export interface PlaygroundConfig {
  model: string
  group: string
  temperature: number
  top_p: number
  max_tokens: number
  frequency_penalty: number
  presence_penalty: number
  seed: number | null
  stream: boolean
  /** system prompt（每次请求前置注入，空则不注入） */
  systemPrompt: string
}

// 参数启用开关
export interface ParameterEnabled {
  temperature: boolean
  top_p: boolean
  max_tokens: boolean
  frequency_penalty: boolean
  presence_penalty: boolean
  seed: boolean
}

// 模型选项
export interface ModelOption {
  label: string
  value: string
  /** 模型最大上下文 token 数（后端 /api/user/models max_context 字段，null=未设置） */
  maxContext?: number
}

// 分组选项
export interface GroupOption {
  label: string
  value: string
  ratio: number
  desc?: string
}

// 调试面板 Tab
export type DebugTab = 'preview' | 'actual' | 'sse'

// SSE 事件记录（DebugDrawer 时间线）
export interface SseEventRecord {
  id: string
  raw: string
  delta?: { content?: string; reasoning_content?: string }
  timestamp: number
  isError?: boolean
  isDone?: boolean
}

// 自定义请求体状态
export interface CustomRequestState {
  mode: boolean
  body: string
  isValid: boolean
}
