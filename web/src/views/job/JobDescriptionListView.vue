<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import JobDescriptionCard from '@/components/job/JobDescriptionCard.vue'
import { deleteJobDescription, generateJobDescriptionEmbedding, getJobDescriptionList, parseJobDescription } from '@/api/job-description'
import type { JobDescriptionDetail } from '@/types/job-description'

const router = useRouter()
const records = ref<JobDescriptionDetail[]>([])
const loading = ref(false)
const deletingId = ref<number | null>(null)
const parsingId = ref<number | null>(null)

const parsedCount = computed(() => records.value.filter((item) => item.parseStatus === 'SUCCESS').length)

const loadRecords = async () => {
  loading.value = true

  try {
    records.value = await getJobDescriptionList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取目标岗位列表失败')
  } finally {
    loading.value = false
  }
}

const handleParse = async (record: JobDescriptionDetail) => {
  parsingId.value = record.id

  try {
    const next = await parseJobDescription(record.id)
    records.value = records.value.map((item) => item.id === record.id ? next : item)
    if (next.parseStatus === 'FAILED') {
      ElMessage.error(next.errorMessage || '目标岗位解析失败')
    } else {
      ElMessage.success('目标岗位解析完成')
      await generateJobDescriptionEmbedding(next.id).catch((error) => {
        ElMessage.warning(error instanceof Error ? `岗位向量生成失败：${error.message}` : '岗位向量生成失败')
      })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '目标岗位解析失败')
  } finally {
    parsingId.value = null
  }
}

const handleDelete = async (record: JobDescriptionDetail) => {
  try {
    await ElMessageBox.confirm(`确认删除「${record.title}」吗？关联的匹配分析结果也会一起删除。`, '删除目标岗位', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  deletingId.value = record.id

  try {
    await deleteJobDescription(record.id)
    records.value = records.value.filter((item) => item.id !== record.id)
    ElMessage.success('目标岗位删除成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '目标岗位删除失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<template>
  <section class="job-description-page">
    <PageHeader
      eyebrow="目标岗位"
      title="保存你自己的真实目标 JD"
      description="这里保存用户粘贴的投递岗位，不维护系统预置岗位。解析完成后再进入匹配与优化。"
    >
      <template #actions>
        <el-button @click="router.push('/jobs')">岗位库参考</el-button>
        <el-button type="primary" @click="router.push('/job-descriptions/new')">新增目标岗位</el-button>
      </template>
    </PageHeader>

    <section v-loading="loading" class="job-description-overview">
      <article>
        <span>目标岗位</span>
        <strong>{{ records.length }}</strong>
        <small>用户粘贴 JD</small>
      </article>
      <article>
        <span>已解析</span>
        <strong>{{ parsedCount }}</strong>
        <small>可用于匹配分析</small>
      </article>
      <article>
        <span>主流程</span>
        <strong>匹配与优化</strong>
        <small>从解析成功的目标岗位进入</small>
      </article>
    </section>

    <SkeletonBlock v-if="loading" title :rows="6" />

    <section v-else-if="records.length" class="job-description-card-grid">
      <JobDescriptionCard
        v-for="record in records"
        :key="record.id"
        :record="record"
        :class="{ 'is-busy': deletingId === record.id || parsingId === record.id }"
        @detail="router.push(`/job-descriptions/${record.id}`)"
        @parse="handleParse"
        @match="router.push(`/ai-job-matches?jobDescriptionId=${record.id}`)"
        @delete="handleDelete"
      />
    </section>

    <EmptyState
      v-else
      title="你还没有添加目标岗位"
      description="粘贴一份真实 JD 后，系统可以分析岗位要求并与你的简历进行匹配。"
      secondary-text="下一步建议：复制目标招聘 JD，新增后先完成解析。"
      action-text="新增目标岗位"
      @action="router.push('/job-descriptions/new')"
    />
    <div v-if="!loading && records.length === 0" class="job-description-empty-secondary">
      <el-button @click="router.push('/jobs')">查看岗位库参考</el-button>
    </div>
  </section>
</template>

<style scoped>
.job-description-page {
  display: grid;
  gap: 18px;
}

.job-description-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.job-description-overview article {
  display: grid;
  gap: 5px;
  min-height: 116px;
  align-content: center;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.job-description-overview span,
.job-description-overview small {
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.job-description-overview strong {
  color: var(--app-color-text);
  font-size: 26px;
  line-height: 1.1;
}

.job-description-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.job-description-card-grid .is-busy {
  opacity: 0.72;
  pointer-events: none;
}

.job-description-empty-secondary {
  display: flex;
  justify-content: center;
  margin-top: -6px;
}

@media (max-width: 960px) {
  .job-description-overview,
  .job-description-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
