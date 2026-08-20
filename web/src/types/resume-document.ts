export const RESUME_DOCUMENT_SCHEMA_VERSION = 'RESUME_DOCUMENT_V1'

export interface ResumeDocumentContact {
  id: string
  label: string
  value: string
}

export interface ResumeDocumentBasics {
  name: string | null
  contacts: ResumeDocumentContact[]
}

export interface ResumeDocumentBullet {
  id: string
  text: string
}

export interface ResumeDocumentEntry {
  id: string
  heading: string | null
  meta: string | null
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
