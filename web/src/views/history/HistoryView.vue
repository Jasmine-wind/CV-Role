<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import AiHistoryDetailPanel from '@/components/history/AiHistoryDetailPanel.vue'
import { getAiResultDetail, getAiResultPage } from '@/api/history'
import type { AiResultDetail, AiResultRecord } from '@/types/history'
import { truncateText } from '@/utils/display'

const router = useRouter()

const records = ref<AiResultRecord[]>([])
const activeRecord = ref<AiResultRecord | null>(null)
const activeDetail = ref<AiResultDetail | null>(null)
const loading = ref(false)
const loadingDetail = ref(false)
const detailDrawerVisible = ref(false)
const compactDetailMode = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const resultType = ref('')
const status = ref('')
const keyword = ref('')

const resultTypeOptions = [
  { label: '全部', value: '' },
  { label: '简历诊断', value: 'RESUME_DIAGNOSIS' },
  { label: '目标岗位解析', value: 'TARGET_JOB_PARSE' },
  { label: '匹配分析', value: 'MATCH_ANALYSIS' },
  { label: '优化建议', value: 'JOB_OPTIMIZATION_SUGGESTION' },
  { label: '局部改写', value: 'LOCAL_REWRITE' },
]

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '处理中', value: 'PENDING' },
]

const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())
const displayedRecords = computed(() => {
  if (!normalizedKeyword.value) {
    return records.value
  }

  return records.value.filter((item) => {
    const searchableText = [
      item.title,
      item.summary,
      item.resumeName,
      item.jobTitle,
      resolveResultTypeText(item.resultType),
      resolveStatusText(item.status),
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()

    return searchableText.includes(normalizedKeyword.value)
  })
})
const hasRecords = computed(() => records.value.length > 0)
const hasDisplayedRecords = computed(() => displayedRecords.value.length > 0)
const inlineDetailVisible = computed(() => !compactDetailMode.value)

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const normalizeSummary = (summary: string | null | undefined, fallback = '暂无摘要') => {
  const value = summary?.trim()
  if (!value) {
    return fallback
  }

  if (value.startsWith('{') || value.startsWith('[')) {
    return '已生成结构化结果，进入详情查看。'
  }

  return truncateText(value, 96) || fallback
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

const loadHistory = async () => {
  loading.value = true

  try {
    const result = await getAiResultPage({
      resultType: resultType.value || undefined,
      status: status.value || undefined,
      page: page.value,
      size: size.value,
    })
    records.value = result.records
    total.value = result.total
    if (activeRecord.value && !records.value.some((item) => item.recordId === activeRecord.value?.recordId && item.resultType === activeRecord.value?.resultType)) {
      activeRecord.value = null
      activeDetail.value = null
    }
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
  activeRecord.value = record
  if (compactDetailMode.value) {
    detailDrawerVisible.value = true
  }

  try {
    activeDetail.value = await getAiResultDetail(record.resultType, record.recordId)
  } catch (error) {
    activeDetail.value = null
    ElMessage.error(error instanceof Error ? error.message : '获取 AI 结果详情失败')
  } finally {
    loadingDetail.value = false
  }
}

let compactDetailMediaQuery: MediaQueryList | null = null

const syncCompactDetailMode = () => {
  compactDetailMode.value = Boolean(compactDetailMediaQuery?.matches)
  if (compactDetailMode.value) {
    detailDrawerVisible.value = Boolean(activeDetail.value)
  } else {
    detailDrawerVisible.value = false
  }
}

const openResume = (resumeId: number | null) => {
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

const openJobDescription = (jobDescriptionId: number | null) => {
  if (!jobDescriptionId) {
    return
  }

  router.push(`/job-descriptions/${jobDescriptionId}`)
}

const openMatch = (resumeId: number | null, jobDescriptionId: number | null) => {
  if (!resumeId || !jobDescriptionId) {
    ElMessage.warning('当前 AI 结果缺少简历或目标岗位关联')
    return
  }

  router.push({
    path: '/ai-job-matches',
    query: {
      resumeId: String(resumeId),
      jobDescriptionId: String(jobDescriptionId),
    },
  })
}

const handleGoResume = () => {
  openResume(activeDetail.value?.resumeId ?? null)
}

const handleGoJob = () => {
  openJobDescription(activeDetail.value?.jobDescriptionId ?? null)
}

const handleGoMatch = () => {
  openMatch(activeDetail.value?.resumeId ?? null, activeDetail.value?.jobDescriptionId ?? null)
}

onMounted(() => {
  compactDetailMediaQuery = window.matchMedia('(max-width: 1199px)')
  syncCompactDetailMode()
  compactDetailMediaQuery.addEventListener('change', syncCompactDetailMode)
  loadHistory()
})

onUnmounted(() => {
  compactDetailMediaQuery?.removeEventListener('change', syncCompactDetailMode)
})
</script>

<template>
  <section class="history-page">
    <section class="history-shell">
      <PageHeader
        eyebrow="AI 历史"
        title="回看已保存的 AI 结果"
        description="继续处理未完成的优化任务。"
      />

      <section class="history-toolbar" aria-label="AI 历史筛选">
        <div class="history-toolbar-main">
          <el-radio-group v-model="resultType" class="history-type-tabs" @change="search">
            <el-radio-button
              v-for="item in resultTypeOptions"
              :key="item.value"
              :label="item.value"
            >
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <div class="history-toolbar-actions">
          <label class="history-status-filter">
            <span>状态</span>
            <el-select v-model="status" class="history-status-select" @change="search">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </label>
          <el-input
            v-model="keyword"
            class="history-search-input"
            clearable
            placeholder="搜索标题 / 简历 / 岗位"
          />
          <el-button type="primary" :loading="loading" @click="loadHistory">刷新</el-button>
        </div>
      </section>

      <section class="history-workspace">
        <section class="history-list-panel">
          <header class="history-panel-header">
            <div>
              <h2 class="history-section-title">历史结果</h2>
              <p>{{ keyword ? `本页匹配 ${displayedRecords.length} 条，全部结果 ${total} 条。` : `共 ${total} 条结果。` }}</p>
            </div>
            <el-button v-if="keyword" text type="primary" @click="keyword = ''">清空搜索</el-button>
          </header>

          <section class="history-list-scroll">
            <SkeletonBlock v-if="loading" title :rows="6" />

            <div v-else-if="hasDisplayedRecords" class="history-result-list">
              <article
                v-for="row in displayedRecords"
                :key="`${row.resultType}-${row.recordId}`"
                class="history-result-card"
                :class="{ 'is-active': activeRecord?.recordId === row.recordId && activeRecord?.resultType === row.resultType }"
              >
                <button type="button" class="history-result-main" @click="openDetail(row)">
                  <div class="history-cell-stack">
                    <div class="history-title-line">
                      <el-tag size="small" type="primary">{{ resolveResultTypeText(row.resultType) }}</el-tag>
                      <el-tag size="small" :type="resolveStatusType(row.status)">
                        {{ resolveStatusText(row.status) }}
                      </el-tag>
                    </div>
                    <strong>{{ row.title || '-' }}</strong>
                    <span class="history-card-summary">{{ normalizeSummary(row.summary) }}</span>
                  </div>
                  <small>{{ formatDateTime(row.updatedAt || row.createdAt) }}</small>
                </button>

                <div class="history-result-meta">
                  <span>简历：{{ row.resumeName || '-' }}</span>
                  <span>目标岗位：{{ row.jobTitle || '-' }}</span>
                </div>
              </article>
            </div>

            <EmptyState
              v-else-if="hasRecords"
              title="没有匹配结果"
              description="换一个关键词，或清空搜索后继续查看本页结果。"
              secondary-text="搜索只在当前页结果中筛选。"
              action-text="清空搜索"
              @action="keyword = ''"
            />

            <EmptyState
              v-else
              title="暂无 AI 历史"
              description="完成简历诊断、目标岗位解析、匹配分析或局部改写后，会在这里回看。"
              secondary-text="下一步建议：先上传并解析一份简历。"
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

        <section v-if="inlineDetailVisible" class="history-detail-panel">
          <section class="history-detail-scroll">
            <AiHistoryDetailPanel
              :detail="activeDetail"
              :loading="loadingDetail"
              :compact="false"
              @go-resume="handleGoResume"
              @go-job="handleGoJob"
              @go-match="handleGoMatch"
            />
          </section>
        </section>
      </section>

      <el-drawer
        v-model="detailDrawerVisible"
        title="AI 结果详情"
        size="min(520px, 92vw)"
        destroy-on-close
      >
        <AiHistoryDetailPanel
          :detail="activeDetail"
          :loading="loadingDetail"
          :compact="true"
          @go-resume="handleGoResume"
          @go-job="handleGoJob"
          @go-match="handleGoMatch"
        />
      </el-drawer>
    </section>
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
  gap: 18px;
  width: min(100%, 1480px);
  margin: 0 auto;
  min-width: 0;
}

.history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-soft);
}

.history-toolbar-main,
.history-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.history-toolbar-main {
  flex: 1 1 auto;
  overflow-x: auto;
  padding-bottom: 1px;
}

.history-toolbar-actions {
  flex: 0 0 auto;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.history-type-tabs {
  min-width: max-content;
}

.history-type-tabs :deep(.el-radio-button__inner) {
  min-width: 68px;
  border-color: var(--app-color-border);
  font-size: 13px;
  font-weight: 650;
}

.history-status-filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  font-weight: 650;
}

.history-status-select {
  width: 132px;
}

.history-search-input {
  width: clamp(220px, 20vw, 300px);
}

.history-workspace {
  display: grid;
  grid-template-columns: 460px minmax(620px, 1fr);
  gap: 20px;
  min-width: 0;
  min-height: 620px;
  height: calc(100dvh - 236px);
  align-items: stretch;
}

.history-list-panel,
.history-detail-panel {
  display: grid;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.history-list-panel {
  grid-template-rows: auto minmax(0, 1fr);
}

.history-detail-panel {
  align-self: stretch;
}

.history-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 18px 18px 14px;
  border-bottom: 1px solid var(--app-color-border);
}

.history-panel-header p {
  margin: 4px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.history-section-title {
  margin: 0;
  color: var(--app-color-text);
  font-size: 16px;
  font-weight: 700;
}

.history-list-scroll,
.history-detail-scroll {
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
}

.history-list-scroll {
  padding: 14px 14px 0;
}

.history-detail-scroll {
  padding: 18px;
}

.history-result-list {
  display: grid;
  gap: 10px;
}

.history-result-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.history-result-card.is-active {
  border-color: rgba(37, 99, 235, 0.28);
  background: var(--app-color-primary-soft);
}

.history-result-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  width: 100%;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.history-result-main small {
  flex: 0 0 auto;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  white-space: nowrap;
}

.history-result-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.history-result-meta span {
  overflow: hidden;
  min-width: 0;
  padding: 7px 9px;
  border-radius: 10px;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--app-color-surface);
}

.history-cell-stack {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: var(--app-color-text);
  font-size: 13px;
  line-height: 1.5;
}

.history-cell-stack strong,
.history-cell-stack span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-cell-stack strong {
  white-space: nowrap;
}

.history-title-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.history-card-summary {
  display: -webkit-box;
  color: var(--app-color-text-secondary);
  line-height: 1.55;
  white-space: normal;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.history-pagination {
  display: flex;
  position: sticky;
  bottom: 0;
  justify-content: flex-end;
  margin: 14px -14px 0;
  padding: 12px 14px;
  border-top: 1px solid var(--app-color-border);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(8px);
}

@media (max-width: 1439px) {
  .history-workspace {
    grid-template-columns: 400px minmax(0, 1fr);
    gap: 18px;
  }
}

@media (max-width: 1199px) {
  .history-workspace {
    grid-template-columns: 1fr;
    height: calc(100dvh - 246px);
  }

  .history-detail-panel {
    display: none;
  }
}

@media (max-width: 960px) {
  .history-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .history-toolbar-main,
  .history-toolbar-actions {
    width: 100%;
  }

  .history-toolbar-actions {
    justify-content: flex-start;
  }

  .history-search-input {
    flex: 1 1 260px;
    width: auto;
  }
}

@media (max-width: 768px) {
  .history-toolbar-actions,
  .history-status-filter {
    align-items: stretch;
    flex-direction: column;
  }

  .history-status-filter {
    width: 100%;
  }

  .history-status-select,
  .history-search-input {
    width: 100%;
  }

  .history-workspace {
    min-height: 560px;
    height: calc(100dvh - 300px);
  }

  .history-result-main {
    flex-direction: column;
    gap: 8px;
  }

  .history-result-main small {
    white-space: normal;
  }

  .history-result-meta {
    grid-template-columns: 1fr;
  }

  .history-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .history-list-scroll {
    padding: 12px 12px 0;
  }
}
</style>
