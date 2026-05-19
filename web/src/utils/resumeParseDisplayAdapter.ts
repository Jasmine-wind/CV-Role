import type {
  ResumeAchievement,
  ResumeBasicInfoField,
  ResumeDisplayModel,
  ResumeDisplayMeta,
  ResumeExperience,
  ResumeProject,
  ResumeSourceRef,
  ResumeStructuredContent,
} from '@/types/resume'

export interface ResumeDisplayField {
  key: string
  label: string
  value: string
}

export interface ResumeSkillGroupDisplay {
  key: string
  label: string
  skills: string[]
  previewSkills: string[]
  hiddenCount: number
}

export interface ResumeEducationCard {
  id: string
  school: string
  degreeMajor: string
  timeRange: string
  extras: string[]
}

export interface ResumeExperienceCard {
  id: string
  type: string
  organization: string
  role: string
  timeRange: string
  summary: string
  details: string[]
  sourceText: string
  sourceLineRange: { startLine?: number | null; endLine?: number | null } | null
  hiddenCount: number
}

export interface ResumeProjectCard {
  id: string
  name: string
  summary: string
  sourceText: string
  sourceLineRange: { startLine?: number | null; endLine?: number | null } | null
  techStack: string[]
  role: string
  responsibilities: string[]
  hiddenCount: number
}

export interface ResumeAchievementCard {
  id: string
  title: string
  meta: string
}

export interface ResumeSummaryCard {
  text: string
  preview: string
  fullText: string
  expandable: boolean
}

export interface ResumeDisplaySections {
  basicInfo: ResumeDisplayField[]
  skillGroups: ResumeSkillGroupDisplay[]
  educationCards: ResumeEducationCard[]
  experienceCards: ResumeExperienceCard[]
  internshipCards: ResumeExperienceCard[]
  campusCards: ResumeExperienceCard[]
  projectCards: ResumeProjectCard[]
  projectPreviewCards: ResumeProjectCard[]
  projectHiddenCount: number
  achievementCards: ResumeAchievementCard[]
  certificateTags: string[]
  summaryCard: ResumeSummaryCard | null
  pendingItems: string[]
  pendingPreviewItems: string[]
  otherCollapsedItems: string[]
  otherPreviewItems: string[]
  debugInfo: {
    rawSectionCount: number
    qualityWarnings: string[]
    displayMeta?: ResumeDisplayMeta | null
  }
}

const BASIC_INFO_FIELDS: Array<{ key: string; label: string; core?: boolean }> = [
  { key: 'name', label: '姓名', core: true },
  { key: 'phone', label: '手机号', core: true },
  { key: 'email', label: '邮箱', core: true },
  { key: 'jobIntention', label: '求职意向', core: true },
  { key: 'highestEducation', label: '最高学历', core: true },
  { key: 'workYears', label: '工作年限', core: true },
  { key: 'resumeType', label: '简历类型', core: true },
  { key: 'gender', label: '性别' },
  { key: 'age', label: '年龄' },
  { key: 'location', label: '所在地' },
  { key: 'github', label: 'GitHub' },
  { key: 'gpa', label: 'GPA' },
  { key: 'languageAbility', label: '语言能力' },
]

const GROUP_LABELS: Record<string, string> = {
  backend: '后端',
  frontend: '前端',
  database: '数据库',
  middleware: '中间件',
  tool: '工具 / 环境',
  ai: 'AI / 算法',
  other: '其他技能',
}

const BACKEND_SKILLS = new Set([
  'Java', 'Spring', 'Spring Boot', 'Spring MVC', 'Spring Cloud', 'Spring Security',
  'MyBatis', 'MyBatis-Plus', 'Dubbo', 'Python', 'FastAPI', 'RESTful', 'JWT',
])
const FRONTEND_SKILLS = new Set(['Vue', 'JavaScript', 'TypeScript', 'HTML', 'CSS', 'jQuery', 'Bootstrap', 'Element UI'])
const DATABASE_SKILLS = new Set(['MySQL', 'PostgreSQL', 'Redis', 'MongoDB', 'Oracle', 'Elasticsearch', 'SQL'])
const MIDDLEWARE_SKILLS = new Set(['RabbitMQ', 'RocketMQ', 'Kafka', 'Zookeeper', 'Eureka', 'Nginx', 'Tomcat', 'FastDFS'])
const TOOL_SKILLS = new Set(['Git', 'Maven', 'Gradle', 'Docker', 'Kubernetes', 'Linux', 'IDEA', 'MATLAB', 'Apache POI', 'PowerDesigner'])
const AI_SKILLS = new Set(['OpenCV', 'YOLO', 'DETR', 'Transformer', 'PyTorch', 'TensorFlow', 'Scikit-learn', 'Pandas'])
const DETAIL_LABEL_PREFIX_PATTERN = /^(项目名称|项目描述|技术栈|技术选型|开发环境|开发工具|软件构架|软件架构|负责模块|参与项目描述|职责|工作内容|公司名称|职位名称|工作时间|工作描述)[:：]?/i

export const buildResumeParseDisplaySections = (content: ResumeStructuredContent | null): ResumeDisplaySections => {
  const backendDisplayModel = content?.displayModel ?? content?.aiDisplayModel ?? content?.ruleDisplayModel
  if (backendDisplayModel) {
    return buildFromDisplayModel(content, backendDisplayModel)
  }

  const structuredData = content?.structuredData
  const basicInfo = content?.basicInfo ?? {}
  const basicInfoDebug = content?.basicInfoDebug ?? {}
  const experiences = structuredData?.experiences ?? []
  const projects = structuredData?.projects ?? []
  const achievements = structuredData?.achievements ?? []
  const others = structuredData?.others ?? content?.others ?? []
  const educationCards = buildEducationCards(structuredData?.education ?? content?.education ?? [], basicInfo)
  const skillGroups = buildSkillGroups(structuredData?.skills?.groups, structuredData?.skills?.keywords ?? content?.skills ?? [])
  const experienceCards = buildExperienceCards(experiences, ['WORK'], content?.workExperiences)
  const internshipCards = buildExperienceCards(experiences, ['INTERNSHIP'], content?.internships)
  const campusCards = buildCampusCards(experiences, content?.campusExperiences)
  const projectCards = finalizeProjectCards(buildProjectCards(projects, content?.projects))
  const achievementCards = buildAchievementCards(achievements, content?.awards)
  const certificateTags = uniqueStrings(structuredData?.certificates ?? content?.certificates ?? []).filter(isUsefulLine)
  const summaryCard = buildSummaryCard(structuredData?.summary ?? content?.summary, structuredData?.summarySourceRef?.text)
  const displayedTexts = collectDisplayedTexts({
    educationCards,
    skillGroups,
    experienceCards,
    internshipCards,
    campusCards,
    projectCards,
    achievementCards,
    certificateTags,
    summaryCard,
  })
  const pendingItems = buildPendingItems(others, displayedTexts)

  return {
    basicInfo: buildBasicInfoFields(content, basicInfo, basicInfoDebug),
    skillGroups,
    educationCards,
    experienceCards,
    internshipCards,
    campusCards,
    projectCards,
    projectPreviewCards: projectCards.slice(0, 3),
    projectHiddenCount: Math.max(projectCards.length - 3, 0),
    achievementCards,
    certificateTags,
    summaryCard,
    pendingItems,
    pendingPreviewItems: pendingItems.slice(0, 2),
    otherCollapsedItems: pendingItems,
    otherPreviewItems: pendingItems.slice(0, 2),
    debugInfo: {
      rawSectionCount: content?.rawSections?.length ?? 0,
      qualityWarnings: content?.qualityWarnings ?? [],
      displayMeta: null,
    },
  }
}

const buildFromDisplayModel = (
  content: ResumeStructuredContent | null,
  model: ResumeDisplayModel,
): ResumeDisplaySections => {
  const topSkills = uniqueStrings(model.skillSummary?.topSkills ?? []).filter(isSkillTag).slice(0, 10)
  const skillGroups = topSkills.length
    ? [{
        key: 'display-top-skills',
        label: '核心技能',
        skills: topSkills,
        previewSkills: topSkills.slice(0, 8),
        hiddenCount: Math.max(topSkills.length - 8, 0),
      }]
    : (model.skillSummary?.groups ?? [])
        .map((group, index) => {
          const skills = uniqueStrings(group.skills ?? []).filter(isSkillTag)
          return {
            key: `display-skill-${index}-${group.name || 'other'}`,
            label: group.name || '其他技能',
            skills,
            previewSkills: skills.slice(0, 8),
            hiddenCount: Math.max(skills.length - 8, 0),
          }
        })
        .filter((group) => group.skills.length > 0)
  const pendingItems = buildPendingItems(model.pendingItems ?? [], new Set())
  const projectCards = finalizeProjectCards(aggregateDisplayProjectCards(model.projectCards ?? []))

  return {
    basicInfo: buildDisplayModelBasicInfo(content, model),
    skillGroups,
    educationCards: (model.educationCards ?? [])
      .map((card, index) => ({
        id: `display-edu-${index}`,
        school: cleanDisplayText(card.school || '') || '未识别',
        degreeMajor: [card.degree, card.major].filter(Boolean).join(' / ') || '',
        timeRange: validTimeRange(card.timeRange || '') ? card.timeRange || '' : '',
        extras: uniqueStrings([cleanSupplementText(card.summary || '', [card.school || '', card.degree || '', card.major || '', card.timeRange || ''])])
          .filter(isUsefulEducationLine)
          .slice(0, 2),
      }))
      .filter((card) => isUsefulLine(card.school) || card.extras.length > 0),
    experienceCards: aggregateDisplayExperienceCards(model.workExperienceCards ?? [], 'WORK'),
    internshipCards: aggregateDisplayExperienceCards(model.internshipCards ?? [], 'INTERNSHIP'),
    campusCards: aggregateDisplayExperienceCards(model.campusExperienceCards ?? [], 'CAMPUS'),
    projectCards,
    projectPreviewCards: projectCards.slice(0, 3),
    projectHiddenCount: Math.max(projectCards.length - 3, 0),
    achievementCards: (model.achievementCards ?? [])
      .map((card, index) => ({
        id: `display-achievement-${index}`,
        title: cleanDisplayText(card.title || ''),
        meta: cleanDisplayText(card.meta || ''),
      }))
      .filter((card) => isUsefulLine(card.title)),
    certificateTags: uniqueStrings(model.certificateTags ?? []).filter(isUsefulLine),
    summaryCard: buildSummaryCard(model.summaryCard?.content, model.summaryCard?.sourceRef?.text),
    pendingItems,
    pendingPreviewItems: pendingItems.slice(0, 2),
    otherCollapsedItems: pendingItems,
    otherPreviewItems: pendingItems.slice(0, 2),
    debugInfo: {
      rawSectionCount: content?.rawSections?.length ?? 0,
      qualityWarnings: content?.qualityWarnings ?? [],
      displayMeta: model.displayMeta ?? null,
    },
  }
}

const buildDisplayModelBasicInfo = (content: ResumeStructuredContent | null, model: ResumeDisplayModel) => {
  const overview = model.overview ?? {}
  const basicInfo = content?.basicInfo ?? {}
  const values: ResumeDisplayField[] = [
    { key: 'name', label: '姓名', value: overview.name || content?.name || basicInfo.name || '未识别' },
    { key: 'phone', label: '手机号', value: content?.phone || basicInfo.phone || '' },
    { key: 'email', label: '邮箱', value: content?.email || basicInfo.email || '' },
    { key: 'jobIntention', label: '求职意向', value: overview.targetRole || '' },
    { key: 'highestEducation', label: '最高学历', value: overview.highestDegree || '' },
    { key: 'workYears', label: '工作年限', value: overview.workYears || '' },
    { key: 'resumeType', label: '简历类型', value: overview.resumeType || '' },
  ]
  return values
    .map((field) => ({ ...field, value: cleanDisplayText(field.value) }))
    .filter((field) => field.value && (field.key !== 'name' || isReliableName(field.value) || field.value === '未识别'))
}

const aggregateDisplayExperienceCards = (
  cards: NonNullable<ResumeDisplayModel['workExperienceCards']>,
  type: string,
) => {
  if (!cards.length) return []
  const groups = new Map<string, typeof cards>()
  for (const card of cards) {
    const company = cleanCardTitle(card.company || '')
    const key = company && !isGenericExperienceTitle(company) ? company : '__merged__'
    groups.set(key, [...(groups.get(key) ?? []), card])
  }
  if (groups.has('__merged__') && groups.size > 1) {
    const orphan = groups.get('__merged__') ?? []
    groups.delete('__merged__')
    const firstKey = groups.keys().next().value as string
    groups.set(firstKey, [...(groups.get(firstKey) ?? []), ...orphan])
  }
  return Array.from(groups.values()).map((items, index) => {
    const allText = uniqueStrings(items.flatMap((card) => [
      card.company || '',
      card.position || '',
      card.timeRange || '',
      card.summary || '',
      ...(card.responsibilities ?? []),
    ])).filter(isUsefulLine)
    const sourceRefs = items.map((card) => card.sourceRef)
    const sourceText = mergeSourceRefText(sourceRefs) || allText.join('\n')
    const joined = allText.join(' ')
    const company = cleanCardTitle(items.map((card) => card.company || '').find((value) => {
      const cleaned = cleanCardTitle(value)
      return cleaned && !isGenericExperienceTitle(cleaned)
    }) || extractOrganization(joined))
    const organization = company || `${type === 'WORK' ? '工作经历' : type === 'INTERNSHIP' ? '实习经历' : '经历'} ${index + 1}`
    const timeRange = items.map((card) => card.timeRange || '').find(validTimeRange) || extractTimeRange(joined)
    const position = cleanSupplementText(
      items.map((card) => card.position || '').find((value) => cleanSupplementText(value, [organization])) || extractRole(joined),
      [organization, timeRange],
    )
    const details = sanitizeDetails(
      summarizeLines(allText),
      [organization, position, timeRange],
    ).filter((line) => !isGenericExperienceTitle(line))
    const result = {
      id: `display-${type}-${index}`,
      type,
      organization,
      role: '',
      timeRange: '',
      summary: '',
      details,
      sourceText,
      sourceLineRange: sourceLineRangeFromRefs(sourceRefs),
      hiddenCount: Math.max(details.length - 3, 0),
    }
    result.role = [position, timeRange].filter(Boolean).join(' · ')
    result.summary = cleanSupplementText(stripLongLine(items.map((card) => card.summary || '').find(Boolean) || details[0] || ''), [result.organization, result.role, ...details.slice(1)])
    result.details = result.summary ? sanitizeDetails(details, [result.summary, result.organization, result.role]) : details
    result.hiddenCount = Math.max(result.details.length - 3, 0)
    return result
  })
  .filter((card) => isUsefulLine(card.organization) || isUsefulLine(card.summary) || card.details.length > 0)
}

const aggregateDisplayProjectCards = (cards: NonNullable<ResumeDisplayModel['projectCards']>) => {
  if (!cards.length) return []
  const groups = new Map<string, typeof cards>()
  for (const [index, card] of cards.entries()) {
    const name = cleanProjectTitle(card.name, groups.size)
    const lineKey = card.sourceRef?.startLine || card.sourceRef?.endLine
      ? `line-${card.sourceRef?.startLine ?? ''}-${card.sourceRef?.endLine ?? ''}`
      : ''
    const key = name && !isGenericProjectTitle(name) ? name : lineKey || `generic-project-${index}`
    groups.set(key, [...(groups.get(key) ?? []), card])
  }
  return Array.from(groups.values()).map((items, index) => {
    const techStack = uniqueStrings(items.flatMap((card) => card.techStack ?? [])).filter(isSkillTag)
    const allText = uniqueStrings(items.flatMap((card) => [
      card.name || '',
      card.summary || '',
      ...(card.responsibilities ?? []),
    ])).filter(isUsefulLine)
    const sourceRefs = items.map((card) => card.sourceRef)
    const sourceText = mergeSourceRefText(sourceRefs) || allText.join('\n')
    const name = cleanProjectTitle(items.map((card) => card.name || '').find((value) => {
      const cleaned = cleanProjectTitle(value, index)
      return cleaned && !isGenericProjectTitle(cleaned)
    }), index) || cleanProjectTitle(allText.find((line) => /项目|系统|平台|SRTP/i.test(line)), index) || `项目经历 ${index + 1}`
    let responsibilities = sanitizeDetails(summarizeLines(allText), [name], techStack)
    const summary = cleanSupplementText(stripLongLine(items.map((card) => card.summary || '').find(Boolean) || responsibilities[0] || ''), [name, ...responsibilities.slice(1)])
    responsibilities = summary ? sanitizeDetails(responsibilities, [summary, name], techStack) : responsibilities
    return {
      id: `display-project-${index}`,
      name,
      summary,
      sourceText,
      sourceLineRange: sourceLineRangeFromRefs(sourceRefs),
      techStack,
      role: '',
      responsibilities,
      hiddenCount: Math.max(responsibilities.length - 3, 0),
    }
  }).filter((card) => isUsefulLine(card.name) || isUsefulLine(card.summary) || card.responsibilities.length > 0)
}

const finalizeProjectCards = (cards: ResumeProjectCard[]) => {
  const result: ResumeProjectCard[] = []
  for (const card of cards) {
    const normalizedCard = {
      ...card,
      name: cleanProjectTitle(card.name, result.length) || '项目经历',
      summary: cleanSupplementText(card.summary, [card.name, ...card.responsibilities, ...card.techStack]),
      responsibilities: sanitizeDetails(card.responsibilities, [card.name, card.summary], card.techStack),
      techStack: uniqueStrings(card.techStack).filter(isSkillTag),
    }
    if (!isDisplayableProjectCard(normalizedCard)) {
      continue
    }
    const duplicateIndex = result.findIndex((item) => isDuplicateProjectCard(item, normalizedCard))
    if (duplicateIndex >= 0) {
      const existing = result[duplicateIndex]
      if (existing) {
        result[duplicateIndex] = mergeProjectCard(existing, normalizedCard)
      }
      continue
    }
    result.push(normalizedCard)
  }
  return result
}

const isDisplayableProjectCard = (card: ResumeProjectCard) => {
  const reliableTitle = Boolean(card.name && card.name !== '项目经历' && !isGenericProjectTitle(card.name))
  const hasSummary = isUsefulLine(card.summary)
  const hasResponsibilities = card.responsibilities.length > 0
  const hasTechStack = card.techStack.length > 0
  if (reliableTitle) {
    return hasSummary || hasResponsibilities || hasTechStack
  }
  return hasSummary && hasResponsibilities
}

const isDuplicateProjectCard = (left: ResumeProjectCard, right: ResumeProjectCard) => {
  const leftRange = left.sourceLineRange
  const rightRange = right.sourceLineRange
  if (leftRange?.startLine != null && leftRange?.endLine != null
    && leftRange.startLine === rightRange?.startLine
    && leftRange.endLine === rightRange?.endLine) {
    return true
  }
  const leftName = cleanProjectTitle(left.name, 0)
  const rightName = cleanProjectTitle(right.name, 0)
  if (leftName && rightName && isDuplicateDisplayText(leftName, [rightName])) {
    return true
  }
  return Boolean(left.summary && right.summary && isDuplicateDisplayText(left.summary, [right.summary]))
}

const mergeProjectCard = (left: ResumeProjectCard, right: ResumeProjectCard): ResumeProjectCard => {
  const preferred = projectCardScore(right) > projectCardScore(left) ? right : left
  const secondary = preferred === right ? left : right
  const responsibilities = sanitizeDetails(
    uniqueStrings([...preferred.responsibilities, ...secondary.responsibilities]),
    [preferred.name, preferred.summary],
    [...preferred.techStack, ...secondary.techStack]
  )
  return {
    ...preferred,
    summary: preferred.summary || secondary.summary,
    sourceText: preferred.sourceText.length >= secondary.sourceText.length ? preferred.sourceText : secondary.sourceText,
    techStack: uniqueStrings([...preferred.techStack, ...secondary.techStack]).filter(isSkillTag),
    responsibilities,
    hiddenCount: Math.max(responsibilities.length - 3, 0),
  }
}

const projectCardScore = (card: ResumeProjectCard) => {
  let score = 0
  if (card.name && card.name !== '项目经历' && !isGenericProjectTitle(card.name)) score += 5
  if (card.summary) score += 3
  score += Math.min(card.techStack.length, 4)
  score += Math.min(card.responsibilities.length, 4)
  return score
}

const buildBasicInfoFields = (
  content: ResumeStructuredContent | null,
  basicInfo: Record<string, string>,
  debug: Record<string, ResumeBasicInfoField>,
) => {
  const values: Record<string, string | null | undefined> = {
    ...basicInfo,
    name: content?.name ?? basicInfo.name,
    phone: content?.phone ?? basicInfo.phone,
    email: content?.email ?? basicInfo.email,
    jobIntention: content?.jobIntention ?? basicInfo.jobIntention,
    highestEducation: content?.highestEducation ?? basicInfo.degree,
    resumeType: content?.resumeType ?? basicInfo.resumeType,
  }

  return BASIC_INFO_FIELDS
    .map((field) => {
      const value = reliableBasicFieldValue(field.key, values[field.key], debug[field.key], field.core)
      return value ? { key: field.key, label: field.label, value } : null
    })
    .filter((item): item is ResumeDisplayField => item !== null)
}

const reliableBasicFieldValue = (
  key: string,
  value: string | null | undefined,
  debug: ResumeBasicInfoField | undefined,
  core?: boolean,
) => {
  const normalized = (value ?? '').trim()
  if (!normalized) {
    return core ? '未识别' : ''
  }
  if (debug?.status && debug.status !== 'CONFIRMED') {
    return core ? '未识别' : ''
  }
  if (debug?.confidence != null && debug.confidence < 0.55) {
    return core ? '未识别' : ''
  }
  if (key === 'age') {
    const age = Number(normalized)
    if (!Number.isFinite(age) || age < 16 || age > 60) {
      return ''
    }
  }
  if (key === 'name' && !isReliableName(normalized)) {
    return '未识别'
  }
  return normalized
}

const buildSkillGroups = (groups: Record<string, string[]> | null | undefined, keywords: string[]) => {
  const normalizedGroups: Record<string, string[]> = {
    backend: [],
    frontend: [],
    database: [],
    middleware: [],
    tool: [],
    ai: [],
    other: [],
  }
  const allSkills = [
    ...Object.values(groups ?? {}).flat(),
    ...keywords,
  ]

  for (const skill of uniqueStrings(allSkills).filter(isSkillTag)) {
    const groupKey = resolveSkillGroup(skill)
    normalizedGroups[groupKey]?.push(skill)
  }

  return Object.entries(normalizedGroups)
    .map(([key, skills]) => ({
      key,
      label: GROUP_LABELS[key] ?? key,
      skills: uniqueStrings(skills),
      previewSkills: uniqueStrings(skills).slice(0, 8),
      hiddenCount: Math.max(uniqueStrings(skills).length - 8, 0),
    }))
    .filter((group) => group.skills.length > 0)
}

const resolveSkillGroup = (skill: string) => {
  if (BACKEND_SKILLS.has(skill)) return 'backend'
  if (FRONTEND_SKILLS.has(skill)) return 'frontend'
  if (DATABASE_SKILLS.has(skill)) return 'database'
  if (MIDDLEWARE_SKILLS.has(skill)) return 'middleware'
  if (TOOL_SKILLS.has(skill)) return 'tool'
  if (AI_SKILLS.has(skill)) return 'ai'
  return 'other'
}

const buildEducationCards = (items: string[], basicInfo: Record<string, string>) => {
  const usefulItems = uniqueStrings(items).filter(isUsefulEducationLine)
  if (!usefulItems.length && !basicInfo.school && !basicInfo.university) {
    return []
  }
  const merged = usefulItems.join(' ')
  const school = basicInfo.university || basicInfo.school || extractSchool(merged) || '未识别'
  const degree = basicInfo.degree || extractDegree(merged)
  const major = basicInfo.major || extractMajor(merged)
  const timeRange = extractTimeRange(merged)
  const extras = uniqueStrings(usefulItems)
    .map((item) => cleanSupplementText(item, [school, degree, major, timeRange]))
    .filter(isUsefulLine)
    .slice(0, 2)

  return [{
    id: `edu-${school}-${timeRange || 'unknown'}`,
    school,
      degreeMajor: cleanSupplementText([degree, major].filter(Boolean).join(' / '), [school, timeRange]) || '未识别',
      timeRange: validTimeRange(timeRange) ? timeRange : '',
      extras,
  }]
}

const buildExperienceCards = (
  experiences: ResumeExperience[],
  types: string[],
  fallback: string[] | null | undefined,
) => {
  const matched = experiences.filter((item) => item.type && types.includes(item.type))
  if (matched.length) {
    return aggregateExperienceCards(matched, types[0] ?? 'UNKNOWN')
  }
  return aggregateFallbackExperienceLines(fallback ?? [], types[0] ?? 'UNKNOWN')
}

const buildCampusCards = (experiences: ResumeExperience[], fallback: string[] | null | undefined) => {
  const cards = buildExperienceCards(experiences, ['CAMPUS', 'PRACTICE', 'VOLUNTEER'], fallback)
  if (cards.length <= 1) {
    return cards
  }
  const details = sanitizeDetails(uniqueStrings(cards.flatMap((card) => [card.summary, ...card.details])), ['校园 / 实践经历'])
    .filter(isUsefulLine)
  const sourceText = uniqueStrings(cards.map((card) => card.sourceText)).filter(isUsefulLine).join('\n')
  return [{
    id: 'campus-merged',
    type: 'CAMPUS',
    organization: '校园 / 实践经历',
    role: '',
    timeRange: '',
    summary: details[0] ?? '',
    details: details.slice(1),
    sourceText,
    sourceLineRange: null,
    hiddenCount: Math.max(details.slice(1).length - 3, 0),
  }]
}

const aggregateExperienceCards = (experiences: ResumeExperience[], defaultType: string) => {
  const groups = new Map<string, ResumeExperience[]>()
  for (const item of experiences) {
    const groupKey = [
      item.sourceSectionId || '',
      item.organization || extractOrganization(item.description ?? ''),
      formatDateRange(item.startDate, item.endDate) || extractTimeRange(item.description ?? ''),
    ].filter(Boolean).join('|') || 'single'
    groups.set(groupKey, [...(groups.get(groupKey) ?? []), item])
  }

  return Array.from(groups.values()).map((items, index) => {
    const lines = uniqueStrings(items.flatMap((item) => [
      item.description ?? '',
      ...(item.bullets ?? []),
      ...(item.evidence ?? []),
    ])).map(cleanFieldLine).filter(isUsefulLine)
    const sourceRefs = items.map((item) => item.sourceRef)
    const sourceText = mergeSourceRefText(sourceRefs) || lines.join('\n')
    const joined = lines.join(' ')
    const organization = cleanCardTitle(firstNonBlank(
      ...items.map((item) => item.organization),
      extractOrganization(joined),
      defaultType === 'WORK' ? `工作经历 ${index + 1}` : `经历 ${index + 1}`
    ))
    const rawRole = firstNonBlank(...items.map((item) => item.role), extractRole(joined))
    const timeRange = firstNonBlank(
      ...items.map((item) => formatDateRange(item.startDate, item.endDate)),
      extractTimeRange(joined)
    )
    const role = [cleanSupplementText(rawRole, [organization, timeRange, ...lines]), timeRange].filter(Boolean).join(' · ')
    let details = sanitizeDetails(summarizeLines(lines), [organization, role, timeRange])
    const summary = cleanSupplementText(details[0] || '', [organization, role, ...details.slice(1)])
    details = summary ? sanitizeDetails(details, [summary, organization, role, timeRange]) : details
    return {
      id: `${defaultType}-${index}`,
      type: defaultType,
      organization,
      role,
      timeRange: '',
      summary,
      details,
      sourceText,
      sourceLineRange: sourceLineRangeFromRefs(sourceRefs),
      hiddenCount: Math.max(details.length - 3, 0),
    }
  })
}

const aggregateFallbackExperienceLines = (values: string[], defaultType: string) => {
  const lines = uniqueStrings(values).map(cleanFieldLine).filter(isUsefulLine)
  if (!lines.length) {
    return []
  }
  const joined = lines.join(' ')
  const organization = cleanCardTitle(extractOrganization(joined) || (defaultType === 'WORK' ? '工作经历 1' : '经历 1'))
  const timeRange = extractTimeRange(joined)
  const role = [cleanSupplementText(extractRole(joined), [organization, timeRange, ...lines]), timeRange].filter(Boolean).join(' · ')
  let details = sanitizeDetails(summarizeLines(lines), [organization, role, timeRange])
  const summary = cleanSupplementText(details[0] || '', [organization, role, ...details.slice(1)])
  details = summary ? sanitizeDetails(details, [summary, organization, role, timeRange]) : details
  return [{
    id: `fallback-${defaultType}-0`,
    type: defaultType,
    organization,
    role,
    timeRange: '',
    summary,
    details,
    sourceText: lines.join('\n'),
    sourceLineRange: null,
    hiddenCount: Math.max(details.length - 3, 0),
  }]
}

const buildProjectCards = (projects: ResumeProject[], fallback: string[] | null | undefined) => {
  if (projects.length) {
    const groups = new Map<string, ResumeProject[]>()
    for (const [index, project] of projects.entries()) {
      const name = cleanProjectTitle(project.name, index)
      const lineKey = project.sourceRef?.startLine || project.sourceRef?.endLine
        ? `line-${project.sourceRef?.startLine ?? ''}-${project.sourceRef?.endLine ?? ''}`
        : ''
      const key = name && !isGenericProjectTitle(name)
        ? name
        : lineKey || project.parentExperienceIndex?.toString() || `project-${index}`
      groups.set(key, [...(groups.get(key) ?? []), project])
    }
    return Array.from(groups.values()).map((items, index) => {
      const primary = items[0]
      const evidence = uniqueStrings(items.flatMap((project) => [
        project.description ?? '',
        ...(project.evidence ?? []),
      ]))
      const techStack = uniqueStrings(items.flatMap((project) => project.techStack ?? [])).filter(isSkillTag)
      let responsibilities = sanitizeDetails(summarizeLines(items.flatMap((project) => (
        project.responsibilities?.length ? project.responsibilities : project.evidence ?? []
      ))), [], techStack)
      const sourceRefs = items.map((project) => project.sourceRef)
      const sourceText = mergeSourceRefText(sourceRefs) || evidence.join('\n')
      const name = cleanProjectTitle(primary?.name, index) || cleanProjectTitle(primary?.description, index) || '项目经历'
      const role = uniqueStrings(items.flatMap((project) => [project.role ?? '', project.mentor ? `导师：${project.mentor}` : '']))
        .map((item) => cleanSupplementText(item, [name, ...responsibilities]))
        .filter(Boolean)
        .join(' · ')
      const summary = cleanSupplementText(stripLongLine(primary?.description || evidence[0] || ''), [name, role, ...responsibilities, ...techStack])
      responsibilities = summary ? sanitizeDetails(responsibilities, [summary, name, role], techStack) : responsibilities
      return {
        id: `project-${index}`,
        name,
        summary,
        sourceText,
        sourceLineRange: sourceLineRangeFromRefs(sourceRefs),
        techStack,
        role,
        responsibilities,
        hiddenCount: Math.max(responsibilities.length - 3, 0),
      }
    }).filter((item) => item.name || item.summary)
  }
  const lines = uniqueStrings(fallback ?? []).map(cleanFieldLine).filter(isUsefulLine)
  if (!lines.length) {
    return []
  }
  const techStack = uniqueStrings(lines.flatMap((line) => extractInlineSkills(line))).filter(isSkillTag)
  let responsibilities = sanitizeDetails(summarizeLines(lines), [], techStack)
  const sourceText = lines.join('；')
  const name = cleanProjectTitle(lines.find((line) => /项目|系统|平台|SRTP/i.test(line)), 0) || '项目经历'
  const summary = cleanSupplementText(stripLongLine(lines.find((line) => !isLabelOnly(line)) ?? ''), [name, ...responsibilities])
  responsibilities = summary ? sanitizeDetails(responsibilities, [summary, name], techStack) : responsibilities
  return [{
    id: 'fallback-project-0',
    name,
    summary,
    sourceText,
    sourceLineRange: null,
    techStack,
    role: '',
    responsibilities,
    hiddenCount: Math.max(responsibilities.length - 3, 0),
  }]
}

const buildAchievementCards = (achievements: ResumeAchievement[], fallback: string[] | null | undefined) => {
  if (achievements.length) {
    return achievements.map((item, index) => ({
      id: `achievement-${index}`,
      title: item.title || item.evidence?.[0] || '未命名成果',
      meta: [item.level, item.competition, item.ranking, item.timeRange || item.date].filter(Boolean).join(' · '),
    })).filter((item) => isUsefulLine(item.title))
  }
  return uniqueStrings(fallback ?? []).filter(isUsefulLine).map((item, index) => ({
    id: `achievement-fallback-${index}`,
    title: item,
    meta: extractTimeRange(item),
  }))
}

const buildSummaryCard = (summary: string | null | undefined, sourceText?: string | null) => {
  const text = (summary ?? '').trim()
  if (!text) return null
  if (isSkillFragmentText(text)) return null
  const cleanSourceText = sanitizeSummarySourceText(sourceText, text)
  const fullText = cleanSourceText || text
  const preview = text.length > 120 ? `${text.slice(0, 120)}...` : text
  return {
    text,
    preview,
    fullText,
    expandable: text.length > 120 || !isDuplicateDisplayText(fullText, [text]),
  }
}

const buildPendingItems = (items: string[], displayedTexts: Set<string>) => {
  return uniqueStrings(items)
    .filter(isUsefulLine)
    .filter((item) => !isLabelOnly(item))
    .filter((item) => !displayedTexts.has(normalizeComparable(item)))
    .map((item) => stripLongLine(item))
    .slice(0, 20)
}

const summarizeLines = (items: string[]) => uniqueStrings(items)
  .map(cleanFieldLine)
  .map(stripLongLine)
  .filter(isUsefulLine)
  .filter((item) => !isLabelOnly(item))
  .filter((item) => item.length >= 4)

const sanitizeDetails = (items: string[], references: string[] = [], tags: string[] = []) => uniqueStrings(items)
  .map(cleanFieldLine)
  .map(stripLongLine)
  .filter(isUsefulLine)
  .filter((item) => !isLowQualityDetail(item, references, tags))
  .slice(0, 12)

const isLowQualityDetail = (value: string, references: string[] = [], tags: string[] = []) => {
  const cleaned = cleanDisplayText(value)
  if (!cleaned) return true
  if (isLabelOnly(cleaned) || DETAIL_LABEL_PREFIX_PATTERN.test(cleaned)) return true
  if (isDuplicateDisplayText(cleaned, references)) return true
  if (tags.some((tag) => isDuplicateDisplayText(cleaned, [tag]))) return true
  if (isSkillOnlyFragment(cleaned)) return true
  if (cleaned.length < 6 && !/(负责|参与|开发|实现|完成|组织|获得|担任|研究|优化|设计|维护|编写)/.test(cleaned)) return true
  return false
}

const isSkillOnlyFragment = (value: string) => {
  const cleaned = cleanDisplayText(value)
  if (!cleaned) return true
  if (/^(Oracle|MySQL|Redis|MongoDB|JavaScript|Java|Ajax|jQuery|Vue|React|HTML|CSS|Spring|Spring Boot|Spring MVC|MyBatis|Tomcat|Nginx|Maven|IDEA|Linux|Docker|Git|SQL)$/i.test(cleaned)) {
    return true
  }
  if (/^[A-Za-z0-9+#.\s,，、/\\-]+$/.test(cleaned) && /[,，、/\\\s]/.test(cleaned) && !/(负责|参与|开发|实现|完成|设计|优化|维护|编写)/i.test(cleaned)) {
    return true
  }
  return false
}

const isSkillFragmentText = (value: string) => {
  const parts = value
    .split(/[\n,，、;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)
  if (parts.length < 2) {
    return isSkillOnlyFragment(value) && !/(能力|经验|熟悉|精通|掌握|了解|负责|参与|开发|工作|团队|抗压|学习|沟通)/.test(value)
  }
  const skillLikeCount = parts.filter(isSkillOnlyFragment).length
  return skillLikeCount / parts.length >= 0.75
}

const sanitizeSummarySourceText = (sourceText: string | null | undefined, summary: string) => {
  const source = (sourceText ?? '').trim()
  if (!source || isSkillFragmentText(source)) return ''
  if (isDuplicateDisplayText(source, [summary])) return ''
  return source
}

const stripLongLine = (value: string) => {
  const cleaned = cleanDisplayText(value)
  return cleaned.length > 160 ? `${cleaned.slice(0, 160)}...` : cleaned
}

const cleanCardTitle = (value: string | null | undefined) => {
  const cleaned = cleanDisplayText(value ?? '')
  if (isForbiddenMainText(cleaned)) {
    return ''
  }
  return cleaned
}

const cleanSupplementText = (value: string | null | undefined, references: string[] = []) => {
  const cleaned = cleanDisplayText(value ?? '')
  if (isForbiddenMainText(cleaned)) {
    return ''
  }
  if (references.some((reference) => isDuplicateDisplayText(cleaned, [reference]))) {
    return ''
  }
  return cleaned
}

const isForbiddenMainText = (value: string) => {
  const cleaned = value.trim()
  if (!isUsefulLine(cleaned)) return true
  if (isLabelOnly(cleaned)) return true
  if (isGenericExperienceTitle(cleaned) || isGenericProjectTitle(cleaned)) return true
  return /未识别|公司未识别|职位未识别/.test(cleaned)
}

const isGenericExperienceTitle = (value: string) => /^(工作经历|实习经历|经历)\s*[2-9]\d*$/.test(value.trim())

const isGenericProjectTitle = (value: string) => /^项目经历\s*\d*$/.test(value.trim())

const isDuplicateDisplayText = (value: string, references: string[]) => {
  const target = normalizeComparable(value)
  if (!target || target.length <= 1) return true
  return references.some((reference) => {
    const source = normalizeComparable(reference)
    if (!source || source.length <= 1) return false
    if (target === source) return true
    const shorter = target.length <= source.length ? target : source
    const longer = target.length > source.length ? target : source
    return shorter.length >= 4 && longer.includes(shorter)
  })
}

const cleanDisplayText = (value: string) => value
  .replace(/^(项目名称|项目描述|技术栈|技术选型|开发环境|开发工具|软件构架|软件架构|负责模块|参与项目描述|职责|工作内容|公司名称|职位名称|工作时间|工作描述)[:：]\s*/, '')
  .replace(/\s+/g, ' ')
  .trim()

const cleanFieldLine = (value: string) => cleanDisplayText(value)
  .replace(/^(公司名称|职位名称|工作时间|工作描述|项目名称|项目描述|开发环境|开发工具|软件构架|软件架构|技术选型|负责模块|参与项目描述)[:：]?\s*$/, '')
  .trim()

const cleanProjectTitle = (value: string | null | undefined, _index: number) => {
  const cleaned = cleanDisplayText(value ?? '')
  if (!cleaned || isLabelOnly(cleaned) || cleaned === '未识别') {
    return ''
  }
  const candidate = cleaned.split(/[，,；;。]/)[0]?.trim() || cleaned
  if (isInvalidProjectTitle(candidate)) {
    return ''
  }
  return candidate
}

const isInvalidProjectTitle = (value: string) => {
  const cleaned = cleanDisplayText(value)
  if (!cleaned || isLabelOnly(cleaned)) return true
  if (/^项目经历\s*\d*$/.test(cleaned)) return true
  if (cleaned.length > 36 || /[。！？!?；;]/.test(cleaned)) return true
  if (/^(负责|参与|使用|采用|通过|实现|开发|编写|维护|优化|设计|管理|完成|做|对|是一个|该系统|该项目|主要|为了|左右)/.test(cleaned)) return true
  if (/(是一个|该系统|该项目|采用|通过|使用|负责|参与|实现|开发|编写|维护|优化|设计|左右代码|其余代码|交给系统|自动生成)/.test(cleaned)) return true
  return false
}

const extractSchool = (value: string) => value.match(/[\u4e00-\u9fa5]{2,}(?:大学|学院|学校|职业学院|工学院)/)?.[0] ?? ''
const extractDegree = (value: string) => value.match(/博士|硕士|研究生|本科|大专|专科|高中/)?.[0] ?? ''
const extractMajor = (value: string) => value.match(/(?:专业[:：]?\s*)?([\u4e00-\u9fa5A-Za-z0-9+ #.-]{2,20})(?:专业|,|，|预计毕业|毕业)/)?.[1]?.trim() ?? ''
const extractOrganization = (value: string) => value.match(/[\u4e00-\u9fa5A-Za-z0-9（）()]{2,}(?:公司|集团|大学|学院|学校|实验室|中心|协会|社团)/)?.[0] ?? ''
const extractRole = (value: string) => value.match(/(?:Java|Python|前端|后端|算法|软件)?(?:开发)?(?:工程师|实习生|负责人|组长|成员|经理|专员)/)?.[0] ?? ''
const extractTimeRange = (value: string) => {
  const matched = value.match(/(?:19|20)\d{2}(?:\s*[./年-]\s*\d{1,2}\s*月?)?\s*(?:[-~—–至到]+)\s*(?:(?:19|20)\d{2}(?:\s*[./年-]\s*\d{1,2}\s*月?)?|至今|Present)/i)?.[0] ?? ''
  return validTimeRange(matched) ? matched.replace(/\s+/g, '') : ''
}

const formatDateRange = (start?: string | null, end?: string | null) => {
  if (!start && !end) return ''
  return [start, end].filter(Boolean).join(' - ')
}

const validTimeRange = (value: string) => {
  if (!value) return false
  if (/^\d{1,2}$|^\d{1,2}-\d{1,4}$/.test(value)) return false
  return /(?:19|20)\d{2}|至今|Present/i.test(value)
}

const isUsefulEducationLine = (value: string) => {
  const cleaned = value.trim()
  if (!isUsefulLine(cleaned)) return false
  if (isLabelOnly(cleaned)) return false
  if (/^\d{1,2}$|^\d{1,2}-\d{1,4}$/.test(cleaned)) return false
  return true
}

const isUsefulLine = (value: string) => {
  const cleaned = value.trim()
  if (!cleaned) return false
  if (/^[\d一二三四五六七八九十]+[.、．)]?$/.test(cleaned)) return false
  if (/^[\s\-_=+*#·•。.,，、;；:：|/\\[\]()（）]+$/.test(cleaned)) return false
  return true
}

const isLabelOnly = (value: string) => /^(毕业院校|学历|专业|学校|时间|项目名称|项目描述|技术栈|技术选型|开发环境|开发工具|软件构架|软件架构|负责模块|参与项目描述|公司名称|职位名称|工作时间|工作描述|邮箱|电话|姓名|未识别|rawText|evidence|sourceText|sourceSection|description|summary|subtitle)[:：]?$/.test(value.trim())

const isSkillTag = (value: string) => {
  const cleaned = value.trim()
  if (!cleaned || cleaned.length > 32 || /[。！？!?]/.test(cleaned)) return false
  if (/(具备|熟悉|精通|负责|参与|掌握|了解|本人|能力|经验|项目|开发过程|设计思想)/.test(cleaned) && cleaned.length > 18) {
    return false
  }
  return /[A-Za-z0-9+#.]|后端|前端|数据库|算法|多线程|集合|框架|脚本|测试|运维|容器|消息队列/.test(cleaned)
}

const extractInlineSkills = (value: string) => {
  const matched = value.match(/[A-Za-z][A-Za-z0-9+#.-]{0,24}/g) ?? []
  return matched
}

const isReliableName = (value: string) => {
  if (!value || value.length < 2 || value.length > 8) return false
  if (/[0-9@.:：/\\|,，;；]/.test(value)) return false
  return !/(姓名|个人简历|简历|求职|岗位|电话|邮箱|手机|学校|学院|大学|专业|项目|经历|技能|教育|证书|奖项|自我评价|参加项目描述|本人)/.test(value)
}

const uniqueStrings = (items: string[] | null | undefined) => {
  const seen = new Set<string>()
  const result: string[] = []
  for (const item of items ?? []) {
    const cleaned = cleanDisplayText(String(item ?? ''))
    const key = cleaned.toLowerCase()
    if (cleaned && !seen.has(key)) {
      seen.add(key)
      result.push(cleaned)
    }
  }
  return result
}

const mergeSourceRefText = (refs: Array<ResumeSourceRef | null | undefined>) => {
  const seen = new Set<string>()
  const texts: string[] = []
  for (const ref of refs) {
    const text = (ref?.text ?? '').trim()
    if (!isUsefulLine(text)) {
      continue
    }
    const key = normalizeComparable(text)
    if (key && !seen.has(key)) {
      seen.add(key)
      texts.push(text)
    }
  }
  return texts.join('\n')
}

const sourceLineRangeFromRefs = (refs: Array<ResumeSourceRef | null | undefined>) => {
  const ranges = refs
    .filter((ref): ref is ResumeSourceRef => Boolean(ref?.startLine != null && ref?.endLine != null))
  if (!ranges.length) {
    return null
  }
  return {
    startLine: Math.min(...ranges.map((ref) => Number(ref.startLine))),
    endLine: Math.max(...ranges.map((ref) => Number(ref.endLine))),
  }
}

const collectDisplayedTexts = (model: {
  educationCards: ResumeEducationCard[]
  skillGroups: ResumeSkillGroupDisplay[]
  experienceCards: ResumeExperienceCard[]
  internshipCards: ResumeExperienceCard[]
  campusCards: ResumeExperienceCard[]
  projectCards: ResumeProjectCard[]
  achievementCards: ResumeAchievementCard[]
  certificateTags: string[]
  summaryCard: ResumeSummaryCard | null
}) => {
  const values = [
    ...model.educationCards.flatMap((card) => [card.school, card.degreeMajor, card.timeRange, ...card.extras]),
    ...model.skillGroups.flatMap((group) => group.skills),
    ...model.experienceCards.flatMap((card) => [card.organization, card.role, card.timeRange, card.summary, ...card.details]),
    ...model.internshipCards.flatMap((card) => [card.organization, card.role, card.timeRange, card.summary, ...card.details]),
    ...model.campusCards.flatMap((card) => [card.organization, card.role, card.timeRange, card.summary, ...card.details]),
    ...model.projectCards.flatMap((card) => [card.name, card.summary, card.role, ...card.techStack, ...card.responsibilities]),
    ...model.achievementCards.flatMap((card) => [card.title, card.meta]),
    ...model.certificateTags,
    model.summaryCard?.text ?? '',
  ]
  return new Set(values.map(normalizeComparable).filter(Boolean))
}

const normalizeComparable = (value: string) => cleanDisplayText(value).replace(/[\s,，、；;:：.。/\\|()[\]（）【】]/g, '').toLowerCase()

const sameCompact = (left: string, right: string) => {
  if (!left || !right) return false
  return normalizeComparable(left) === normalizeComparable(right)
}

const firstNonBlank = (...values: Array<string | null | undefined>) => {
  return values.find((value) => value && value.trim())?.trim() ?? ''
}
