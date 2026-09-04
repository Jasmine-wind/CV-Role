export const RESUME_CONTACT_TYPE_OPTIONS = [
  { value: 'PHONE', label: '电话' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'WECHAT', label: '微信' },
  { value: 'QQ', label: 'QQ' },
  { value: 'LINKEDIN', label: 'LinkedIn' },
  { value: 'GITHUB', label: 'GitHub' },
  { value: 'WEBSITE', label: '个人网站' },
  { value: 'LOCATION', label: '所在地' },
  { value: 'OTHER', label: '其他' },
]

export const REQUIRED_RESUME_CONTACT_TYPE_OPTIONS = RESUME_CONTACT_TYPE_OPTIONS.filter(
  (option) => option.value === 'PHONE' || option.value === 'EMAIL',
)

export const getResumeContactTypeLabel = (type?: string | null) =>
  RESUME_CONTACT_TYPE_OPTIONS.find((option) => option.value === type)?.label ?? '联系方式'

export const getResumeContactPlaceholder = (type?: string | null) => {
  switch (type) {
    case 'PHONE':
      return '例如 138 1234 5678'
    case 'EMAIL':
      return '例如 name@example.com'
    case 'GITHUB':
      return '例如 github.com/name'
    case 'WEBSITE':
      return '例如 your-site.com'
    default:
      return '联系方式内容'
  }
}
