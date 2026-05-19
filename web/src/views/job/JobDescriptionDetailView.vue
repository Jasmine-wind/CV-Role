<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteJobDescription, getJobDescriptionDetail, parseJobDescription } from '@/api/job-description'
import type { JobDescriptionDetail, JobDescriptionStructuredContent } from '@/types/job-description'

const route = useRoute()
const router = useRouter()
const detail = ref<JobDescriptionDetail | null>(null)
const loading = ref(false)
const parsing = ref(false)
const deleting = ref(false)
const loadFailed = ref(false)

const jobDescriptionId = computed(() => Number(route.params.id))

const structuredContent = computed<JobDescriptionStructuredContent | null>(() => {
  if (!detail.value?.structuredContent) {
    return null
  }

  try {
    const parsed = JSON.parse(detail.value.structuredContent) as Partial<JobDescriptionStructuredContent>
    return {
      jobTitle: parsed.jobTitle || '',
      requiredSkills: Array.isArray(parsed.requiredSkills) ? parsed.requiredSkills : [],
      bonusSkills: Array.isArray(parsed.bonusSkills) ? parsed.bonusSkills : [],
      experienceSignals: Array.isArray(parsed.experienceSignals) ? parsed.experienceSignals : [],
      responsibilities: Array.isArray(parsed.responsibilities) ? parsed.responsibilities : [],
      keywords: Array.isArray(parsed.keywords) ? parsed.keywords : [],
      summary: parsed.summary || '',
    }
  } catch {
    return null
  }
})

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const statusType = computed(() => {
  if (detail.value?.parseStatus === 'SUCCESS') {
    return 'success'
  }
  if (detail.value?.parseStatus === 'FAILED') {
    return 'danger'
  }
  return 'info'
})

const statusText = computed(() => {
  if (detail.value?.parseStatus === 'SUCCESS') {
    return '已解析'
  }
  if (detail.value?.parseStatus === 'FAILED') {
    return '解析失败'
  }
  return '未解析'
})

const sourceTypeText = computed(() => {
  if (detail.value?.sourceType === 'PRESET') {
    return '系统预置'
  }
  if (detail.value?.sourceType === 'CRAWLED') {
    return '外部采集'
  }
  return '用户粘贴 JD'
})

const loadDetail = async () => {
  if (!Number.isFinite(jobDescriptionId.value)) {
    loadFailed.value = true
    ElMessage.error('目标岗位 ID 不正确')
    return
  }

  loading.value = true
  loadFailed.value = false

  try {
    detail.value = await getJobDescriptionDetail(jobDescriptionId.value)
  } catch (error) {
    detail.value = null
    loadFailed.value = true
    ElMessage.error(error instanceof Error ? error.message : '获取目标岗位失败')
  } finally {
    loading.value = false
  }
}

const handleParse = async () => {
  if (!detail.value) {
    return
  }

  parsing.value = true

  try {
    detail.value = await parseJobDescription(detail.value.id)
    if (detail.value.parseStatus === 'FAILED') {
      ElMessage.error(detail.value.errorMessage || '目标岗位解析失败')
    } else {
      ElMessage.success('目标岗位解析完成')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '目标岗位解析失败')
  } finally {
    parsing.value = false
  }
}

const handleDelete = async () => {
  if (!detail.value) {
    return
  }

  try {
    await ElMessageBox.confirm(`确认删除「${detail.value.title}」吗？关联的匹配分析结果也会一起删除。`, '删除目标岗位', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  deleting.value = true

  try {
    await deleteJobDescription(detail.value.id)
    ElMessage.success('目标岗位删除成功')
    router.push('/job-descriptions')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '目标岗位删除失败')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <main class="job-description-page">
    <section class="job-description-shell">
      <header class="job-description-header">
        <div>
          <h1 class="job-description-title">{{ detail?.title || '目标岗位详情' }}</h1>
          <p class="job-description-subtitle">查看目标岗位 JD，并触发目标岗位解析。</p>
        </div>
        <el-space>
          <el-button @click="router.push('/job-descriptions')">目标岗位</el-button>
          <el-button @click="router.push('/job-descriptions/new')">新增目标岗位</el-button>
          <el-button
            :disabled="detail?.parseStatus !== 'SUCCESS'"
            @click="router.push(`/ai-job-matches?jobDescriptionId=${detail?.id}`)"
          >
            匹配分析
          </el-button>
          <el-button
            v-if="detail"
            type="danger"
            :loading="deleting"
            :disabled="parsing"
            @click="handleDelete"
          >
            删除
          </el-button>
          <el-button @click="router.push('/jobs')">岗位库</el-button>
        </el-space>
      </header>

      <section v-loading="loading" class="job-description-panel">
        <el-empty v-if="loadFailed" description="目标岗位不存在或无权访问" :image-size="96">
          <el-button type="primary" @click="router.push('/job-descriptions/new')">新增目标岗位</el-button>
        </el-empty>

        <template v-else-if="detail">
          <div class="job-description-toolbar">
            <el-tag :type="statusType">{{ statusText }}</el-tag>
            <el-button type="primary" :loading="parsing" @click="handleParse">开始解析</el-button>
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ sourceTypeText }}</el-descriptions-item>
            <el-descriptions-item label="解析状态">{{ detail.parseStatus }}</el-descriptions-item>
            <el-descriptions-item label="模型">{{ detail.modelName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Prompt 版本">{{ detail.promptVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="detail.parseStatus === 'FAILED'"
            class="job-description-alert"
            :title="detail.errorMessage || '目标岗位解析失败'"
            type="error"
            :closable="false"
            show-icon
          />

          <section class="job-description-section">
            <h2 class="job-description-section-title">目标岗位 JD 原文</h2>
            <p class="job-description-text">{{ detail.rawText }}</p>
          </section>

          <section class="job-description-section">
            <h2 class="job-description-section-title">结构化解析结果</h2>
            <el-empty v-if="!structuredContent" description="暂无解析结果" :image-size="80" />

            <template v-else>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="职位名称">{{ structuredContent.jobTitle || '-' }}</el-descriptions-item>
                <el-descriptions-item label="岗位摘要">{{ structuredContent.summary || '-' }}</el-descriptions-item>
              </el-descriptions>

              <section class="job-description-grid">
                <div>
                  <h3 class="job-description-group-title">必备技能</h3>
                  <div v-if="structuredContent.requiredSkills.length" class="job-description-tags">
                    <el-tag v-for="item in structuredContent.requiredSkills" :key="item" type="success">{{ item }}</el-tag>
                  </div>
                  <el-empty v-else description="暂无必备技能" :image-size="72" />
                </div>

                <div>
                  <h3 class="job-description-group-title">加分技能</h3>
                  <div v-if="structuredContent.bonusSkills.length" class="job-description-tags">
                    <el-tag v-for="item in structuredContent.bonusSkills" :key="item" type="warning">{{ item }}</el-tag>
                  </div>
                  <el-empty v-else description="暂无加分技能" :image-size="72" />
                </div>

                <div>
                  <h3 class="job-description-group-title">经验信号</h3>
                  <div v-if="structuredContent.experienceSignals.length" class="job-description-list">
                    <p v-for="item in structuredContent.experienceSignals" :key="item" class="job-description-text">{{ item }}</p>
                  </div>
                  <el-empty v-else description="暂无经验信号" :image-size="72" />
                </div>

                <div>
                  <h3 class="job-description-group-title">关键词</h3>
                  <div v-if="structuredContent.keywords.length" class="job-description-tags">
                    <el-tag v-for="item in structuredContent.keywords" :key="item">{{ item }}</el-tag>
                  </div>
                  <el-empty v-else description="暂无关键词" :image-size="72" />
                </div>
              </section>

              <section class="job-description-section">
                <h3 class="job-description-group-title">职责内容</h3>
                <div v-if="structuredContent.responsibilities.length" class="job-description-list">
                  <p v-for="item in structuredContent.responsibilities" :key="item" class="job-description-text">{{ item }}</p>
                </div>
                <el-empty v-else description="暂无职责内容" :image-size="72" />
              </section>
            </template>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>

<style scoped>
.job-description-page {
  min-height: 100vh;
  padding: 40px 28px 56px;
  background: #f4f7fb;
}

.job-description-shell {
  width: min(100%, 1040px);
  margin: 0 auto;
}

.job-description-header,
.job-description-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.job-description-title {
  margin: 0;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.job-description-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.7;
}

.job-description-panel {
  min-height: 360px;
  padding: 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.job-description-alert,
.job-description-section {
  margin-top: 24px;
}

.job-description-section-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.job-description-group-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.job-description-text {
  margin: 0;
  color: #344054;
  line-height: 1.8;
  white-space: pre-wrap;
}

.job-description-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.job-description-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.job-description-list {
  display: grid;
  gap: 8px;
}

@media (max-width: 720px) {
  .job-description-header,
  .job-description-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .job-description-grid {
    grid-template-columns: 1fr;
  }
}
</style>
