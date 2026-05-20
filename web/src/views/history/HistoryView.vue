<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import AiHistoryDetailDrawer from '@/components/history/AiHistoryDetailDrawer.vue'
import { getAiResultDetail, getAiResultPage } from '@/api/history'
import type { AiResultDetail, AiResultRecord } from '@/types/history'

const router = useRouter()

const records = ref<AiResultRecord[]>([])
const activeDetail = ref<AiResultDetail | null>(null)
const loading = ref(false)
const loadingDetail = ref(false)
const detailVisible = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const resultType = ref('')
const status = ref('')
const resumeIdInput = ref('')
const jobDescriptionIdInput = ref('')

const resultTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '简历诊断', value: 'RESUME_DIAGNOSIS' },
  { label: '目标岗位解析', value: 'TARGET_JOB_PARSE' },
  { label: '匹配分析', value: 'MATCH_ANALYSIS' },
  { label: '岗位优化建议', value: 'JOB_OPTIMIZATION_SUGGESTION' },
  { label: '局部改写', value: 'LOCAL_REWRITE' },
]

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '处理中', value: 'PENDING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

const hasRecords = computed(() => records.value.length > 0)

const detailEntries = computed(() => {
  if (!activeDetail.value?.content) {
    return []
  }

  return Object.entries(activeDetail.value.content)
})

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const parseOptionalId = (value: string) => {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }

  const parsed = Number(trimmed)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const resolveResultTypeText = (type: string | null | undefined) => {
  const typeMap: Record<string, string> = {
    RESUME_DIAGNOSIS: '简历诊断',
    TARGET_JOB_PARSE: '目标岗位解析',
    MATCH_ANALYSIS: '匹配分析',
    JOB_OPTIMIZATION_SUGGESTION: '岗位优化建议',
    LOCAL_REWRITE: '局部改写',
  }

  return type ? (typeMap[type] ?? type) : '-'
}

const resolveStatusText = (value: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    PENDING: '处理中',
    SUCCESS: '成功',
    FAILED: '失败',
  }

  return value ? (statusMap[value] ?? value) : '-'
}

const resolveStatusType = (value: string | null | undefined) => {
  if (value === 'SUCCESS') {
    return 'success'
  }

  if (value === 'FAILED') {
    return 'danger'
  }

  if (value === 'PENDING') {
    return 'warning'
  }

  return 'info'
}

const formatContentKey = (key: string) => {
  const keyMap: Record<string, string> = {
    score: '评分',
    strengths: '优势',
    problems: '问题',
    suggestionsSummary: '建议摘要',
    rawTextPreview: '原文摘要',
    structuredContent: '解析结果',
    overallScore: '匹配分数',
    strongMatches: '强匹配',
    weakMatches: '弱匹配',
    missingSkills: '缺失技能',
    weakExperienceDescriptions: '表达较弱经历',
    evidence: '依据',
    riskNotes: '风险提示',
    aiJobMatchResultId: '匹配结果 ID',
    suggestions: '优化建议',
    aiResumeSuggestionId: '优化建议 ID',
    rewriteType: '改写类型',
    targetSection: '目标章节',
    originalText: '原文',
    rewrittenText: '改写文本',
    rewriteReason: '改写理由',
    caution: '注意事项',
    acceptStatus: '采纳状态',
  }

  return keyMap[key] ?? key
}

const formatContentValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  return JSON.stringify(value, null, 2)
}

const loadHistory = async () => {
  loading.value = true

  try {
    const result = await getAiResultPage({
      resultType: resultType.value || undefined,
      status: status.value || undefined,
      resumeId: parseOptionalId(resumeIdInput.value),
      jobDescriptionId: parseOptionalId(jobDescriptionIdInput.value),
      page: page.value,
      size: size.value,
    })
    records.value = result.records
    total.value = result.total
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(error instanceof Error ? error.message : '获取 AI 历史失败')
  } finally {
    loading.value = false
  }
}

const search = async () => {
  page.value = 1
  await loadHistory()
}

const resetFilters = async () => {
  resultType.value = ''
  status.value = ''
  resumeIdInput.value = ''
  jobDescriptionIdInput.value = ''
  page.value = 1
  await loadHistory()
}

const handlePageChange = async (nextPage: number) => {
  page.value = nextPage
  await loadHistory()
}

const handleSizeChange = async (nextSize: number) => {
  size.value = nextSize
  page.value = 1
  await loadHistory()
}

const openDetail = async (record: AiResultRecord) => {
  loadingDetail.value = true
  detailVisible.value = true

  try {
    activeDetail.value = await getAiResultDetail(record.resultType, record.recordId)
  } catch (error) {
    activeDetail.value = null
    detailVisible.value = false
    ElMessage.error(error instanceof Error ? error.message : '获取 AI 结果详情失败')
  } finally {
    loadingDetail.value = false
  }
}

const goResume = (resumeId: number | null) => {
  if (!resumeId) {
    return
  }

  router.push({
    path: '/resumes',
    query: {
      resumeId: String(resumeId),
    },
  })
}

const goJobDescription = (jobDescriptionId: number | null) => {
  if (!jobDescriptionId) {
    return
  }

  router.push(`/job-descriptions/${jobDescriptionId}`)
}

const goMatch = (record: AiResultRecord) => {
  if (!record.resumeId || !record.jobDescriptionId) {
    ElMessage.warning('当前 AI 结果缺少简历或目标岗位关联')
    return
  }

  router.push({
    path: '/ai-job-matches',
    query: {
      resumeId: String(record.resumeId),
      jobDescriptionId: String(record.jobDescriptionId),
    },
  })
}

onMounted(() => {
  loadHistory()
})
</script>

<template>
  <section class="history-page">
    <section class="history-shell">
      <PageHeader
        eyebrow="AI 历史"
        title="回看已保存的 AI 结果"
        description="历史页只做查询和详情回看，不触发新的 AI 生成。需要继续处理时跳回对应业务页面。"
      >
        <template #actions>
          <el-button type="primary" :loading="loading" @click="loadHistory">刷新</el-button>
        </template>
      </PageHeader>

      <el-alert
        class="history-boundary-alert"
        title="AI 历史只回看已保存结果，不会触发新的 AI 生成。"
        type="info"
        :closable="false"
        show-icon
      />

      <section class="history-filter-panel">
        <el-form class="history-filter-form" label-position="top">
          <el-form-item label="结果类型">
            <el-select v-model="resultType" class="history-filter-control">
              <el-option
                v-for="item in resultTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="status" class="history-filter-control">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="简历 ID">
            <el-input v-model="resumeIdInput" class="history-filter-control" placeholder="可选" clearable />
          </el-form-item>
          <el-form-item label="目标岗位 ID">
            <el-input v-model="jobDescriptionIdInput" class="history-filter-control" placeholder="可选" clearable />
          </el-form-item>
          <el-form-item label="操作">
            <el-space>
              <el-button type="primary" @click="search">筛选</el-button>
              <el-button @click="resetFilters">重置</el-button>
            </el-space>
          </el-form-item>
        </el-form>
      </section>

      <section v-loading="loading" class="history-table-panel">
        <div v-if="hasRecords" class="history-result-list">
          <article
            v-for="row in records"
            :key="`${row.resultType}-${row.recordId}`"
            class="history-result-card"
          >
            <header>
              <div class="history-cell-stack">
                <div class="history-title-line">
                  <el-tag size="small" type="primary">{{ resolveResultTypeText(row.resultType) }}</el-tag>
                  <el-tag size="small" :type="resolveStatusType(row.status)">
                    {{ resolveStatusText(row.status) }}
                  </el-tag>
                </div>
                <strong>{{ row.title || '-' }}</strong>
                <span>{{ row.summary || '-' }}</span>
              </div>
              <small>{{ formatDateTime(row.updatedAt || row.createdAt) }}</small>
            </header>

            <div class="history-result-meta">
              <span>简历：{{ row.resumeName || '-' }}</span>
              <span>目标岗位：{{ row.jobTitle || '-' }}</span>
              <span>模型：{{ row.modelName || '-' }}</span>
              <span>Prompt：{{ row.promptVersion || '-' }}</span>
            </div>

            <footer class="history-actions">
              <el-button size="small" @click="openDetail(row)">详情</el-button>
              <el-button size="small" :disabled="!row.resumeId" @click="goResume(row.resumeId)">简历</el-button>
              <el-button
                size="small"
                :disabled="!row.jobDescriptionId"
                @click="goJobDescription(row.jobDescriptionId)"
              >
                目标岗位
              </el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="!row.resumeId || !row.jobDescriptionId"
                @click="goMatch(row)"
              >
                匹配与优化
              </el-button>
            </footer>
          </article>
        </div>

        <EmptyState
          v-if="!loading && !hasRecords"
          title="暂无 AI 历史"
          description="完成简历诊断、目标岗位解析、匹配分析或局部改写后，会在这里回看。"
          action-text="去我的简历"
          @action="router.push('/resumes')"
        />

        <div v-if="total > 0" class="history-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="page"
            :page-size="size"
            :page-sizes="[10, 20, 50]"
            :total="total"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </section>
    </section>

    <AiHistoryDetailDrawer v-model="detailVisible">
      <section v-loading="loadingDetail" class="history-detail">
        <el-empty v-if="!activeDetail" description="暂无详情" :image-size="80" />

        <template v-else>
          <section class="history-detail-section">
            <div class="history-detail-header">
              <div>
                <h2 class="history-section-title">{{ activeDetail.title || '-' }}</h2>
                <el-space wrap>
                  <el-tag type="primary">{{ resolveResultTypeText(activeDetail.resultType) }}</el-tag>
                  <el-tag :type="resolveStatusType(activeDetail.status)">
                    {{ resolveStatusText(activeDetail.status) }}
                  </el-tag>
                </el-space>
              </div>
            </div>
            <el-descriptions :column="1" border class="history-meta">
              <el-descriptions-item label="关联简历">{{ activeDetail.resumeName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="目标岗位">{{ activeDetail.jobTitle || '-' }}</el-descriptions-item>
              <el-descriptions-item label="模型">{{ activeDetail.modelName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Prompt 版本">{{ activeDetail.promptVersion || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDateTime(activeDetail.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDateTime(activeDetail.updatedAt) }}</el-descriptions-item>
            </el-descriptions>
            <el-alert
              v-if="activeDetail.errorMessage"
              :title="activeDetail.errorMessage"
              type="error"
              :closable="false"
              show-icon
              class="history-error"
            />
          </section>

          <section class="history-detail-section">
            <h2 class="history-section-title">结构化内容</h2>
            <div class="history-content-list">
              <article
                v-for="[key, value] in detailEntries"
                :key="key"
                class="history-content-item"
              >
                <h3 class="history-content-title">{{ formatContentKey(key) }}</h3>
                <pre class="history-content-value">{{ formatContentValue(value) }}</pre>
              </article>
            </div>
          </section>
        </template>
      </section>
    </AiHistoryDetailDrawer>
  </section>
</template>

<style scoped>
.history-page {
  min-height: 0;
  padding: 0;
  background: transparent;
}

.history-shell {
  display: grid;
  gap: 16px;
  width: 100%;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.history-title {
  margin: 0;
  color: var(--app-color-text);
  font-size: 28px;
  font-weight: 700;
}

.history-subtitle {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.history-boundary-alert {
  margin-bottom: 16px;
}

.history-filter-panel,
.history-table-panel {
  padding: 24px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.history-filter-panel {
  margin-bottom: 16px;
}

.history-filter-form {
  display: grid;
  grid-template-columns: repeat(5, minmax(150px, 1fr));
  gap: 14px;
  align-items: end;
}

.history-filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.history-filter-control {
  width: 100%;
}

.history-table {
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
}

.history-result-list {
  display: grid;
  gap: 12px;
}

.history-result-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--app-color-border);
  border-radius: 16px;
  background: var(--app-color-surface-soft);
}

.history-result-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.history-result-card header small {
  flex: 0 0 auto;
  color: var(--app-color-text-secondary);
}

.history-result-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.history-result-meta span {
  overflow: hidden;
  padding: 8px 10px;
  border-radius: 10px;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--app-color-surface);
}

.history-cell-stack {
  display: grid;
  gap: 4px;
  color: var(--app-color-text);
  font-size: 13px;
  line-height: 1.5;
}

.history-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.history-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.history-detail {
  min-height: 360px;
}

.history-detail-section + .history-detail-section {
  margin-top: 24px;
}

.history-detail-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.history-section-title {
  margin: 0 0 12px;
  color: var(--app-color-text);
  font-size: 16px;
  font-weight: 700;
}

.history-meta {
  margin-top: 16px;
}

.history-error {
  margin-top: 16px;
}

.history-content-list {
  display: grid;
  gap: 12px;
}

.history-content-item {
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.history-content-title {
  margin: 0 0 8px;
  color: var(--app-color-text);
  font-size: 14px;
  font-weight: 700;
}

.history-content-value {
  margin: 0;
  color: var(--app-color-text);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 960px) {
  .history-filter-form {
    grid-template-columns: repeat(2, minmax(150px, 1fr));
  }
}

@media (max-width: 760px) {
  .history-header {
    align-items: stretch;
    flex-direction: column;
  }

  .history-filter-panel,
  .history-table-panel {
    padding: 16px;
  }

  .history-filter-form {
    grid-template-columns: 1fr;
  }

  .history-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
