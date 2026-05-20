<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getJobList } from '@/api/job'
import type { JobListItem } from '@/types/job'

const router = useRouter()
const jobs = ref<JobListItem[]>([])
const loading = ref(false)

const loadJobs = async () => {
  loading.value = true

  try {
    jobs.value = await getJobList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取岗位库失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadJobs()
})
</script>

<template>
  <section class="job-page">
    <PageHeader
      eyebrow="岗位库参考"
      title="系统预置岗位只作为参考"
      description="岗位库不是维护系统岗位的入口，也不是用户真实投递主流程。真实 JD 请从目标岗位新增。"
    >
      <template #actions>
        <el-button type="primary" @click="router.push('/job-descriptions/new')">新增目标岗位</el-button>
        <el-button @click="router.push('/job-descriptions')">返回目标岗位</el-button>
      </template>
    </PageHeader>

    <section v-loading="loading" class="job-reference-grid">
      <article
        v-for="job in jobs"
        :key="job.id"
        class="job-reference-card"
      >
        <header>
          <div>
            <h3>{{ job.title }}</h3>
            <p>{{ job.companyName }} · {{ job.location }}</p>
          </div>
          <el-tag type="info">{{ job.jobCategory || '岗位参考' }}</el-tag>
        </header>
        <div class="job-tag-list">
          <el-tag v-for="skill in job.requiredSkills.slice(0, 8)" :key="skill" size="small">{{ skill }}</el-tag>
        </div>
        <footer>
          <el-button @click="router.push(`/jobs/${job.id}`)">查看参考详情</el-button>
          <el-button type="primary" plain @click="router.push('/job-descriptions/new')">基于真实 JD 新增</el-button>
        </footer>
      </article>
    </section>

    <EmptyState
      v-if="!loading && jobs.length === 0"
      title="暂无岗位库参考"
      description="岗位库只是辅助参考，不影响你从目标岗位粘贴真实 JD。"
      action-text="新增目标岗位"
      @action="router.push('/job-descriptions/new')"
    />
  </section>
</template>

<style scoped>
.job-page {
  display: grid;
  gap: 18px;
}

.job-reference-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.job-reference-card {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.job-reference-card header,
.job-reference-card footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.job-reference-card h3 {
  margin: 0;
  color: var(--app-color-text);
  font-size: 18px;
}

.job-reference-card p {
  margin: 6px 0 0;
  color: var(--app-color-text-secondary);
  line-height: 1.6;
}

.job-reference-card footer {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.job-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 1200px) {
  .job-reference-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .job-reference-grid {
    grid-template-columns: 1fr;
  }

  .job-reference-card header {
    flex-direction: column;
  }
}
</style>
