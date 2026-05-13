<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
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
    ElMessage.error(error instanceof Error ? error.message : '获取岗位列表失败')
  } finally {
    loading.value = false
  }
}

const goDetail = (job: JobListItem) => {
  router.push(`/jobs/${job.id}`)
}

onMounted(() => {
  loadJobs()
})
</script>

<template>
  <main class="job-page">
    <section class="job-shell">
      <header class="job-header">
        <div>
          <h1 class="job-title">岗位列表</h1>
          <p class="job-subtitle">选择目标岗位，查看岗位要求与技能关键词。</p>
        </div>
        <el-space>
          <el-button @click="router.push('/job-descriptions')">岗位解析</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </el-space>
      </header>

      <el-table v-loading="loading" :data="jobs" class="job-table" empty-text="暂无可用岗位">
        <el-table-column prop="title" label="岗位名称" min-width="180" />
        <el-table-column prop="companyName" label="公司" min-width="150" />
        <el-table-column prop="jobCategory" label="方向" width="140" />
        <el-table-column prop="location" label="地点" width="120" />
        <el-table-column label="技能要求" min-width="260">
          <template #default="{ row }: { row: JobListItem }">
            <div class="job-tag-list">
              <el-tag v-for="skill in row.requiredSkills" :key="skill" size="small">{{ skill }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }: { row: JobListItem }">
            <el-button size="small" type="primary" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
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
  width: min(100%, 1200px);
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

.job-table {
  border: 1px solid #dde5f0;
  border-radius: 8px;
}

.job-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 640px) {
  .job-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
