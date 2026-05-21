<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import BaseCard from '@/components/common/BaseCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import JobParseResult from '@/components/job/JobParseResult.vue'
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
  <section class="job-description-detail-page">
    <PageHeader
      eyebrow="目标岗位详情"
      :title="detail?.title || '目标岗位详情'"
      description="核对岗位要求后，进入匹配与优化选择简历生成匹配分析。"
    >
      <template #actions>
        <el-button @click="router.push('/job-descriptions')">返回目标岗位</el-button>
        <el-button
          v-if="detail && detail.parseStatus === 'SUCCESS'"
          type="primary"
          @click="router.push(`/ai-job-matches?jobDescriptionId=${detail.id}`)"
        >
          开始匹配分析
        </el-button>
        <el-button
          v-else-if="detail"
          type="primary"
          :loading="parsing"
          @click="handleParse"
        >
          解析目标岗位
        </el-button>
        <el-button
          v-if="detail && detail.parseStatus === 'SUCCESS'"
          :loading="parsing"
          @click="handleParse"
        >
          重新解析
        </el-button>
        <el-button
          v-if="detail"
          type="danger"
          plain
          :loading="deleting"
          :disabled="parsing"
          @click="handleDelete"
        >
          删除
        </el-button>
      </template>
    </PageHeader>

    <section v-loading="loading" class="job-description-detail-body">
      <ErrorState
        v-if="loadFailed"
        title="目标岗位不存在或无权访问"
        description="可以返回目标岗位列表，或新增一个真实 JD。"
        action-text="新增目标岗位"
        @action="router.push('/job-descriptions/new')"
      />

      <template v-else-if="detail">
        <BaseCard title="岗位摘要" subtitle="先确认岗位是否可用于匹配分析。">
          <div class="job-description-meta-grid">
            <div class="job-description-meta-item">
              <span class="job-description-meta-label">当前状态</span>
              <div class="job-description-meta-value">
                <StatusTag :status="detail.parseStatus" />
              </div>
            </div>
            <div class="job-description-meta-item">
              <span class="job-description-meta-label">目标岗位</span>
              <strong class="job-description-meta-value">{{ structuredContent?.jobTitle || detail.title }}</strong>
            </div>
            <div class="job-description-meta-item">
              <span class="job-description-meta-label">更新时间</span>
              <strong class="job-description-meta-value">{{ formatDateTime(detail.updatedAt) }}</strong>
            </div>
          </div>
          <p v-if="structuredContent?.summary" class="job-description-summary-text">
            {{ structuredContent.summary }}
          </p>
          <el-alert
            v-if="detail.parseStatus === 'FAILED'"
            class="job-description-alert"
            :title="detail.errorMessage || '目标岗位解析失败'"
            type="error"
            :closable="false"
            show-icon
          />
        </BaseCard>

        <section class="job-description-reading-flow">
          <BaseCard title="岗位解析结果" subtitle="用于后续匹配分析的核心岗位要求。">
            <JobParseResult :content="structuredContent" />
          </BaseCard>

          <BaseCard title="JD 原文" subtitle="保留你粘贴的真实岗位描述，方便核对解析是否遗漏。">
            <p class="job-description-raw-text">{{ detail.rawText }}</p>
          </BaseCard>
        </section>

        <BaseCard title="下一步" subtitle="目标岗位解析成功后，再基于简历进入匹配分析。">
          <div class="job-description-next-action">
            <div>
              <strong>{{ detail.parseStatus === 'SUCCESS' ? '开始匹配分析' : '先解析目标岗位' }}</strong>
              <p>{{ detail.parseStatus === 'SUCCESS' ? '匹配与优化页会选择简历和目标岗位，生成匹配分、强弱项、缺失技能和后续建议。' : '解析会抽取职责、技能、关键词和经验要求；解析成功后再进入匹配分析。' }}</p>
            </div>
            <el-button
              type="primary"
              :loading="parsing"
              @click="detail.parseStatus === 'SUCCESS' ? router.push(`/ai-job-matches?jobDescriptionId=${detail.id}`) : handleParse()"
            >
              {{ detail.parseStatus === 'SUCCESS' ? '进入匹配与优化' : '解析目标岗位' }}
            </el-button>
          </div>
        </BaseCard>
      </template>

      <EmptyState
        v-else
        title="请选择目标岗位"
        description="从目标岗位列表进入详情后，可以查看原文和解析结果。"
        action-text="返回目标岗位"
        @action="router.push('/job-descriptions')"
      />
    </section>
  </section>
</template>

<style scoped>
.job-description-detail-page,
.job-description-detail-body {
  display: grid;
  gap: 18px;
}

.job-description-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.job-description-meta-item {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  background: var(--app-color-surface-soft);
}

.job-description-meta-label {
  color: var(--app-color-text-secondary);
}

.job-description-meta-value {
  overflow: hidden;
  color: var(--app-color-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-description-meta-value.el-tag {
  justify-self: start;
}

.job-description-alert {
  margin-top: 16px;
}

.job-description-summary-text {
  margin: 16px 0 0;
  color: var(--app-color-text-secondary);
  line-height: 1.8;
}

.job-description-reading-flow {
  display: grid;
  gap: 18px;
}

.job-description-raw-text {
  max-height: 720px;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 16px;
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.8;
  overflow: auto;
  white-space: pre-wrap;
  background: var(--app-color-surface-soft);
}

.job-description-next-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.job-description-next-action strong {
  color: var(--app-color-text);
  font-size: 18px;
}

.job-description-next-action p {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  line-height: 1.7;
}

@media (max-width: 1024px) {
  .job-description-meta-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .job-description-next-action {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
