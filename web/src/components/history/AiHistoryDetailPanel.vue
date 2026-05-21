<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'
import JobParseResult from '@/components/job/JobParseResult.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import type { AiResultDetail, AiResultType } from '@/types/history'
import type { JobDescriptionStructuredContent } from '@/types/job-description'
import {
  getFirstValidText,
  hasDisplayValue,
  parseJsonValue,
  splitTextSegments,
  toRecordArray,
  toTextArray,
  truncateText,
} from '@/utils/display'

interface DetailMetaItem {
  label: string
  value: string
}

interface MatchItem {
  item: string
  reason: string
}

interface SuggestionItem {
  priority: string
  type: string
  targetSection: string
  issue: string
  suggestion: string
  evidence: string[]
  caution: string | null
}

const props = defineProps<{
  detail: AiResultDetail | null
  loading?: boolean
  compact?: boolean
}>()

const emit = defineEmits<{
  'go-resume': []
  'go-job': []
  'go-match': []
}>()

const expandedOriginal = ref(false)
const expandedRewritten = ref(false)

const resultTypeLabel = computed(() => {
  const map: Record<AiResultType, string> = {
    RESUME_DIAGNOSIS: '简历诊断',
    TARGET_JOB_PARSE: '目标岗位解析',
    MATCH_ANALYSIS: '匹配分析',
    JOB_OPTIMIZATION_SUGGESTION: '岗位优化建议',
    LOCAL_REWRITE: '局部改写',
  }

  return props.detail?.resultType ? map[props.detail.resultType] ?? props.detail.resultType : '-'
})

const primaryActionLabel = computed(() => {
  const type = props.detail?.resultType
  if (type === 'RESUME_DIAGNOSIS') {
    return '回到我的简历'
  }
  if (type === 'TARGET_JOB_PARSE') {
    return '回到目标岗位'
  }
  return '回到匹配与优化'
})

const primaryAction = () => {
  const type = props.detail?.resultType
  if (type === 'RESUME_DIAGNOSIS') {
    emit('go-resume')
    return
  }
  if (type === 'TARGET_JOB_PARSE') {
    emit('go-job')
    return
  }
  emit('go-match')
}

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const toNumber = (value: unknown) => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const numberValue = Number(value)
    return Number.isFinite(numberValue) ? numberValue : null
  }
  return null
}

const toMatchItems = (value: unknown): MatchItem[] => {
  return toRecordArray(value)
    .map((item) => ({
      item: getFirstValidText(item.item, item.section, item.title, item.name),
      reason: getFirstValidText(item.reason, item.issue, item.content, item.description),
    }))
    .filter((item) => hasDisplayValue(item.item) || hasDisplayValue(item.reason))
    .slice(0, 5)
}

const toSuggestionItems = (value: unknown): SuggestionItem[] => {
  return toRecordArray(value)
    .map((item) => ({
      priority: getFirstValidText(item.priority, 'LOW'),
      type: getFirstValidText(item.type, 'GENERAL'),
      targetSection: getFirstValidText(item.targetSection),
      issue: getFirstValidText(item.issue),
      suggestion: getFirstValidText(item.suggestion),
      evidence: toTextArray(item.evidence).slice(0, 3),
      caution: getFirstValidText(item.caution) || null,
    }))
    .filter((item) => hasDisplayValue(item.issue) || hasDisplayValue(item.suggestion))
}

const resumeDiagnosis = computed(() => {
  const content = props.detail?.content ?? {}
  return {
    score: toNumber(content.score),
    strengths: toTextArray(content.strengths).slice(0, 3),
    problems: toTextArray(content.problems).slice(0, 3),
    suggestions: toTextArray(content.suggestionsSummary).slice(0, 3),
  }
})

const targetJobContent = computed<JobDescriptionStructuredContent | null>(() => {
  const rawStructuredContent = props.detail?.content?.structuredContent
  const parsed = parseJsonValue<Record<string, unknown>>(rawStructuredContent)
  if (!parsed) {
    if (typeof rawStructuredContent === 'string' && rawStructuredContent.trim()) {
      const normalized = rawStructuredContent.trim()
      return {
        jobTitle: '',
        requiredSkills: [],
        bonusSkills: [],
        experienceSignals: [],
        responsibilities: [],
        keywords: [],
        summary: normalized.startsWith('{') || normalized.startsWith('[')
          ? '已生成结构化结果，进入详情查看。'
          : truncateText(normalized, 240),
      }
    }
    return null
  }

  return {
    jobTitle: getFirstValidText(parsed.jobTitle),
    requiredSkills: toTextArray(parsed.requiredSkills).slice(0, 8),
    bonusSkills: toTextArray(parsed.bonusSkills).slice(0, 8),
    experienceSignals: toTextArray(parsed.experienceSignals).slice(0, 8),
    responsibilities: toTextArray(parsed.responsibilities).slice(0, 8),
    keywords: toTextArray(parsed.keywords).slice(0, 8),
    summary: getFirstValidText(parsed.summary),
  }
})

const matchAnalysis = computed(() => {
  const content = props.detail?.content ?? {}
  const score = toNumber(content.overallScore)
  const strongMatches = toMatchItems(content.strongMatches)
  const weakMatches = toMatchItems(content.weakMatches)
  const missingSkills = toMatchItems(content.missingSkills)
  const weakExperienceDescriptions = toRecordArray(content.weakExperienceDescriptions)
    .map((item) => ({
      section: getFirstValidText(item.section, item.title),
      issue: getFirstValidText(item.issue, item.reason, item.content),
    }))
    .filter((item) => hasDisplayValue(item.section) || hasDisplayValue(item.issue))
    .slice(0, 3)
  const evidence = toRecordArray(content.evidence)
    .map((item) => ({
      source: getFirstValidText(item.source, 'resume'),
      content: getFirstValidText(item.content, item.text),
    }))
    .filter((item) => hasDisplayValue(item.content))
    .slice(0, 5)
  const riskNotes = toTextArray(content.riskNotes).slice(0, 5)
  const firstMissingSkill = missingSkills[0]
  const firstWeakMatch = weakMatches[0]
  const firstWeakExperience = weakExperienceDescriptions[0]

  const nextSteps: string[] = []
  if (firstMissingSkill) {
    nextSteps.push(`优先补齐 ${firstMissingSkill.item || '缺失技能'}。`)
  }
  if (firstWeakMatch) {
    nextSteps.push(`继续优化 ${firstWeakMatch.item || '弱匹配项'} 的表达。`)
  }
  if (riskNotes.length > 0) {
    nextSteps.push(`先处理风险提醒，再进入岗位优化建议。`)
  }
  if (firstWeakExperience) {
    nextSteps.push(`补充 ${firstWeakExperience.section || '薄弱经历'} 的表达。`)
  }
  if (nextSteps.length === 0) {
    nextSteps.push('进入岗位优化建议，继续细化简历与岗位的匹配表达。')
  }

  return {
    score,
    strongMatches,
    weakMatches,
    missingSkills,
    weakExperienceDescriptions,
    evidence,
    riskNotes,
    nextSteps,
  }
})

const optimizationSuggestions = computed(() => {
  const rawSuggestions = toRecordArray(props.detail?.content?.suggestions)
    .map((item) => ({
      priority: getFirstValidText(item.priority, 'LOW'),
      type: getFirstValidText(item.type, 'GENERAL'),
      targetSection: getFirstValidText(item.targetSection),
      issue: getFirstValidText(item.issue),
      suggestion: getFirstValidText(item.suggestion),
      evidence: toTextArray(item.evidence).slice(0, 3),
      caution: getFirstValidText(item.caution) || null,
    }))
    .filter((item) => hasDisplayValue(item.issue) || hasDisplayValue(item.suggestion))

  const sortOrder: Record<string, number> = {
    HIGH: 0,
    MEDIUM: 1,
    LOW: 2,
  }

  const sorted = [...rawSuggestions].sort((left, right) => (sortOrder[left.priority] ?? 9) - (sortOrder[right.priority] ?? 9))

  return {
    high: sorted.filter((item) => item.priority === 'HIGH').slice(0, 3),
    medium: sorted.filter((item) => item.priority === 'MEDIUM').slice(0, 3),
    low: sorted.filter((item) => item.priority === 'LOW').slice(0, 3),
    other: sorted.filter((item) => item.priority === 'LOW').slice(0, 3),
    summary: sorted.length > 0
      ? `共有 ${sorted.length} 条建议，优先处理高优先级项。`
      : '暂无岗位优化建议。',
  }
})

const rewriteDetail = computed(() => {
  const content = props.detail?.content ?? {}
  const targetSection = getFirstValidText(content.targetSection, '简历片段')
  const rewriteType = getFirstValidText(content.rewriteType, 'OTHER')
  const originalText = getFirstValidText(content.originalText)
  const rewrittenText = getFirstValidText(content.rewrittenText)
  const rewriteReason = getFirstValidText(content.rewriteReason)
  const caution = getFirstValidText(content.caution)
  const acceptStatus = getFirstValidText(content.acceptStatus, props.detail?.status)

  const summary = getFirstValidText(
    rewriteReason ? truncateText(rewriteReason, 120) : '',
    `本次改写优化了 ${targetSection} 的表达，并尽量保留原文事实。`,
  )

  const reasonItems = splitTextSegments(rewriteReason).slice(0, 3)
  if (!reasonItems.length && rewriteReason) {
    reasonItems.push(rewriteReason)
  }

  return {
    targetSection,
    rewriteType,
    originalText,
    rewrittenText,
    rewriteReason,
    caution,
    acceptStatus,
    summary,
    reasonItems,
  }
})

const activeMetaCards = computed<DetailMetaItem[]>(() => {
  const detail = props.detail
  if (!detail) {
    return []
  }

  const cards: DetailMetaItem[] = []
  if (detail.resumeName) {
    cards.push({ label: '关联简历', value: detail.resumeName })
  }
  if (detail.jobTitle) {
    cards.push({ label: '目标岗位', value: detail.jobTitle })
  }
  cards.push({ label: '更新时间', value: formatDateTime(detail.updatedAt) })
  return cards.filter((item) => hasDisplayValue(item.value))
})

const detailTitle = computed(() => {
  if (!props.detail) {
    return '-'
  }

  if (props.detail.resultType === 'LOCAL_REWRITE') {
    return `${resultTypeLabel.value} · ${rewriteDetail.value.targetSection || '局部改写'}`
  }

  return props.detail.title || resultTypeLabel.value
})

const detailSummary = computed(() => {
  if (!props.detail) {
    return ''
  }

  if (props.detail.resultType === 'RESUME_DIAGNOSIS') {
    const score = resumeDiagnosis.value.score
    const parts = []
    if (score !== null) {
      parts.push(`当前诊断评分 ${score} 分。`)
    }
    if (resumeDiagnosis.value.strengths[0]) {
      parts.push(`主要优势：${resumeDiagnosis.value.strengths[0]}。`)
    }
    if (resumeDiagnosis.value.problems[0]) {
      parts.push(`主要问题：${resumeDiagnosis.value.problems[0]}。`)
    }
    return parts.join(' ') || '这份简历已完成诊断。'
  }

  if (props.detail.resultType === 'TARGET_JOB_PARSE') {
    const summary = targetJobContent.value?.summary
    if (summary) {
      return summary
    }
    const skills = targetJobContent.value?.requiredSkills?.slice(0, 3) ?? []
    return skills.length ? `该岗位重点关注 ${skills.join('、')}。` : '该岗位已完成解析。'
  }

  if (props.detail.resultType === 'MATCH_ANALYSIS') {
    const score = matchAnalysis.value.score
    const parts = []
    if (score !== null) {
      parts.push(`匹配分 ${score} 分。`)
    }
    if (matchAnalysis.value.missingSkills[0]) {
      parts.push(`优先补齐 ${matchAnalysis.value.missingSkills[0].item}。`)
    }
    if (matchAnalysis.value.weakMatches[0]) {
      parts.push(`继续优化 ${matchAnalysis.value.weakMatches[0].item} 的表达。`)
    }
    return parts.join(' ') || '匹配分析已完成。'
  }

  if (props.detail.resultType === 'JOB_OPTIMIZATION_SUGGESTION') {
    return optimizationSuggestions.value.summary
  }

  if (props.detail.resultType === 'LOCAL_REWRITE') {
    return rewriteDetail.value.summary
  }

  return props.detail.errorMessage || '已生成结构化结果，进入详情查看。'
})

const longOriginal = computed(() => rewriteDetail.value.originalText.length > 280)
const longRewritten = computed(() => rewriteDetail.value.rewrittenText.length > 280)

const displayedOriginalText = computed(() => {
  if (expandedOriginal.value || !longOriginal.value) {
    return rewriteDetail.value.originalText || '-'
  }
  return truncateText(rewriteDetail.value.originalText, 280) || '-'
})

const displayedRewrittenText = computed(() => {
  if (expandedRewritten.value || !longRewritten.value) {
    return rewriteDetail.value.rewrittenText || '-'
  }
  return truncateText(rewriteDetail.value.rewrittenText, 280) || '-'
})

const toggleOriginal = () => {
  expandedOriginal.value = !expandedOriginal.value
}

const toggleRewritten = () => {
  expandedRewritten.value = !expandedRewritten.value
}

watch(
  () => props.detail?.recordId,
  () => {
    expandedOriginal.value = false
    expandedRewritten.value = false
  },
)

const resultTypeText = (type: string | null | undefined) => {
  const map: Record<string, string> = {
    RESUME_DIAGNOSIS: '简历诊断',
    TARGET_JOB_PARSE: '目标岗位解析',
    MATCH_ANALYSIS: '匹配分析',
    JOB_OPTIMIZATION_SUGGESTION: '岗位优化建议',
    LOCAL_REWRITE: '局部改写',
  }

  return type ? map[type] ?? type : '-'
}

const rewriteTypeText = (type: string | null | undefined) => {
  const map: Record<string, string> = {
    PROJECT: '项目经历',
    SKILL: '技能标签',
    INTERNSHIP: '实习或工作经历',
    SUMMARY: '个人总结',
    EDUCATION: '教育经历',
    OTHER: '其他',
  }

  return type ? map[type] ?? type : '-'
}

const priorityText = (priority: string | null | undefined) => {
  const map: Record<string, string> = {
    HIGH: '高优先级',
    MEDIUM: '中优先级',
    LOW: '低优先级',
  }

  return priority ? map[priority] ?? priority : '-'
}

const suggestionTypeText = (type: string | null | undefined) => {
  const map: Record<string, string> = {
    SKILL_GAP: '技能缺口',
    EXPERIENCE_WEAKNESS: '经历表达不足',
    PROJECT_DESCRIPTION: '项目描述优化',
    HIGHLIGHT_STRENGTH: '优势突出',
    STRUCTURE: '结构优化',
    GENERAL: '综合建议',
  }

  return type ? map[type] ?? type : '-'
}

const matchItemText = (item: MatchItem) => {
  if (!item.item && !item.reason) {
    return '-'
  }
  if (!item.reason) {
    return item.item
  }
  return `${item.item}：${item.reason}`
}

const evidenceSourceText = (source: string | null | undefined) => {
  const map: Record<string, string> = {
    resume: '简历',
    job: '岗位',
    SKILL: '技能标签',
    skill: '技能标签',
  }

  return source ? map[source] ?? source : '-'
}

const evidenceSourceType = (source: string | null | undefined) => {
  if (source === 'job') {
    return 'warning'
  }

  if (source === 'resume') {
    return 'success'
  }

  return 'info'
}
</script>

<template>
  <section class="history-detail-panel" :class="{ 'is-compact': compact }">
    <SkeletonBlock v-if="loading" title :rows="8" />

    <EmptyState
      v-else-if="!detail"
      title="选择一条 AI 结果"
      description="右侧会按结果类型展示用户可读的结构化报告。"
      secondary-text="查看右侧摘要后，可继续返回对应业务页面。"
    />

    <template v-else>
      <section class="history-detail-hero">
        <div class="history-detail-heading">
          <div class="history-detail-title-row">
            <el-tag type="primary">{{ resultTypeLabel }}</el-tag>
            <StatusTag :status="detail.status" />
          </div>
          <h2>{{ detailTitle }}</h2>
          <p>{{ detailSummary }}</p>
        </div>

        <el-button type="primary" @click="primaryAction">
          {{ primaryActionLabel }}
        </el-button>
      </section>

      <div class="history-detail-meta-grid">
        <article v-for="item in activeMetaCards" :key="item.label" class="history-detail-meta-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>

      <el-alert
        v-if="detail.errorMessage"
        class="history-detail-alert"
        :title="detail.errorMessage"
        type="error"
        :closable="false"
        show-icon
      />

      <section v-if="detail.resultType === 'RESUME_DIAGNOSIS'" class="history-detail-section">
        <div class="history-detail-section-header">
          <h3>简历诊断</h3>
          <span v-if="resumeDiagnosis.score !== null" class="history-detail-score">评分 {{ resumeDiagnosis.score }}</span>
        </div>
        <div class="history-detail-grid">
          <article v-if="resumeDiagnosis.strengths.length" class="history-detail-card is-success">
            <h4>主要优势</h4>
            <ul>
              <li v-for="item in resumeDiagnosis.strengths" :key="item">{{ item }}</li>
            </ul>
          </article>
          <article v-if="resumeDiagnosis.problems.length" class="history-detail-card is-warning">
            <h4>主要问题</h4>
            <ul>
              <li v-for="item in resumeDiagnosis.problems" :key="item">{{ item }}</li>
            </ul>
          </article>
          <article v-if="resumeDiagnosis.suggestions.length" class="history-detail-card is-primary span-full">
            <h4>下一步建议</h4>
            <ul>
              <li v-for="item in resumeDiagnosis.suggestions" :key="item">{{ item }}</li>
            </ul>
          </article>
        </div>
      </section>

      <section v-else-if="detail.resultType === 'TARGET_JOB_PARSE'" class="history-detail-section">
        <div class="history-detail-section-header">
          <h3>目标岗位画像</h3>
        </div>
        <JobParseResult :content="targetJobContent" />
        <article v-if="getFirstValidText(detail.content.rawTextPreview)" class="history-detail-card span-full">
          <h4>JD 原文</h4>
          <p class="history-detail-text is-muted">
            {{ getFirstValidText(detail.content.rawTextPreview) }}
          </p>
        </article>
      </section>

      <section v-else-if="detail.resultType === 'MATCH_ANALYSIS'" class="history-detail-section">
        <div class="history-detail-section-header">
          <h3>匹配分析</h3>
          <span v-if="matchAnalysis.score !== null" class="history-detail-score">匹配分 {{ matchAnalysis.score }}</span>
        </div>
        <div class="history-detail-grid">
          <article v-if="matchAnalysis.strongMatches.length" class="history-detail-card is-success">
            <h4>强匹配项</h4>
            <ul>
              <li v-for="item in matchAnalysis.strongMatches" :key="`${item.item}-${item.reason}`">{{ matchItemText(item) }}</li>
            </ul>
          </article>
          <article v-if="matchAnalysis.weakMatches.length" class="history-detail-card is-warning">
            <h4>弱匹配项</h4>
            <ul>
              <li v-for="item in matchAnalysis.weakMatches" :key="`${item.item}-${item.reason}`">{{ matchItemText(item) }}</li>
            </ul>
          </article>
          <article v-if="matchAnalysis.missingSkills.length" class="history-detail-card is-danger">
            <h4>缺失技能</h4>
            <ul>
              <li v-for="item in matchAnalysis.missingSkills" :key="`${item.item}-${item.reason}`">{{ matchItemText(item) }}</li>
            </ul>
          </article>
          <article v-if="matchAnalysis.evidence.length" class="history-detail-card span-full">
            <h4>匹配依据</h4>
            <ul class="history-detail-bullet-list">
              <li v-for="item in matchAnalysis.evidence" :key="`${item.source}-${item.content}`">
                <el-tag size="small" :type="evidenceSourceType(item.source)">
                  {{ evidenceSourceText(item.source) }}
                </el-tag>
                <span>{{ item.content }}</span>
              </li>
            </ul>
          </article>
          <article v-if="matchAnalysis.riskNotes.length" class="history-detail-card is-danger span-full">
            <h4>风险提醒</h4>
            <ul>
              <li v-for="item in matchAnalysis.riskNotes" :key="item">{{ item }}</li>
            </ul>
          </article>
          <article class="history-detail-card is-primary span-full">
            <h4>下一步建议</h4>
            <ul>
              <li v-for="item in matchAnalysis.nextSteps" :key="item">{{ item }}</li>
            </ul>
          </article>
        </div>
      </section>

      <section v-else-if="detail.resultType === 'JOB_OPTIMIZATION_SUGGESTION'" class="history-detail-section">
        <div class="history-detail-section-header">
          <h3>岗位优化建议</h3>
        </div>
        <div class="history-detail-group-list">
          <article v-if="optimizationSuggestions.high.length" class="history-detail-group">
            <h4>高优先级建议</h4>
            <div class="history-detail-suggestion-list">
              <article v-for="item in optimizationSuggestions.high" :key="`${item.priority}-${item.type}-${item.issue}`" class="history-detail-suggestion-card">
                <div class="history-detail-suggestion-tags">
                  <el-tag size="small" type="danger">{{ priorityText(item.priority) }}</el-tag>
                  <el-tag size="small">{{ suggestionTypeText(item.type) }}</el-tag>
                  <el-tag v-if="item.targetSection" size="small" type="info">{{ item.targetSection }}</el-tag>
                </div>
                <p><strong>问题</strong>{{ item.issue }}</p>
                <p><strong>建议</strong>{{ item.suggestion }}</p>
                <p v-if="item.evidence.length" class="history-detail-suggestion-note">
                  <strong>依据</strong>{{ item.evidence.join('；') }}
                </p>
                <p v-if="item.caution" class="history-detail-suggestion-warning">
                  <strong>注意事项</strong>{{ item.caution }}
                </p>
              </article>
            </div>
          </article>

          <article v-if="optimizationSuggestions.medium.length" class="history-detail-group">
            <h4>中优先级建议</h4>
            <div class="history-detail-suggestion-list">
              <article v-for="item in optimizationSuggestions.medium" :key="`${item.priority}-${item.type}-${item.issue}`" class="history-detail-suggestion-card">
                <div class="history-detail-suggestion-tags">
                  <el-tag size="small" type="warning">{{ priorityText(item.priority) }}</el-tag>
                  <el-tag size="small">{{ suggestionTypeText(item.type) }}</el-tag>
                  <el-tag v-if="item.targetSection" size="small" type="info">{{ item.targetSection }}</el-tag>
                </div>
                <p><strong>问题</strong>{{ item.issue }}</p>
                <p><strong>建议</strong>{{ item.suggestion }}</p>
                <p v-if="item.evidence.length" class="history-detail-suggestion-note">
                  <strong>依据</strong>{{ item.evidence.join('；') }}
                </p>
                <p v-if="item.caution" class="history-detail-suggestion-warning">
                  <strong>注意事项</strong>{{ item.caution }}
                </p>
              </article>
            </div>
          </article>

          <article
            v-if="!optimizationSuggestions.high.length && !optimizationSuggestions.medium.length && optimizationSuggestions.low.length"
            class="history-detail-group"
          >
            <h4>其他建议</h4>
            <div class="history-detail-suggestion-list">
              <article v-for="item in optimizationSuggestions.other" :key="`${item.priority}-${item.type}-${item.issue}`" class="history-detail-suggestion-card">
                <div class="history-detail-suggestion-tags">
                  <el-tag size="small" type="info">{{ priorityText(item.priority) }}</el-tag>
                  <el-tag size="small">{{ suggestionTypeText(item.type) }}</el-tag>
                  <el-tag v-if="item.targetSection" size="small" type="info">{{ item.targetSection }}</el-tag>
                </div>
                <p><strong>问题</strong>{{ item.issue }}</p>
                <p><strong>建议</strong>{{ item.suggestion }}</p>
                <p v-if="item.evidence.length" class="history-detail-suggestion-note">
                  <strong>依据</strong>{{ item.evidence.join('；') }}
                </p>
                <p v-if="item.caution" class="history-detail-suggestion-warning">
                  <strong>注意事项</strong>{{ item.caution }}
                </p>
              </article>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="detail.resultType === 'LOCAL_REWRITE'" class="history-detail-section">
        <div class="history-detail-section-header">
          <h3>局部改写</h3>
          <el-space wrap>
            <span class="history-detail-chip">适用位置：{{ rewriteTypeText(rewriteDetail.rewriteType) }}</span>
            <StatusTag :status="rewriteDetail.acceptStatus" />
          </el-space>
        </div>
        <p class="history-detail-suggestion-summary">{{ rewriteDetail.summary }}</p>
        <div class="history-detail-grid">
          <article class="history-detail-card">
            <div class="history-detail-card-head">
              <h4>原文片段</h4>
              <el-button v-if="longOriginal" text type="primary" @click="toggleOriginal">
                {{ expandedOriginal ? '收起' : '展开全文' }}
              </el-button>
            </div>
            <p class="history-detail-text is-muted">{{ displayedOriginalText }}</p>
          </article>
          <article class="history-detail-card is-primary">
            <div class="history-detail-card-head">
              <h4>优化后表达</h4>
              <el-button v-if="longRewritten" text type="primary" @click="toggleRewritten">
                {{ expandedRewritten ? '收起' : '展开全文' }}
              </el-button>
            </div>
            <p class="history-detail-text is-highlight">{{ displayedRewrittenText }}</p>
          </article>
          <article v-if="rewriteDetail.reasonItems.length" class="history-detail-card span-full">
            <h4>为什么这样改</h4>
            <ul>
              <li v-for="item in rewriteDetail.reasonItems" :key="item">{{ item }}</li>
            </ul>
          </article>
          <article v-if="rewriteDetail.caution" class="history-detail-card is-danger span-full">
            <h4>风险提醒</h4>
            <p class="history-detail-text">{{ rewriteDetail.caution }}</p>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.history-detail-panel {
  display: grid;
  gap: 18px;
}

.history-detail-panel.is-compact {
  gap: 14px;
}

.history-detail-panel.is-compact .history-detail-hero,
.history-detail-panel.is-compact .history-detail-section {
  padding: 14px;
}

.history-detail-panel.is-compact .history-detail-card,
.history-detail-panel.is-compact .history-detail-group,
.history-detail-panel.is-compact .history-detail-suggestion-card {
  padding: 12px;
}

.history-detail-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.history-detail-heading {
  min-width: 0;
}

.history-detail-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.history-detail-heading h2 {
  margin: 0;
  color: var(--app-color-text);
  font-size: 20px;
  line-height: 1.4;
}

.history-detail-heading p {
  margin: 10px 0 0;
  color: var(--app-color-text-secondary);
  line-height: 1.7;
}

.history-detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.history-detail-meta-card {
  display: grid;
  gap: 5px;
  padding: 12px 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
}

.history-detail-meta-card span {
  color: var(--app-color-text-secondary);
  font-size: 12px;
}

.history-detail-meta-card strong {
  color: var(--app-color-text);
  font-size: 13px;
  line-height: 1.5;
}

.history-detail-alert {
  margin-top: 2px;
}

.history-detail-section {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.history-detail-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.history-detail-section-header h3 {
  margin: 0;
  color: var(--app-color-text);
  font-size: 16px;
  font-weight: 700;
}

.history-detail-score {
  color: var(--app-color-success);
  font-size: 13px;
  font-weight: 700;
}

.history-detail-grid,
.history-detail-group-list {
  display: grid;
  gap: 12px;
}

.history-detail-card,
.history-detail-group,
.history-detail-suggestion-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.history-detail-card.is-primary {
  background: var(--app-color-primary-soft);
}

.history-detail-card.is-success {
  background: rgba(22, 163, 74, 0.06);
}

.history-detail-card.is-warning {
  background: rgba(245, 158, 11, 0.08);
}

.history-detail-card.is-danger {
  background: rgba(220, 38, 38, 0.06);
}

.history-detail-card.span-full {
  grid-column: 1 / -1;
}

.history-detail-card h4,
.history-detail-group h4,
.history-detail-suggestion-card strong {
  margin: 0;
  color: var(--app-color-text);
  font-size: 14px;
  font-weight: 700;
}

.history-detail-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.history-detail-card ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  color: var(--app-color-text);
  line-height: 1.7;
}

.history-detail-bullet-list {
  list-style: none;
  padding: 0;
}

.history-detail-bullet-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.history-detail-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  background: var(--app-color-surface-soft);
}

.history-detail-suggestion-summary {
  margin: 0;
  color: var(--app-color-text-secondary);
  line-height: 1.7;
}

.history-detail-suggestion-list {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.history-detail-suggestion-card {
  display: grid;
  gap: 10px;
}

.history-detail-suggestion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.history-detail-suggestion-card p {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.history-detail-suggestion-card strong {
  margin-right: 8px;
}

.history-detail-suggestion-note {
  color: var(--app-color-text-secondary) !important;
}

.history-detail-suggestion-warning {
  color: var(--app-color-danger) !important;
}

.history-detail-text {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.history-detail-text.is-muted {
  color: var(--app-color-text-secondary);
}

.history-detail-text.is-highlight {
  color: var(--app-color-text);
}

@media (max-width: 760px) {
  .history-detail-hero,
  .history-detail-section-header,
  .history-detail-card-head {
    align-items: stretch;
    flex-direction: column;
  }

  .history-detail-meta-grid {
    grid-template-columns: 1fr;
  }

  .history-detail-card.span-full {
    grid-column: auto;
  }
}
</style>
