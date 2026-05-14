<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHistoryDetail, getHistoryPage } from '@/api/history'
import type { HistoryDetail, HistoryListItem, HistoryMatchResult } from '@/types/history'

const router = useRouter()

const records = ref<HistoryListItem[]>([])
const activeDetail = ref<HistoryDetail | null>(null)
const loading = ref(false)
const loadingDetail = ref(false)
const detailVisible = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const hasRecords = computed(() => records.value.length > 0)

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const formatFileSize = (value: number | null | undefined) => {
  if (!value) {
    return '-'
  }

  if (value >= 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(2)} MB`
  }

  return `${(value / 1024).toFixed(1)} KB`
}

const resolveParseStatusText = (status: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    NOT_STARTED: '未解析',
    PENDING: '待解析',
    PROCESSING: '解析中',
    SUCCESS: '解析成功',
    FAILED: '解析失败',
  }

  return status ? (statusMap[status] ?? status) : '未解析'
}

const resolveAnalysisStatusText = (status: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    NOT_STARTED: '未分析',
    PENDING: '待分析',
    SUCCESS: '分析成功',
    FAILED: '分析失败',
  }

  return status ? (statusMap[status] ?? status) : '未分析'
}

const resolveStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }

  if (status === 'FAILED') {
    return 'danger'
  }

  if (status === 'PENDING' || status === 'PROCESSING') {
    return 'warning'
  }

  return 'info'
}

const loadHistory = async () => {
  loading.value = true

  try {
    const result = await getHistoryPage(page.value, size.value)
    records.value = result.records
    total.value = result.total
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(error instanceof Error ? error.message : '获取历史记录失败')
  } finally {
    loading.value = false
  }
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

const openDetail = async (record: HistoryListItem) => {
  loadingDetail.value = true
  detailVisible.value = true

  try {
    activeDetail.value = await getHistoryDetail(record.resumeId)
  } catch (error) {
    activeDetail.value = null
    detailVisible.value = false
    ElMessage.error(error instanceof Error ? error.message : '获取历史详情失败')
  } finally {
    loadingDetail.value = false
  }
}

const goResume = (resumeId: number) => {
  router.push({
    path: '/resumes',
    query: {
      resumeId: String(resumeId),
    },
  })
}

const goMatch = (record: HistoryListItem) => {
  if (record.latestMatchSource === 'AI_JOB_DESCRIPTION' && record.latestJobDescriptionId) {
    router.push({
      path: '/ai-job-matches',
      query: {
        resumeId: String(record.resumeId),
        jobDescriptionId: String(record.latestJobDescriptionId),
      },
    })
    return
  }

  if (!record.latestJobId) {
    ElMessage.warning('当前记录暂无岗位匹配结果')
    return
  }

  router.push({
    path: `/jobs/${record.latestJobId}`,
    query: {
      resumeId: String(record.resumeId),
    },
  })
}

const goMatchDetail = (match: HistoryMatchResult) => {
  if (!activeDetail.value) {
    return
  }

  if (match.matchSource === 'AI_JOB_DESCRIPTION' && match.jobDescriptionId) {
    router.push({
      path: '/ai-job-matches',
      query: {
        resumeId: String(activeDetail.value.resumeId),
        jobDescriptionId: String(match.jobDescriptionId),
      },
    })
    return
  }

  if (!match.jobId) {
    return
  }

  router.push({
    path: `/jobs/${match.jobId}`,
    query: {
      resumeId: String(activeDetail.value.resumeId),
    },
  })
}

onMounted(() => {
  loadHistory()
})
</script>

<template>
  <main class="history-page">
    <section class="history-shell">
      <header class="history-header">
        <div>
          <h1 class="history-title">历史记录</h1>
          <p class="history-subtitle">查看简历上传、解析、AI 分析和岗位匹配的最近状态。</p>
        </div>
        <el-space>
          <el-button @click="router.push('/')">返回首页</el-button>
          <el-button type="primary" @click="loadHistory">刷新</el-button>
        </el-space>
      </header>

      <section class="history-table-panel">
        <el-table
          v-loading="loading"
          :data="records"
          class="history-table"
          empty-text="暂无历史记录"
        >
          <el-table-column prop="resumeName" label="简历" min-width="220" />
          <el-table-column label="上传信息" min-width="180">
            <template #default="{ row }: { row: HistoryListItem }">
              <div class="history-cell-stack">
                <span>{{ row.fileType || '-' }} · {{ formatFileSize(row.fileSize) }}</span>
                <span>{{ formatDateTime(row.uploadTime) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="解析" width="120">
            <template #default="{ row }: { row: HistoryListItem }">
              <el-tag :type="resolveStatusType(row.parseStatus)">
                {{ resolveParseStatusText(row.parseStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="AI 分析" min-width="150">
            <template #default="{ row }: { row: HistoryListItem }">
              <div class="history-cell-stack">
                <el-tag :type="resolveStatusType(row.analysisStatus)">
                  {{ resolveAnalysisStatusText(row.analysisStatus) }}
                </el-tag>
                <span>评分：{{ row.analysisScore ?? '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近匹配" min-width="190">
            <template #default="{ row }: { row: HistoryListItem }">
              <div class="history-cell-stack">
                <span>{{ row.latestJobTitle || '暂无匹配岗位' }}</span>
                <span>{{ row.latestCompanyName || '-' }} · {{ row.latestMatchScore ?? '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="180">
            <template #default="{ row }: { row: HistoryListItem }">
              {{ formatDateTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }: { row: HistoryListItem }">
              <div class="history-actions">
                <el-button size="small" @click="openDetail(row)">详情</el-button>
                <el-button size="small" type="primary" @click="goResume(row.resumeId)">查看简历</el-button>
                <el-button
                  size="small"
                  :disabled="!row.latestJobId && !row.latestJobDescriptionId"
                  @click="goMatch(row)"
                >
                  查看匹配
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && !hasRecords" description="暂无历史记录，请先上传简历" :image-size="96">
          <el-button type="primary" @click="router.push('/resumes')">去上传简历</el-button>
        </el-empty>

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

    <el-drawer
      v-model="detailVisible"
      title="历史详情"
      size="560px"
      class="history-detail-drawer"
    >
      <section v-loading="loadingDetail" class="history-detail">
        <el-empty v-if="!activeDetail" description="暂无详情" :image-size="80" />

        <template v-else>
          <section class="history-detail-section">
            <h2 class="history-section-title">简历信息</h2>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="文件名">{{ activeDetail.resume.resumeName }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ activeDetail.resume.fileType }}</el-descriptions-item>
              <el-descriptions-item label="大小">{{ formatFileSize(activeDetail.resume.fileSize) }}</el-descriptions-item>
              <el-descriptions-item label="上传时间">{{ formatDateTime(activeDetail.resume.uploadTime) }}</el-descriptions-item>
            </el-descriptions>
          </section>

          <section class="history-detail-section">
            <h2 class="history-section-title">解析摘要</h2>
            <el-tag :type="resolveStatusType(activeDetail.parseResult.parseStatus)">
              {{ resolveParseStatusText(activeDetail.parseResult.parseStatus) }}
            </el-tag>
            <p class="history-detail-text">{{ activeDetail.parseResult.extractedTextPreview || '-' }}</p>
            <el-alert
              v-if="activeDetail.parseResult.parseErrorMessage"
              :title="activeDetail.parseResult.parseErrorMessage"
              type="error"
              :closable="false"
              show-icon
            />
          </section>

          <section class="history-detail-section">
            <h2 class="history-section-title">AI 分析摘要</h2>
            <div class="history-score-row">
              <el-tag :type="resolveStatusType(activeDetail.aiAnalysis.analysisStatus)">
                {{ resolveAnalysisStatusText(activeDetail.aiAnalysis.analysisStatus) }}
              </el-tag>
              <strong>评分：{{ activeDetail.aiAnalysis.analysisScore ?? '-' }}</strong>
            </div>
            <el-alert
              title="AI 分析仅供参考，涉及经历、技能、证书、奖项和量化结果的内容需要你确认后再使用。"
              type="warning"
              :closable="false"
              show-icon
              class="history-ai-warning"
            />
            <p class="history-detail-text">{{ activeDetail.aiAnalysis.suggestionsSummary || '-' }}</p>
            <el-alert
              v-if="activeDetail.aiAnalysis.analysisErrorMessage"
              :title="activeDetail.aiAnalysis.analysisErrorMessage"
              type="error"
              :closable="false"
              show-icon
            />
          </section>

          <section class="history-detail-section">
            <h2 class="history-section-title">岗位匹配记录</h2>
            <el-empty
              v-if="activeDetail.matchResults.length === 0"
              description="暂无岗位匹配结果"
              :image-size="80"
            />
            <div v-else class="history-match-list">
              <article
                v-for="match in activeDetail.matchResults"
                :key="match.matchId"
                class="history-match-item"
              >
                <div>
                  <h3 class="history-match-title">{{ match.jobTitle || '未知岗位' }}</h3>
                  <p class="history-detail-text">
                    {{ match.companyName || '-' }} · {{ match.jobCategory || '-' }} · {{ formatDateTime(match.matchUpdatedAt) }}
                  </p>
                </div>
                <div class="history-match-score">{{ match.matchScore ?? '-' }}</div>
                <p class="history-detail-text">{{ match.matchReason || '-' }}</p>
                <p class="history-detail-text">{{ match.suggestionsPreview || '-' }}</p>
                <el-button size="small" type="primary" @click="goMatchDetail(match)">查看匹配</el-button>
              </article>
            </div>
          </section>
        </template>
      </section>
    </el-drawer>
  </main>
</template>

<style scoped>
.history-page {
  min-height: 100vh;
  padding: 40px 28px 56px;
  background: #f4f7fb;
}

.history-shell {
  width: min(100%, 1280px);
  margin: 0 auto;
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
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.history-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.7;
}

.history-table-panel {
  padding: 24px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.history-table {
  border: 1px solid #dde5f0;
  border-radius: 8px;
}

.history-cell-stack {
  display: grid;
  gap: 4px;
  color: #344054;
  font-size: 13px;
  line-height: 1.5;
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

.history-section-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.history-detail-text {
  margin: 10px 0 0;
  color: #344054;
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.history-score-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.history-ai-warning {
  margin-top: 12px;
}

.history-match-list {
  display: grid;
  gap: 12px;
}

.history-match-item {
  position: relative;
  padding: 16px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #f8fafc;
}

.history-match-title {
  margin: 0;
  padding-right: 56px;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.history-match-score {
  position: absolute;
  top: 16px;
  right: 16px;
  min-width: 44px;
  color: #2563eb;
  font-size: 22px;
  font-weight: 700;
  text-align: right;
}

@media (max-width: 760px) {
  .history-header {
    align-items: stretch;
    flex-direction: column;
  }

  .history-table-panel {
    padding: 16px;
  }

  .history-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
