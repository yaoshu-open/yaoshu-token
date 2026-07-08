// 职责：OTP / 备用码 / 邮箱的正则校验与格式化
// 被消费方：OtpForm / SignUpForm（邮箱）

const BACKUP_CODE_REGEX = /^[A-Z0-9]{4}-[A-Z0-9]{4}$/i
const OTP_REGEX = /^\d{6}$/
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// 校验 6 位 OTP
export function isValidOTP(code: string): boolean {
  return OTP_REGEX.test(code)
}

// 校验备用码 XXXX-XXXX 格式
export function isValidBackupCode(code: string): boolean {
  return BACKUP_CODE_REGEX.test(code)
}

// 格式化备用码：自动添加连字符（输入 8 位 → XXXX-XXXX）
export function formatBackupCode(value: string): string {
  // 移除非字母数字字符 + 转大写
  let cleaned = value.toUpperCase().replace(/[^A-Z0-9]/g, '')

  // 限制 8 字符
  if (cleaned.length > 8) {
    cleaned = cleaned.slice(0, 8)
  }

  // 4 字符后插入连字符
  if (cleaned.length > 4) {
    return `${cleaned.slice(0, 4)}-${cleaned.slice(4)}`
  }

  return cleaned
}

// 去除备用码的连字符（提交给后端前）
export function cleanBackupCode(code: string): string {
  return code.replace(/-/g, '')
}

// 基础邮箱校验
export function isValidEmail(email: string): boolean {
  return EMAIL_REGEX.test(email)
}
