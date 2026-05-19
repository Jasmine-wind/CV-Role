export interface ResumeListItem {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  createdAt: string
}

export interface ResumeDetail {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  createdAt: string
  updatedAt: string
}

export interface ResumeUploadResult {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  createdAt: string
}

export interface ResumeParseResult {
  resumeId: number
  parseStatus: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | string
  extractedText: string | null
  cleanedText: string | null
  sectionResult: string | null
  structuredJson: string | null
  errorMessage: string | null
  textQualityStatus?: 'GOOD' | 'WARNING' | 'FAILED' | string | null
  textQualityIssues?: string | null
  textQualityMessage?: string | null
  parseQualityStatus?: 'GOOD' | 'WARNING' | 'FAILED' | string | null
  parseQualityWarnings?: string | null
  parseQualityMessage?: string | null
  parseQualityScore?: number | null
  updatedAt: string | null
}

export type ResumeParseMode = 'FAST' | 'BALANCED' | 'ACCURATE'

export interface ResumeParseOptions {
  parseMode?: ResumeParseMode
}

export interface ResumeTextSection {
  sectionType: string
  heading: string
  sourceSectionConfidence?: string | null
  lines: string[]
  blocks?: ResumeBlock[] | null
}

export interface ResumeBlock {
  index?: number | null
  originalIndex?: number | null
  displayOrder?: number | null
  text?: string | null
  prevText?: string | null
  nextText?: string | null
  sourceType?: string | null
  sourceSection?: string | null
  ruleSection?: string | null
  ruleConfidence?: number | null
  sourceSectionConfidence?: string | null
  lockedLevel?: string | null
  resumeTypeHint?: string | null
  parseMode?: string | null
  finalSectionSource?: string | null
  sectionLocked?: boolean | null
}

export interface ResumeBasicInfoField {
  value?: string | null
  confidence?: number | null
  source?: string | null
  evidence?: string | null
  status?: 'CONFIRMED' | 'REJECTED' | 'EMPTY' | 'LOW_CONFIDENCE' | string | null
  rejectReason?: string | null
}

export interface ResumeParseMeta {
  parseMode?: ResumeParseMode | string | null
  parserVersion?: string | null
  aiStatus?: 'USED' | 'SKIPPED' | 'FALLBACK' | 'DISABLED' | string | null
  aiUsed?: boolean | null
  aiSkippedReason?: string | null
  aiFallbackOccurred?: boolean | null
  aiFallbackReason?: string | null
  aiCacheHit?: boolean | null
  aiCacheKeyDigest?: string | null
  totalParseDurationMs?: number | null
  ruleParseDurationMs?: number | null
  aiSectionClassifyDurationMs?: number | null
  aiStructuredParseDurationMs?: number | null
}

export interface ResumeRawSectionBlock {
  index?: number | null
  text?: string | null
  iconType?: string | null
  originalIndex?: number | null
  displayOrder?: number | null
}

export interface ResumeRawSection {
  id?: string | null
  originalTitle?: string | null
  normalizedSection?: string | null
  displayName?: string | null
  confidence?: number | null
  source?: string | null
  originalOrder?: number | null
  displayOrder?: number | null
  blocks?: ResumeRawSectionBlock[] | null
}

export interface ResumeSkillEvidence {
  skill?: string | null
  sourceSectionId?: string | null
  sourceText?: string | null
}

export interface ResumeSkillSet {
  keywords?: string[] | null
  groups?: Record<string, string[]> | null
  evidence?: ResumeSkillEvidence[] | null
}

export interface ResumeSourceRef {
  startLine?: number | null
  endLine?: number | null
  text?: string | null
}

export interface ResumeIndexedLine {
  lineId?: number | null
  page?: number | null
  text?: string | null
  normalizedText?: string | null
  sourceType?: string | null
  rawSectionId?: string | null
  sectionHint?: string | null
  sectionConfidence?: number | null
  isNoise?: boolean | null
}

export interface ResumeExperience {
  type?: 'WORK' | 'INTERNSHIP' | 'CAMPUS' | 'PRACTICE' | 'VOLUNTEER' | 'UNKNOWN' | string | null
  organization?: string | null
  role?: string | null
  startDate?: string | null
  endDate?: string | null
  description?: string | null
  bullets?: string[] | null
  sourceSectionId?: string | null
  sourceTitle?: string | null
  evidence?: string[] | null
  sourceRef?: ResumeSourceRef | null
  confidence?: number | null
}

export interface ResumeProject {
  name?: string | null
  description?: string | null
  role?: string | null
  mentor?: string | null
  timeRange?: string | null
  environment?: string | null
  techStack?: string[] | null
  responsibilities?: string[] | null
  startDate?: string | null
  endDate?: string | null
  sourceType?: 'INDEPENDENT' | 'WORK_EXPERIENCE' | 'INTERNSHIP' | 'CAMPUS' | 'UNKNOWN' | string | null
  parentExperienceIndex?: number | null
  sourceSectionId?: string | null
  evidence?: string[] | null
  sourceRef?: ResumeSourceRef | null
  confidence?: number | null
}

export interface ResumeAchievement {
  title?: string | null
  level?: string | null
  competition?: string | null
  ranking?: string | null
  timeRange?: string | null
  date?: string | null
  sourceSectionId?: string | null
  parentExperienceIndex?: number | null
  evidence?: string[] | null
  sourceRef?: ResumeSourceRef | null
  confidence?: number | null
}

export interface ResumeStructuredData {
  education?: string[] | null
  educationSourceRefs?: ResumeSourceRef[] | null
  skills?: ResumeSkillSet | null
  experiences?: ResumeExperience[] | null
  projects?: ResumeProject[] | null
  achievements?: ResumeAchievement[] | null
  certificates?: string[] | null
  summary?: string | null
  summarySourceRef?: ResumeSourceRef | null
  others?: string[] | null
}

export interface ResumeDisplayModelOverview {
  name?: string | null
  targetRole?: string | null
  resumeType?: string | null
  highestDegree?: string | null
  workYears?: string | null
  coreSkills?: string[] | null
}

export interface ResumeDisplayModelSkillGroup {
  name?: string | null
  skills?: string[] | null
}

export interface ResumeDisplayModelSkillSummary {
  topSkills?: string[] | null
  groups?: ResumeDisplayModelSkillGroup[] | null
}

export interface ResumeDisplayModelEducationCard {
  school?: string | null
  degree?: string | null
  major?: string | null
  timeRange?: string | null
  summary?: string | null
  sourceRef?: ResumeSourceRef | null
}

export interface ResumeDisplayModelExperienceCard {
  company?: string | null
  position?: string | null
  timeRange?: string | null
  summary?: string | null
  responsibilities?: string[] | null
  collapsed?: boolean | null
  sourceRef?: ResumeSourceRef | null
}

export interface ResumeDisplayModelProjectCard {
  name?: string | null
  summary?: string | null
  techStack?: string[] | null
  responsibilities?: string[] | null
  collapsed?: boolean | null
  sourceRef?: ResumeSourceRef | null
}

export interface ResumeDisplayModelAchievementCard {
  title?: string | null
  meta?: string | null
  sourceRef?: ResumeSourceRef | null
}

export interface ResumeDisplayModelSummaryCard {
  content?: string | null
  collapsed?: boolean | null
  sourceRef?: ResumeSourceRef | null
}

export interface ResumeDisplayMeta {
  generatedBy?: 'AI' | 'RULE' | string | null
  aiDisplayUsed?: boolean | null
  aiDisplayFallback?: boolean | null
  aiDisplayErrorMessage?: string | null
  aiDisplayDurationMs?: number | null
  cacheHit?: boolean | null
  cacheKeyDigest?: string | null
  displayPromptVersion?: string | null
  displayAdapterVersion?: string | null
  modelName?: string | null
}

export interface ResumeDisplayModel {
  overview?: ResumeDisplayModelOverview | null
  skillSummary?: ResumeDisplayModelSkillSummary | null
  educationCards?: ResumeDisplayModelEducationCard[] | null
  workExperienceCards?: ResumeDisplayModelExperienceCard[] | null
  internshipCards?: ResumeDisplayModelExperienceCard[] | null
  campusExperienceCards?: ResumeDisplayModelExperienceCard[] | null
  projectCards?: ResumeDisplayModelProjectCard[] | null
  achievementCards?: ResumeDisplayModelAchievementCard[] | null
  certificateTags?: string[] | null
  summaryCard?: ResumeDisplayModelSummaryCard | null
  pendingItems?: string[] | null
  displayMeta?: ResumeDisplayMeta | null
}

export interface ResumeStructuredContent {
  name?: string | null
  phone?: string | null
  email?: string | null
  basicInfo?: Record<string, string> | null
  basicInfoDebug?: Record<string, ResumeBasicInfoField> | null
  rawSections?: ResumeRawSection[] | null
  indexedLines?: ResumeIndexedLine[] | null
  structuredData?: ResumeStructuredData | null
  parseMeta?: ResumeParseMeta | null
  displayModel?: ResumeDisplayModel | null
  aiDisplayModel?: ResumeDisplayModel | null
  ruleDisplayModel?: ResumeDisplayModel | null
  jobIntention?: string | null
  highestEducation?: string | null
  resumeType?: string | null
  parseMode?: ResumeParseMode | string | null
  parserVersion?: string | null
  education?: string[] | null
  skills?: string[] | null
  projects?: string[] | null
  workExperiences?: string[] | null
  internships?: string[] | null
  campusExperiences?: string[] | null
  awards?: string[] | null
  certificates?: string[] | null
  summary?: string | null
  others?: string[] | null
  qualityWarnings?: string[] | null
  aiSectionClassifyEnabled?: boolean | null
  aiSectionClassifyApplied?: boolean | null
  aiSectionClassifyFallbackReason?: string | null
  aiSectionClassifyDurationMs?: number | null
  aiSectionClassifyCacheHit?: boolean | null
  aiSectionClassifyCacheKey?: string | null
  aiStructuredParseEnabled?: boolean | null
  aiStructuredParseApplied?: boolean | null
  aiStructuredParseFallbackReason?: string | null
  aiStructuredParseDurationMs?: number | null
  aiStructuredParseCacheHit?: boolean | null
  aiStructuredParseCacheKey?: string | null
  textExtractDurationMs?: number | null
  ruleParseDurationMs?: number | null
  totalParseDurationMs?: number | null
  sections?: ResumeTextSection[] | null
  rawText?: string | null
  debug?: Record<string, unknown> | null
}

export interface ResumeAiAnalysis {
  resumeId: number
  analysisStatus: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  score: number | null
  strengths?: string[] | null
  problems?: string[] | null
  suggestionsSummary?: string[] | null
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  updatedAt: string | null
}

export interface ResumeAiAnalysisTrigger {
  resumeId: number
  analysisStatus: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  score: number | null
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  updatedAt: string | null
}
