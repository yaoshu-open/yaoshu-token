// hero-terminal-demo 数据与纯函数：4 种 API 协议轮播 + 语法分色 tokenize
// 设计考量：tokenize 为纯函数（脱离 Vue 渲染），产出 {text,type}[] 供模板 v-for 渲染 <span>，可独立单测

type AccentTone = 'amber' | 'blue' | 'emerald' | 'violet'

export interface ApiDemoConfig {
  id: string
  label: string
  method: 'GET' | 'POST'
  endpoint: string
  headers: string[]
  request: string[]
  response: string[]
  responseHighlights: string[]
  tokens: number
  latency: number
  accent: AccentTone
}

/** tokenize 产出的渲染令牌——type 决定 CSS 类名 */
export type TokenType = 'accent' | 'flag' | 'key' | 'muted' | 'number' | 'string'

export interface Token {
  text: string
  type: TokenType
}

/** 轮播间隔与过渡时长（ms） */
export const CYCLE_INTERVAL = 4500
export const TRANSITION_MS = 220

const STRING_RE = /"[^"]*"/g
const PLACEHOLDER_RE = /<[a-z]+>/gi

export const API_DEMOS: ApiDemoConfig[] = [
  {
    id: 'gpt-chat',
    label: 'Chat',
    method: 'POST',
    endpoint: '/v1/chat/completions',
    headers: ['"Authorization: Bearer sk-••••"'],
    request: [
      '"model": "your-model",',
      '"messages": [',
      '  { "role": "user", "content": "..." }',
      ']'
    ],
    response: [
      '{',
      '  "choices": [{ "message": { "content": <text> } }],',
      '  "usage": { "total_tokens": <tokens> }',
      '}'
    ],
    responseHighlights: ['<text>', '<tokens>'],
    tokens: 27,
    latency: 142,
    accent: 'emerald'
  },
  {
    id: 'responses',
    label: 'Responses',
    method: 'POST',
    endpoint: '/v1/responses',
    headers: ['"Authorization: Bearer sk-••••"'],
    request: ['"model": "your-model",', '"input": "..."'],
    response: [
      '{',
      '  "output": [{ "type": "output_text", "text": <text> }],',
      '  "usage": { "total_tokens": <tokens> }',
      '}'
    ],
    responseHighlights: ['<text>', '<tokens>'],
    tokens: 31,
    latency: 168,
    accent: 'amber'
  },
  {
    id: 'claude',
    label: 'Claude',
    method: 'POST',
    endpoint: '/v1/messages',
    headers: ['"x-api-key: sk-••••"', '"anthropic-version: 2023-06-01"'],
    request: [
      '"model": "your-model",',
      '"max_tokens": 1024,',
      '"messages": [',
      '  { "role": "user", "content": "..." }',
      ']'
    ],
    response: [
      '{',
      '  "content": [{ "type": "text", "text": <text> }],',
      '  "usage": { "input_tokens": <in>, "output_tokens": <out> }',
      '}'
    ],
    responseHighlights: ['<text>', '<in>', '<out>'],
    tokens: 29,
    latency: 156,
    accent: 'blue'
  },
  {
    id: 'gemini',
    label: 'Gemini',
    method: 'POST',
    endpoint: '/v1beta/models/{model}:generateContent',
    headers: ['"x-goog-api-key: sk-••••"'],
    request: [
      '"contents": [',
      '  { "role": "user",',
      '    "parts": [{ "text": "..." }] }',
      ']'
    ],
    response: [
      '{',
      '  "candidates": [{ "content": { "parts": [{ "text": <text> }] } }],',
      '  "usageMetadata": { "totalTokenCount": <tokens> }',
      '}'
    ],
    responseHighlights: ['<text>', '<tokens>'],
    tokens: 25,
    latency: 93,
    accent: 'violet'
  }
]

/** 各 demo 的响应文本占位内容 */
const RESPONSE_TEXT_MAP: Record<string, string> = {
  'gpt-chat': 'Chat request routed.',
  responses: 'Response workflow ready.',
  claude: 'Claude message routed.',
  gemini: 'Gemini request served.'
}

export function truncateResponse(demo: ApiDemoConfig): string {
  return RESPONSE_TEXT_MAP[demo.id] ?? '...'
}

/**
 * JSON 行分色 tokenize：按 "..." 串切分，串后跟 `:` 判定为 key，否则为 string，其余为 muted。
 */
export function tokenizeJsonLine(line: string): Token[] {
  if (!line.trim()) return [{ text: ' ', type: 'muted' }]

  const tokens: Token[] = []
  let cursor = 0
  const matches = [...line.matchAll(STRING_RE)]

  for (const match of matches) {
    const start = match.index ?? 0
    if (start > cursor) {
      tokens.push({ text: line.slice(cursor, start), type: 'muted' })
    }
    const text = match[0]
    const after = line.slice(start + text.length).trimStart()
    const isKey = after.startsWith(':')
    tokens.push({ text, type: isKey ? 'key' : 'string' })
    cursor = start + text.length
  }

  if (cursor < line.length) {
    tokens.push({ text: line.slice(cursor), type: 'muted' })
  }

  return tokens.length > 0 ? tokens : [{ text: line, type: 'muted' }]
}

/**
 * 响应行分色 tokenize：先匹配 <text>/<tokens>/<in>/<out> 占位符替换为 accent/number token，
 * 其余段走 tokenizeJsonLine。
 */
export function tokenizeResponseLine(line: string, demo: ApiDemoConfig): Token[] {
  if (!line.trim()) return [{ text: ' ', type: 'muted' }]

  const matches = [...line.matchAll(PLACEHOLDER_RE)]
  if (matches.length === 0) return tokenizeJsonLine(line)

  const tokens: Token[] = []
  let cursor = 0

  for (const match of matches) {
    const start = match.index ?? 0
    if (start > cursor) {
      for (const t of tokenizeJsonLine(line.slice(cursor, start))) {
        tokens.push(t)
      }
    }
    const placeholder = match[0]
    if (placeholder === '<text>') {
      tokens.push({ text: `"${truncateResponse(demo)}"`, type: 'accent' })
    } else if (placeholder === '<tokens>') {
      tokens.push({ text: String(demo.tokens), type: 'number' })
    } else if (placeholder === '<in>') {
      tokens.push({ text: String(Math.floor(demo.tokens * 0.4)), type: 'number' })
    } else if (placeholder === '<out>') {
      tokens.push({ text: String(Math.ceil(demo.tokens * 0.6)), type: 'number' })
    } else {
      tokens.push({ text: placeholder, type: 'muted' })
    }
    cursor = start + placeholder.length
  }

  if (cursor < line.length) {
    for (const t of tokenizeJsonLine(line.slice(cursor))) {
      tokens.push(t)
    }
  }

  return tokens
}
