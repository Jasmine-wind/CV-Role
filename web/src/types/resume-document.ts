/** RESUME_DOCUMENT_V1 remains the canonical business document schema. */
export const RESUME_DOCUMENT_SCHEMA_VERSION = 'RESUME_DOCUMENT_V1'

export type ResumeDocumentContactType =
  | 'PHONE'
  | 'EMAIL'
  | 'WECHAT'
  | 'QQ'
  | 'LINKEDIN'
  | 'GITHUB'
  | 'WEBSITE'
  | 'LOCATION'
  | 'OTHER'

export interface ResumeDocumentContact {
  id: string
  type: string
  label: string | null
  value: string
}

export interface ResumeDocumentBasics {
  name: string | null
  jobIntention?: string | null
  highestEducation?: string | null
  contacts: ResumeDocumentContact[]
}

export interface ResumeDocumentBullet {
  id: string
  text: string
}

/**
 * 条目按章节语义携带结构化字段：
 * 工作/项目经历使用 organization/role，教育经历使用 school/degree/major，
 * 技能组使用 group + skillItems；日期为原文字符串。
 */
export interface ResumeDocumentEntry {
  id: string
  organization: string | null
  role: string | null
  school: string | null
  degree: string | null
  major: string | null
  startDate: string | null
  endDate: string | null
  location: string | null
  group: string | null
  skillItems: string[] | null
  bullets: ResumeDocumentBullet[]
}

export interface ResumeDocumentSection {
  id: string
  kind: string
  title: string
  entries: ResumeDocumentEntry[]
}

export interface ResumeDocument {
  schemaVersion: string
  basics: ResumeDocumentBasics
  sections: ResumeDocumentSection[]
}
