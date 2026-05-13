<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { submitJobDescription } from '@/api/job-description'

const router = useRouter()
const submitting = ref(false)

const form = reactive({
  title: '',
  rawText: '',
})

const handleSubmit = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请输入岗位描述标题')
    return
  }
  if (!form.rawText.trim()) {
    ElMessage.warning('请输入岗位描述原文')
    return
  }

  submitting.value = true

  try {
    const result = await submitJobDescription({
      title: form.title.trim(),
      rawText: form.rawText.trim(),
    })
    ElMessage.success('岗位描述提交成功')
    router.push(`/job-descriptions/${result.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交岗位描述失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="job-description-page">
    <section class="job-description-shell">
      <header class="job-description-header">
        <div>
          <h1 class="job-description-title">岗位描述解析</h1>
          <p class="job-description-subtitle">提交目标岗位 JD，抽取职位名称、技能要求、经验信号和职责内容。</p>
        </div>
        <el-space>
          <el-button @click="router.push('/job-descriptions')">我的岗位描述</el-button>
          <el-button @click="router.push('/jobs')">岗位列表</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </el-space>
      </header>

      <section class="job-description-panel">
        <el-form label-position="top">
          <el-form-item label="标题">
            <el-input
              v-model="form.title"
              maxlength="200"
              show-word-limit
              placeholder="例如：Java 后端开发工程师"
            />
          </el-form-item>

          <el-form-item label="岗位描述原文">
            <el-input
              v-model="form.rawText"
              type="textarea"
              :rows="16"
              maxlength="10000"
              show-word-limit
              placeholder="粘贴招聘 JD 原文，包含职责、要求、加分项等内容。"
            />
          </el-form-item>

          <div class="job-description-actions">
            <el-button @click="router.push('/jobs')">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
          </div>
        </el-form>
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
  width: min(100%, 960px);
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

.job-description-panel {
  padding: 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.job-description-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 640px) {
  .job-description-header,
  .job-description-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
