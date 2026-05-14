<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteJobDescription, getJobDescriptionList } from '@/api/job-description'
import type { JobDescriptionDetail } from '@/types/job-description'

const router = useRouter()
const records = ref<JobDescriptionDetail[]>([])
const loading = ref(false)
const deletingId = ref<number | null>(null)

const loadRecords = async () => {
  loading.value = true

  try {
    records.value = await getJobDescriptionList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取岗位描述列表失败')
  } finally {
    loading.value = false
  }
}

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const statusType = (status: string) => {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

const statusText = (status: string) => {
  if (status === 'SUCCESS') {
    return '已解析'
  }
  if (status === 'FAILED') {
    return '解析失败'
  }
  return '未解析'
}

const handleDelete = async (record: JobDescriptionDetail) => {
  try {
    await ElMessageBox.confirm(`确认删除「${record.title}」吗？关联的 AI 匹配结果也会一起删除。`, '删除岗位描述', {
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
    ElMessage.success('岗位描述删除成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位描述删除失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<template>
  <main class="job-description-page">
    <section class="job-description-shell">
      <header class="job-description-header">
        <div>
          <h1 class="job-description-title">我的岗位描述</h1>
          <p class="job-description-subtitle">查看已提交的岗位描述，继续解析或回看结构化结果。</p>
        </div>
        <el-space>
          <el-button type="primary" @click="router.push('/job-descriptions/new')">新建岗位描述</el-button>
          <el-button @click="router.push('/ai-job-matches')">AI 匹配</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </el-space>
      </header>

      <el-table v-loading="loading" :data="records" class="job-description-table" empty-text="暂无岗位描述">
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column label="解析状态" width="120">
          <template #default="{ row }: { row: JobDescriptionDetail }">
            <el-tag :type="statusType(row.parseStatus)">{{ statusText(row.parseStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" min-width="140">
          <template #default="{ row }: { row: JobDescriptionDetail }">
            {{ row.modelName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }: { row: JobDescriptionDetail }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }: { row: JobDescriptionDetail }">
            <el-space>
              <el-button size="small" type="primary" @click="router.push(`/job-descriptions/${row.id}`)">查看</el-button>
              <el-button
                size="small"
                :disabled="row.parseStatus !== 'SUCCESS'"
                @click="router.push(`/ai-job-matches?jobDescriptionId=${row.id}`)"
              >
                匹配
              </el-button>
              <el-button
                size="small"
                type="danger"
                :loading="deletingId === row.id"
                :disabled="deletingId !== null && deletingId !== row.id"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
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
  width: min(100%, 1100px);
  margin: 0 auto;
}

.job-description-header {
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

.job-description-table {
  border: 1px solid #dde5f0;
  border-radius: 8px;
}

@media (max-width: 640px) {
  .job-description-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
