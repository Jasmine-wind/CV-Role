<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import BaseCard from '@/components/common/BaseCard.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getJobDetail } from '@/api/job'
import type { JobDetail } from '@/types/job'

const route = useRoute()
const router = useRouter()
const job = ref<JobDetail | null>(null)
const loading = ref(false)
const loadFailed = ref(false)

const jobId = computed(() => Number(route.params.id))

const jobRequirementText = computed(() => {
  if (!job.value) {
    return ''
  }

  return [
    `岗位名称：${job.value.title}`,
    `公司：${job.value.companyName}`,
    `方向：${job.value.jobCategory}`,
    `地点：${job.value.location}`,
    '',
    '岗位描述：',
    job.value.description,
    '',
    '岗位要求：',
    job.value.requirements,
    '',
    `技能关键词：${job.value.requiredSkills.join('、')}`,
  ].join('\n')
})

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
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

const copyJobRequirement = async () => {
  if (!jobRequirementText.value) {
    return
  }

  try {
    await navigator.clipboard.writeText(jobRequirementText.value)
    ElMessage.success('岗位要求已复制，可粘贴到新增目标岗位')
  } catch {
    ElMessage.warning('当前浏览器不支持自动复制，请手动复制岗位要求')
  }
}

onMounted(() => {
  loadJob()
})
</script>

<template>
  <section class="job-detail-page">
    <PageHeader
      eyebrow="岗位库参考"
      :title="job?.title || '岗位参考详情'"
      description="系统预置岗位只用于只读参考。真实投递流程请复制岗位要求后新增目标岗位。"
    >
      <template #actions>
        <el-button @click="router.push('/jobs')">返回岗位库参考</el-button>
        <el-button @click="copyJobRequirement">复制岗位要求</el-button>
        <el-button type="primary" @click="router.push('/job-descriptions/new')">新增目标岗位</el-button>
      </template>
    </PageHeader>

    <section v-loading="loading" class="job-detail-body">
      <ErrorState
        v-if="loadFailed"
        title="岗位不存在或已不可用"
        description="岗位库只是参考入口，可以直接新增目标岗位继续主流程。"
        action-text="新增目标岗位"
        @action="router.push('/job-descriptions/new')"
      />

      <template v-else-if="job">
        <BaseCard title="参考岗位信息" subtitle="这不是目标岗位记录，也不会直接进入匹配分析。">
          <div class="job-detail-meta-grid">
            <span>
              公司
              <strong>{{ job.companyName }}</strong>
            </span>
            <span>
              方向
              <strong>{{ job.jobCategory }}</strong>
            </span>
            <span>
              地点
              <strong>{{ job.location }}</strong>
            </span>
            <span>
              更新时间
              <strong>{{ formatDateTime(job.updatedAt) }}</strong>
            </span>
          </div>
        </BaseCard>

        <section class="job-detail-split">
          <BaseCard title="参考职责内容">
            <p class="job-detail-text">{{ job.description }}</p>
          </BaseCard>

          <BaseCard title="岗位要求">
            <p class="job-detail-text">{{ job.requirements }}</p>
          </BaseCard>
        </section>

        <BaseCard title="技能关键词" subtitle="可作为目标岗位 JD 的参考关键词。">
          <div class="job-tag-list">
            <el-tag v-for="skill in job.requiredSkills" :key="skill" type="success">{{ skill }}</el-tag>
          </div>
        </BaseCard>

        <BaseCard title="主流程入口" subtitle="岗位库不直接做主流程匹配，避免和目标岗位混淆。">
          <div class="job-detail-next-action">
            <div>
              <strong>基于真实 JD 新增目标岗位</strong>
              <p>复制岗位要求后，到“新增目标岗位”粘贴完整 JD，再解析并进入匹配与优化。</p>
            </div>
            <el-button type="primary" @click="router.push('/job-descriptions/new')">粘贴真实 JD</el-button>
          </div>
        </BaseCard>
      </template>
    </section>
  </section>
</template>

<style scoped>
.job-detail-page,
.job-detail-body {
  display: grid;
  gap: 18px;
}

.job-detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.job-detail-meta-grid span {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  background: var(--app-color-surface-soft);
}

.job-detail-meta-grid strong {
  color: var(--app-color-text);
}

.job-detail-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.job-detail-text {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.8;
  white-space: pre-wrap;
}

.job-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.job-detail-next-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.job-detail-next-action strong {
  color: var(--app-color-text);
  font-size: 18px;
}

.job-detail-next-action p {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  line-height: 1.7;
}

@media (max-width: 960px) {
  .job-detail-meta-grid,
  .job-detail-split {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .job-detail-next-action {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
