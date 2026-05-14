<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAiJobMatch, getAiJobMatches, triggerAiJobMatch } from '@/api/ai-job-match'
import { getJobDescriptionList } from '@/api/job-description'
import { getResumeList, getResumeParseResult } from '@/api/resume'
import type { AiJobMatchResult } from '@/types/ai-job-match'
import type { JobDescriptionDetail } from '@/types/job-description'
import type { ResumeListItem, ResumeParseResult } from '@/types/resume'

const route = useRoute()
const router = useRouter()

const resumes = ref<ResumeListItem[]>([])
const jobDescriptions = ref<JobDescriptionDetail[]>([])
const selectedResumeId = ref<number | null>(null)
const selectedJobDescriptionId = ref<number | null>(null)
const selectedResumeParseResult = ref<ResumeParseResult | null>(null)
const selectedMatch = ref<AiJobMatchResult | null>(null)
const matchResults = ref<AiJobMatchResult[]>([])
const loading = ref(false)
const loadingResumeParse = ref(false)
const matching = ref(false)
const loadingResult = ref(false)

const parsedJobDescriptions = computed(() => {
  return jobDescriptions.value.filter((item) => item.parseStatus === 'SUCCESS')
})

const selectedResume = computed(() => {
  return resumes.value.find((item) => item.id === selectedResumeId.value) || null
})

const selectedJobDescription = computed(() => {
  return jobDescriptions.value.find((item) => item.id === selectedJobDescriptionId.value) || null
})

const canMatch = computed(() => {
  return Boolean(selectedResumeId.value && selectedJobDescriptionId.value && selectedJobDescription.value?.parseStatus === 'SUCCESS')
})

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const resolveMatchStatusText = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return '匹配成功'
  }
  if (status === 'FAILED') {
    return '匹配失败'
  }
  if (status === 'PENDING') {
    return '待匹配'
  }
  return status || '-'
}

const resolveMatchStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

const resolveParseStatusText = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return '已解析'
  }
  if (status === 'FAILED') {
    return '解析失败'
  }
  return status || '未解析'
}

const loadInitialData = async () => {
  loading.value = true

  try {
    const [resumeList, jobDescriptionList] = await Promise.all([
      getResumeList(),
      getJobDescriptionList(),
    ])
    resumes.value = resumeList
    jobDescriptions.value = jobDescriptionList
    applyRouteDefaults()
    await loadSelectedResumeParseResult()
    await loadCurrentMatch()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取匹配基础数据失败')
  } finally {
    loading.value = false
  }
}

const applyRouteDefaults = () => {
  const routeResumeId = Number(route.query.resumeId)
  const routeJobDescriptionId = Number(route.query.jobDescriptionId)

  if (Number.isFinite(routeResumeId) && resumes.value.some((item) => item.id === routeResumeId)) {
    selectedResumeId.value = routeResumeId
  } else if (!selectedResumeId.value && resumes.value.length > 0) {
    selectedResumeId.value = resumes.value[0]?.id ?? null
  }

  if (Number.isFinite(routeJobDescriptionId) && jobDescriptions.value.some((item) => item.id === routeJobDescriptionId)) {
    selectedJobDescriptionId.value = routeJobDescriptionId
  } else if (!selectedJobDescriptionId.value && parsedJobDescriptions.value.length > 0) {
    selectedJobDescriptionId.value = parsedJobDescriptions.value[0]?.id ?? null
  }
}

const loadSelectedResumeParseResult = async () => {
  selectedResumeParseResult.value = null
  if (!selectedResumeId.value) {
    return
  }

  loadingResumeParse.value = true

  try {
    selectedResumeParseResult.value = await getResumeParseResult(selectedResumeId.value)
  } catch {
    selectedResumeParseResult.value = null
  } finally {
    loadingResumeParse.value = false
  }
}

const loadCurrentMatch = async () => {
  selectedMatch.value = null
  matchResults.value = []
  if (!selectedResumeId.value) {
    return
  }

  loadingResult.value = true

  try {
    matchResults.value = await getAiJobMatches(selectedResumeId.value)
    if (selectedJobDescriptionId.value) {
      selectedMatch.value = await getAiJobMatch(selectedResumeId.value, selectedJobDescriptionId.value)
    } else {
      selectedMatch.value = matchResults.value[0] || null
    }
  } catch (error) {
    selectedMatch.value = null
    if (error instanceof Error && error.message !== 'AI 岗位匹配结果不存在') {
      ElMessage.warning(error.message)
    }
  } finally {
    loadingResult.value = false
  }
}

const handleResumeChange = async () => {
  await loadSelectedResumeParseResult()
  await loadCurrentMatch()
}

const handleJobDescriptionChange = async () => {
  await loadCurrentMatch()
}

const handleMatch = async () => {
  if (!selectedResumeId.value || !selectedJobDescriptionId.value) {
    ElMessage.warning('请先选择简历和岗位描述')
    return
  }

  if (selectedJobDescription.value?.parseStatus !== 'SUCCESS') {
    ElMessage.warning('请先完成岗位描述解析')
    return
  }

  matching.value = true

  try {
    const triggerResult = await triggerAiJobMatch(selectedResumeId.value, {
      jobDescriptionId: selectedJobDescriptionId.value,
    })
    if (triggerResult.matchStatus === 'FAILED') {
      ElMessage.error(triggerResult.errorMessage || 'AI 岗位匹配失败')
    } else {
      ElMessage.success('AI 岗位匹配完成')
    }
    await loadCurrentMatch()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 岗位匹配失败')
  } finally {
    matching.value = false
  }
}

onMounted(() => {
  loadInitialData()
})
</script>

<template>
  <main class="ai-match-page">
    <section class="ai-match-shell">
      <header class="ai-match-header">
        <div>
          <h1 class="ai-match-title">AI 岗位匹配</h1>
          <p class="ai-match-subtitle">选择已解析简历和已解析岗位描述，生成结构化匹配反馈。</p>
        </div>
        <el-space>
          <el-button @click="router.push('/resumes')">我的简历</el-button>
          <el-button @click="router.push('/job-descriptions')">岗位描述</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </el-space>
      </header>

      <section v-loading="loading" class="ai-match-panel">
        <section class="ai-match-selectors">
          <div class="ai-match-selector">
            <h2 class="ai-match-section-title">选择简历</h2>
            <el-select
              v-model="selectedResumeId"
              class="ai-match-select"
              placeholder="请选择简历"
              filterable
              @change="handleResumeChange"
            >
              <el-option
                v-for="resume in resumes"
                :key="resume.id"
                :label="resume.originalFilename"
                :value="resume.id"
              />
            </el-select>
            <div class="ai-match-meta">
              <span>解析状态：</span>
              <el-tag v-if="selectedResumeParseResult" :type="selectedResumeParseResult.parseStatus === 'SUCCESS' ? 'success' : 'warning'">
                {{ resolveParseStatusText(selectedResumeParseResult.parseStatus) }}
              </el-tag>
              <el-tag v-else type="info">{{ loadingResumeParse ? '读取中' : '未解析或暂无结果' }}</el-tag>
            </div>
          </div>

          <div class="ai-match-selector">
            <h2 class="ai-match-section-title">选择岗位描述</h2>
            <el-select
              v-model="selectedJobDescriptionId"
              class="ai-match-select"
              placeholder="请选择岗位描述"
              filterable
              @change="handleJobDescriptionChange"
            >
              <el-option
                v-for="jobDescription in jobDescriptions"
                :key="jobDescription.id"
                :label="jobDescription.title"
                :value="jobDescription.id"
                :disabled="jobDescription.parseStatus !== 'SUCCESS'"
              >
                <span>{{ jobDescription.title }}</span>
                <span class="ai-match-option-status">{{ resolveParseStatusText(jobDescription.parseStatus) }}</span>
              </el-option>
            </el-select>
            <div class="ai-match-meta">
              <span>岗位解析：</span>
              <el-tag v-if="selectedJobDescription" :type="selectedJobDescription.parseStatus === 'SUCCESS' ? 'success' : 'warning'">
                {{ resolveParseStatusText(selectedJobDescription.parseStatus) }}
              </el-tag>
              <el-tag v-else type="info">未选择</el-tag>
            </div>
          </div>
        </section>

        <el-alert
          v-if="selectedResumeParseResult && selectedResumeParseResult.parseStatus !== 'SUCCESS'"
          class="ai-match-alert"
          title="当前简历尚未解析成功，请先在“我的简历”中完成解析。"
          type="warning"
          :closable="false"
          show-icon
        />

        <el-alert
          v-if="selectedJobDescription && selectedJobDescription.parseStatus !== 'SUCCESS'"
          class="ai-match-alert"
          title="当前岗位描述尚未解析成功，请先完成岗位描述解析。"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="ai-match-actions">
          <el-button
            type="primary"
            :loading="matching"
            :disabled="!canMatch"
            @click="handleMatch"
          >
            开始 AI 匹配
          </el-button>
          <el-button :loading="loadingResult" :disabled="!selectedResumeId" @click="loadCurrentMatch">刷新结果</el-button>
        </div>

        <section v-loading="loadingResult" class="ai-match-result">
          <el-empty v-if="!selectedMatch" description="暂无 AI 匹配结果" :image-size="88" />

          <template v-else>
            <div class="ai-match-summary">
              <div class="ai-match-score">
                <span class="ai-match-score-value">{{ selectedMatch.overallScore ?? '-' }}</span>
                <span class="ai-match-score-label">总体匹配分</span>
              </div>
              <el-descriptions :column="2" border class="ai-match-descriptions">
                <el-descriptions-item label="匹配状态">
                  <el-tag :type="resolveMatchStatusType(selectedMatch.matchStatus)">
                    {{ resolveMatchStatusText(selectedMatch.matchStatus) }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="模型">{{ selectedMatch.modelName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Prompt 版本">{{ selectedMatch.promptVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="更新时间">{{ formatDateTime(selectedMatch.updatedAt) }}</el-descriptions-item>
              </el-descriptions>
            </div>

            <el-alert
              v-if="selectedMatch.matchStatus === 'FAILED'"
              class="ai-match-alert"
              :title="selectedMatch.errorMessage || 'AI 岗位匹配失败'"
              type="error"
              :closable="false"
              show-icon
            />

            <section class="ai-match-grid">
              <div class="ai-match-block">
                <h2 class="ai-match-section-title">强匹配项</h2>
                <div v-if="selectedMatch.strongMatches.length" class="ai-match-list">
                  <p v-for="item in selectedMatch.strongMatches" :key="`${item.item}-${item.reason}`">
                    <strong>{{ item.item || '-' }}</strong>
                    <span>{{ item.reason || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无强匹配项" :image-size="72" />
              </div>

              <div class="ai-match-block">
                <h2 class="ai-match-section-title">弱匹配项</h2>
                <div v-if="selectedMatch.weakMatches.length" class="ai-match-list">
                  <p v-for="item in selectedMatch.weakMatches" :key="`${item.item}-${item.reason}`">
                    <strong>{{ item.item || '-' }}</strong>
                    <span>{{ item.reason || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无弱匹配项" :image-size="72" />
              </div>

              <div class="ai-match-block">
                <h2 class="ai-match-section-title">缺失技能</h2>
                <div v-if="selectedMatch.missingSkills.length" class="ai-match-list">
                  <p v-for="item in selectedMatch.missingSkills" :key="`${item.item}-${item.reason}`">
                    <strong>{{ item.item || '-' }}</strong>
                    <span>{{ item.reason || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无缺失技能" :image-size="72" />
              </div>

              <div class="ai-match-block">
                <h2 class="ai-match-section-title">表达较弱经历</h2>
                <div v-if="selectedMatch.weakExperienceDescriptions.length" class="ai-match-list">
                  <p
                    v-for="item in selectedMatch.weakExperienceDescriptions"
                    :key="`${item.section}-${item.issue}`"
                  >
                    <strong>{{ item.section || '-' }}</strong>
                    <span>{{ item.issue || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无表达较弱经历" :image-size="72" />
              </div>
            </section>

            <section class="ai-match-block ai-match-wide">
              <h2 class="ai-match-section-title">匹配依据</h2>
              <div v-if="selectedMatch.evidence.length" class="ai-match-list">
                <p v-for="item in selectedMatch.evidence" :key="`${item.source}-${item.content}`">
                  <el-tag size="small" :type="item.source === 'job' ? 'warning' : 'success'">
                    {{ item.source === 'job' ? '岗位' : '简历' }}
                  </el-tag>
                  <span>{{ item.content || '-' }}</span>
                </p>
              </div>
              <el-empty v-else description="暂无匹配依据" :image-size="72" />
            </section>

            <section class="ai-match-block ai-match-wide">
              <h2 class="ai-match-section-title">风险提示</h2>
              <div v-if="selectedMatch.riskNotes.length" class="ai-match-list">
                <p v-for="item in selectedMatch.riskNotes" :key="item">{{ item }}</p>
              </div>
              <el-empty v-else description="暂无风险提示" :image-size="72" />
            </section>
          </template>
        </section>
      </section>
    </section>
  </main>
</template>

<style scoped>
.ai-match-page {
  min-height: 100vh;
  padding: 40px 28px 56px;
  background: #f4f7fb;
}

.ai-match-shell {
  width: min(100%, 1180px);
  margin: 0 auto;
}

.ai-match-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.ai-match-title {
  margin: 0;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.ai-match-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.7;
}

.ai-match-panel {
  min-height: 520px;
  padding: 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.ai-match-selectors {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.ai-match-selector,
.ai-match-block {
  padding: 18px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #ffffff;
}

.ai-match-section-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.ai-match-select {
  width: 100%;
}

.ai-match-option-status {
  float: right;
  color: #98a2b3;
  font-size: 13px;
}

.ai-match-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: #667085;
  font-size: 14px;
}

.ai-match-alert,
.ai-match-actions,
.ai-match-result {
  margin-top: 24px;
}

.ai-match-summary {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
}

.ai-match-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 132px;
  border: 1px solid #c7d7fe;
  border-radius: 8px;
  background: #eef4ff;
}

.ai-match-score-value {
  color: #175cd3;
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
}

.ai-match-score-label {
  margin-top: 10px;
  color: #344054;
  font-size: 14px;
}

.ai-match-descriptions {
  width: 100%;
}

.ai-match-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.ai-match-wide {
  margin-top: 20px;
}

.ai-match-list {
  display: grid;
  gap: 10px;
}

.ai-match-list p {
  display: grid;
  gap: 6px;
  margin: 0;
  color: #344054;
  line-height: 1.7;
}

.ai-match-list strong {
  color: #111827;
  font-weight: 700;
}

@media (max-width: 760px) {
  .ai-match-header,
  .ai-match-summary {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .ai-match-selectors,
  .ai-match-grid {
    grid-template-columns: 1fr;
  }
}
</style>
