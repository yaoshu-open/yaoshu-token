/**
 * Playground 通用 Markdown 渲染封装。
 *
 * 设计考量：AI 生成内容不可信，DOMPurify XSS 清洗是红线级安全要求；
 * highlight.js 代码高亮是 Playground 代码问答场景刚需；markdown-it 只读渲染最灵活
 * （md-editor-v3 编辑器能力过剩）。
 *
 * 安全机制：markdown-it → DOMPurify.sanitize → highlight.js 代码块高亮
 * - 禁止渲染 <script>/<iframe>/on* 事件
 * - 允许高亮代码块但禁止内联 JS
 */
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'

// 按需注册常用语言（避免全量 190+ 语言打包膨胀）
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import java from 'highlight.js/lib/languages/java'
import markdown from 'highlight.js/lib/languages/markdown'
import yaml from 'highlight.js/lib/languages/yaml'
import shell from 'highlight.js/lib/languages/shell'
import plaintext from 'highlight.js/lib/languages/plaintext'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('tsx', typescript)
hljs.registerLanguage('jsx', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('shell', shell)
hljs.registerLanguage('json', json)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('vue', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('go', go)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('rs', rust)
hljs.registerLanguage('java', java)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('text', plaintext)

const md = new MarkdownIt({
  html: false, // 禁止内联 HTML（DOMPurify 兜底）
  linkify: true, // 自动识别 URL
  breaks: true, // 软换行 → <br>
  typographer: false, // 关闭智能引号（中文友好，避免误转换）
  highlight(code: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const result = hljs.highlight(code, { language: lang, ignoreIllegals: true })
        return `<pre class="hljs"><code class="language-${lang}">${result.value}</code></pre>`
      } catch {
        // fall through
      }
    }
    // 无语言或未注册：转义后包裹
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  }
})

/**
 * 渲染 Markdown → 已 XSS 清洗的 HTML 字符串。
 * - 支持代码块高亮（已注册 16+ 常见语言）
 * - 链接自动加 target="_blank" rel="noopener noreferrer"
 */
export function renderMarkdown(source: string): string {
  if (!source) return ''
  const raw = md.render(source)
  // 链接安全加固：所有 <a> 加 target=_blank + rel
  const sanitized = DOMPurify.sanitize(raw, {
    ALLOWED_TAGS: [
      'p', 'br', 'hr', 'strong', 'em', 'u', 's', 'del', 'ins', 'mark',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'ul', 'ol', 'li',
      'blockquote', 'code', 'pre', 'kbd', 'sub', 'sup',
      'a', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'span', 'div'
    ],
    ALLOWED_ATTR: [
      'href', 'title', 'target', 'rel',
      'src', 'alt', 'width', 'height',
      'class', 'id'
    ],
    ALLOW_DATA_ATTR: false
  })
  // 链接后处理：外链加 target/rel
  return sanitized.replace(
    /<a\s+([^>]*?)>/gi,
    (match, attrs: string) => {
      if (/target=/i.test(attrs)) return match
      if (!/href=/i.test(attrs)) return match
      return `<a ${attrs} target="_blank" rel="noopener noreferrer">`
    }
  )
}

/**
 * 检测代码块语言（fallback 策略：从 ```lang 提取，未识别时返回 'plaintext'）。
 * 用于 reasoning 折叠等场景下不实际渲染、仅展示语言标签。
 */
export function detectCodeLang(source: string): string | null {
  const match = source.match(/^```(\w+)/)
  return match ? match[1] : null
}
