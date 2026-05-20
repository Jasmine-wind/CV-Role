<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import BaseCard from '@/components/common/BaseCard.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { submitJobDescription } from '@/api/job-description'

const router = useRouter()
const submitting = ref(false)

const form = reactive({
  title: '',
  rawText: '',
})

const handleSubmit = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请输入目标岗位标题')
    return
  }
  if (!form.rawText.trim()) {
    ElMessage.warning('请输入目标岗位 JD 原文')
    return
  }

  submitting.value = true

  try {
    const result = await submitJobDescription({
      title: form.title.trim(),
      rawText: form.rawText.trim(),
    })
    ElMessage.success('目标岗位提交成功')
    router.push(`/job-descriptions/${result.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交目标岗位失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="job-description-create-page">
    <PageHeader
      eyebrow="新增目标岗位"
      title="粘贴真实招聘 JD"
      description="目标岗位固定来自用户输入，不进入系统预置岗位库，也不依赖岗位爬取。"
    >
      <template #actions>
        <el-button @click="router.push('/job-descriptions')">返回目标岗位</el-button>
      </template>
    </PageHeader>

    <BaseCard title="目标岗位信息" subtitle="建议保留职责、要求、加分项和经验要求，解析结果会用于后续匹配分析。">
      <el-alert
        class="job-description-alert"
        title="这里不维护系统预置岗位，只保存你自己的目标 JD。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-form label-position="top" class="job-description-form">
        <el-form-item label="标题">
          <el-input
            v-model="form.title"
            maxlength="200"
            show-word-limit
            placeholder="例如：Java 后端开发工程师"
          />
        </el-form-item>

        <el-form-item label="目标岗位 JD 原文">
          <el-input
            v-model="form.rawText"
            type="textarea"
            :rows="18"
            maxlength="10000"
            show-word-limit
            placeholder="粘贴招聘 JD 原文，包含职责、要求、加分项等内容。"
          />
        </el-form-item>

        <div class="job-description-actions">
          <el-button @click="router.push('/job-descriptions')">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交目标岗位</el-button>
        </div>
      </el-form>
    </BaseCard>
  </section>
</template>

<style scoped>
.job-description-create-page {
  display: grid;
  gap: 18px;
  max-width: 980px;
}

.job-description-alert {
  margin-bottom: 18px;
}

.job-description-form {
  display: grid;
  gap: 6px;
}

.job-description-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 640px) {
  .job-description-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
