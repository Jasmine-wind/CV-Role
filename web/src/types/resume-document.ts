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

export interface ResumeSourceRef {
  startLine?: number | null
  endLine?: number | null
  text?: string | null
  sourceBlockIds?: string[] | null
  sourceOccurrenceIds?: string[] | null
  page?: number | null
  x?: number | null
  y?: number | null
  width?: number | null
  height?: number | null
  fontSize?: number | null
  fontName?: string | null
  boldHint?: boolean | null
  indent?: number | null
  bulletHint?: boolean | null
  role?: string | null
  sourceType?: string | null
}

export interface ResumeDocumentContact {
  id: string
  type: string
  label: string | null
  value: string
  sourceRef?: ResumeSourceRef | null
  sourceOccurrenceIds?: string[] | null
}

export interface ResumeDocumentBasics {
  name: string | null
  jobIntention?: string | null
  highestEducation?: string | null
  contacts: ResumeDocumentContact[]
  sourceRef?: ResumeSourceRef | null
  sourceOccurrenceIds?: string[] | null
  fieldSourceRefs?: Record<string, ResumeSourceRef> | null
}

export interface ResumeDocumentBullet {
  id: string
  text: string
  sourceRef?: ResumeSourceRef | null
  sourceOccurrenceIds?: string[] | null
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
  environment?: string | null
  mentor?: string | null
  techStack?: string[] | null
  techStackSourceRefs?: (ResumeSourceRef | null)[] | null
  group: string | null
  awardTitle?: string | null
  awardLevel?: string | null
  awardCompetition?: string | null
  awardRanking?: string | null
  awardDate?: string | null
  skillItems: string[] | null
  skillDescriptions?: string[] | null
  sourceRef?: ResumeSourceRef | null
  sourceOccurrenceIds?: string[] | null
  fieldSourceRefs?: Record<string, ResumeSourceRef> | null
  skillItemSourceRefs?: (ResumeSourceRef | null)[] | null
  skillDescriptionSourceRefs?: (ResumeSourceRef | null)[] | null
  bullets: ResumeDocumentBullet[]
}

export interface ResumeDocumentSection {
  id: string
  kind: string
  title: string
  entries: ResumeDocumentEntry[]
  sourceRef?: ResumeSourceRef | null
  sourceOccurrenceIds?: string[] | null
}

export interface ResumeDocument {
  schemaVersion: string
  basics: ResumeDocumentBasics
  sections: ResumeDocumentSection[]
  sourceRef?: ResumeSourceRef | null
  sourceOccurrenceIds?: string[] | null
  sourceOccurrenceTexts?: Record<string, string> | null
  sourceOccurrencePrimaryIds?: Record<string, string> | null
  sourceOccurrenceRefs?: Record<string, ResumeSourceRef> | null
}
