<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getJobDetail, getResumeJobMatches, matchResumeToJob } from '@/api/job'
import { getResumeList } from '@/api/resume'
import type { JobDetail, JobMatchResult, JobMatchSuggestion } from '@/types/job'
import type { ResumeListItem } from '@/types/resume'

const route = useRoute()
const router = useRouter()
const job = ref<JobDetail | null>(null)
const resumes = ref<ResumeListItem[]>([])
const selectedResumeId = ref<number | null>(null)
const activeMatchResult = ref<JobMatchResult | null>(null)
const loading = ref(false)
const loadingResumes = ref(false)
const loadingMatches = ref(false)
const matching = ref(false)
const loadFailed = ref(false)

const jobId = computed(() => Number(route.params.id))

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const selectedResume = computed(() => {
  return resumes.value.find((resume) => resume.id === selectedResumeId.value) ?? null
})

const resolveSuggestionType = (type: string) => {
  const typeMap: Record<string, string> = {
    SKILL_GAP: '技能缺口',
    PROJECT_DESCRIPTION: '项目描述',
    HIGHLIGHT_STRENGTH: '优势突出',
    GENERAL: '综合建议',
  }

  return typeMap[type] ?? type
}

const resolvePriorityType = (priority: string) => {
  if (priority === 'HIGH') {
    return 'danger'
  }

  if (priority === 'MEDIUM') {
    return 'warning'
  }

  return 'info'
}

const loadJob = async () => {
  if (!Number.isFinite(jobId.value)) {
    loadFailed.value = true
    ElMessage.error('岗位 ID 不正确')
    return
  }

  loading.value = true
  loadFailed.value = false

  try {
    job.value = await getJobDetail(jobId.value)
  } catch (error) {
    job.value = null
    loadFailed.value = true
    ElMessage.error(error instanceof Error ? error.message : '获取岗位详情失败')
  } finally {
    loading.value = false
  }
}

const loadResumes = async () => {
  loadingResumes.value = true

  try {
    resumes.value = await getResumeList()
    const firstResume = resumes.value[0]
    const queryResumeId = Number(route.query.resumeId)

    if (Number.isFinite(queryResumeId) && resumes.value.some((resume) => resume.id === queryResumeId)) {
      selectedResumeId.value = queryResumeId
    } else if (!selectedResumeId.value && firstResume) {
      selectedResumeId.value = firstResume.id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取简历列表失败')
  } finally {
    loadingResumes.value = false
  }
}

const loadCurrentResumeMatches = async () => {
  if (!selectedResumeId.value || !job.value) {
    activeMatchResult.value = null
    return
  }

  loadingMatches.value = true

  try {
    const matches = await getResumeJobMatches(selectedResumeId.value)
    activeMatchResult.value = matches.find((match) => match.jobId === job.value?.id) ?? null
  } catch (error) {
    activeMatchResult.value = null
    ElMessage.warning(error instanceof Error ? error.message : '获取匹配结果失败')
  } finally {
    loadingMatches.value = false
  }
}

const handleResumeChange = async () => {
  await loadCurrentResumeMatches()
}

const handleMatch = async () => {
  if (!job.value || !selectedResumeId.value) {
    ElMessage.warning('请先选择简历')
    return
  }

  matching.value = true

  try {
    await matchResumeToJob(selectedResumeId.value, job.value.id)
    activeMatchResult.value = null
    await loadCurrentResumeMatches()
    ElMessage.success('岗位匹配完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位匹配失败')
  } finally {
    matching.value = false
  }
}

const suggestionKey = (suggestion: JobMatchSuggestion, index: number) => {
  return `${suggestion.type}-${suggestion.relatedItem}-${index}`
}

onMounted(async () => {
  await loadJob()
  await loadResumes()
  await loadCurrentResumeMatches()
})
</script>

<template>
  <main class="job-page">
    <section class="job-shell">
      <header class="job-header">
        <div>
          <h1 class="job-title">{{ job?.title || '岗位详情' }}</h1>
          <p class="job-subtitle">{{ job ? `${job.companyName} · ${job.location}` : '查看岗位要求与技能关键词。' }}</p>
        </div>
        <el-space>
          <el-button @click="router.push('/jobs')">返回列表</el-button>
          <el-button @click="router.push('/resumes')">我的简历</el-button>
          <el-button type="primary" @click="router.push('/history')">历史记录</el-button>
        </el-space>
      </header>

      <section v-loading="loading" class="job-detail-panel">
        <el-empty v-if="loadFailed" description="岗位不存在或已不可用" :image-size="96">
          <el-button type="primary" @click="router.push('/jobs')">查看岗位列表</el-button>
        </el-empty>

        <template v-else-if="job">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="岗位名称">{{ job.title }}</el-descriptions-item>
            <el-descriptions-item label="公司">{{ job.companyName }}</el-descriptions-item>
            <el-descriptions-item label="方向">{{ job.jobCategory }}</el-descriptions-item>
            <el-descriptions-item label="地点">{{ job.location }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(job.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <section class="job-section">
            <h2 class="job-section-title">技能要求</h2>
            <div class="job-tag-list">
              <el-tag v-for="skill in job.requiredSkills" :key="skill" type="success">{{ skill }}</el-tag>
            </div>
          </section>

          <section class="job-section">
            <h2 class="job-section-title">岗位描述</h2>
            <p class="job-text">{{ job.description }}</p>
          </section>

          <section class="job-section">
            <h2 class="job-section-title">岗位要求</h2>
            <p class="job-text">{{ job.requirements }}</p>
          </section>

          <section class="job-match-entry">
            <div>
              <h2 class="job-section-title">简历匹配</h2>
              <p class="job-text">从已上传简历中选择一份进行岗位匹配。</p>
            </div>
            <div class="job-match-controls">
              <el-select
                v-model="selectedResumeId"
                :loading="loadingResumes"
                placeholder="选择简历"
                class="job-resume-select"
                @change="handleResumeChange"
              >
                <el-option
                  v-for="resume in resumes"
                  :key="resume.id"
                  :label="resume.originalFilename"
                  :value="resume.id"
                />
              </el-select>
              <el-button
                type="primary"
                :disabled="!selectedResumeId"
                :loading="matching"
                @click="handleMatch"
              >
                开始匹配
              </el-button>
            </div>
          </section>

          <el-alert
            v-if="!loadingResumes && resumes.length === 0"
            class="job-match-alert"
            title="暂无可用简历，请先上传并解析简历。"
            type="warning"
            :closable="false"
            show-icon
          />

          <section v-if="selectedResume" v-loading="loadingMatches" class="job-match-result">
            <header class="job-match-result-header">
              <div>
                <h2 class="job-section-title">匹配结果</h2>
                <p class="job-text">{{ selectedResume.originalFilename }}</p>
              </div>
              <el-button @click="loadCurrentResumeMatches">刷新结果</el-button>
            </header>

            <el-empty v-if="!activeMatchResult" description="当前简历还没有该岗位的匹配结果" :image-size="80" />

            <template v-else>
              <div class="job-match-score-row">
                <el-progress
                  class="job-match-score-progress"
                  type="dashboard"
                  :percentage="activeMatchResult.matchScore"
                  :stroke-width="8"
                  :width="132"
                  color="#2563eb"
                />
                <el-descriptions :column="1" border class="job-match-meta">
                  <el-descriptions-item label="岗位">{{ activeMatchResult.jobTitle }}</el-descriptions-item>
                  <el-descriptions-item label="公司">{{ activeMatchResult.companyName }}</el-descriptions-item>
                  <el-descriptions-item label="更新时间">{{ formatDateTime(activeMatchResult.updatedAt) }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <section class="job-section">
                <h2 class="job-section-title">匹配说明</h2>
                <p class="job-text">{{ activeMatchResult.matchReason }}</p>
              </section>

              <section class="job-match-grid">
                <div>
                  <h2 class="job-section-title">命中项</h2>
                  <div v-if="activeMatchResult.matchedItems.length > 0" class="job-tag-list">
                    <el-tag v-for="item in activeMatchResult.matchedItems" :key="item" type="success">{{ item }}</el-tag>
                  </div>
                  <el-empty v-else description="暂无命中项" :image-size="72" />
                </div>

                <div>
                  <h2 class="job-section-title">缺失项</h2>
                  <div v-if="activeMatchResult.missingItems.length > 0" class="job-tag-list">
                    <el-tag v-for="item in activeMatchResult.missingItems" :key="item" type="danger">{{ item }}</el-tag>
                  </div>
                  <el-empty v-else description="暂无缺失项" :image-size="72" />
                </div>
              </section>

              <section class="job-section">
                <h2 class="job-section-title">优化建议</h2>
                <div class="job-suggestion-list">
                  <article
                    v-for="(suggestion, index) in activeMatchResult.suggestions"
                    :key="suggestionKey(suggestion, index)"
                    class="job-suggestion-item"
                  >
                    <div class="job-suggestion-header">
                      <h3 class="job-suggestion-title">{{ suggestion.title }}</h3>
                      <el-space>
                        <el-tag size="small">{{ resolveSuggestionType(suggestion.type) }}</el-tag>
                        <el-tag size="small" :type="resolvePriorityType(suggestion.priority)">
                          {{ suggestion.priority }}
                        </el-tag>
                      </el-space>
                    </div>
                    <p class="job-text">{{ suggestion.content }}</p>
                  </article>
                </div>
              </section>
            </template>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>

<style scoped>
.job-page {
  min-height: 100vh;
  padding: 40px 28px 56px;
  background: #f4f7fb;
}

.job-shell {
  width: min(100%, 960px);
  margin: 0 auto;
}

.job-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.job-title {
  margin: 0;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.job-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.7;
}

.job-detail-panel {
  min-height: 360px;
  padding: 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.job-section {
  margin-top: 24px;
}

.job-section-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.job-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.job-text {
  margin: 0;
  color: #344054;
  line-height: 1.8;
}

.job-match-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 28px;
  padding: 20px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #f8fafc;
}

.job-match-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.job-resume-select {
  width: 260px;
}

.job-match-alert,
.job-match-result {
  margin-top: 20px;
}

.job-match-result {
  padding: 24px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.job-match-result-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.job-match-score-row {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
  margin-top: 20px;
}

.job-match-meta {
  min-width: 0;
}

.job-match-score-progress {
  display: block;
  width: 132px;
  height: 132px;
  justify-self: center;
}

.job-match-score-progress :deep(.el-progress-circle) {
  width: 132px !important;
  height: 132px !important;
}

.job-match-score-progress :deep(.el-progress__text) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 132px;
  height: 132px;
  margin: 0;
  top: 0;
  left: 0;
  line-height: 1;
  transform: none;
}

.job-match-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 24px;
}

.job-suggestion-list {
  display: grid;
  gap: 12px;
}

.job-suggestion-item {
  padding: 16px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #f8fafc;
}

.job-suggestion-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.job-suggestion-title {
  margin: 0;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

@media (max-width: 640px) {
  .job-header,
  .job-match-entry,
  .job-match-controls,
  .job-match-result-header,
  .job-suggestion-header {
    align-items: stretch;
    flex-direction: column;
  }

  .job-resume-select {
    width: 100%;
  }

  .job-match-score-row,
  .job-match-grid {
    grid-template-columns: 1fr;
  }
}
</style>
