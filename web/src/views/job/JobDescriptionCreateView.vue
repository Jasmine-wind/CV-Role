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
      description="粘贴你准备投递的岗位 JD，系统会抽取技能、职责和经验要求。"
    >
      <template #actions>
        <el-button @click="router.push('/job-descriptions')">返回目标岗位</el-button>
      </template>
    </PageHeader>

    <section class="job-description-create-layout">
      <BaseCard title="目标岗位信息" subtitle="建议保留职责、要求、加分项和经验要求，解析结果会用于后续匹配分析。">
        <el-alert
          class="job-description-alert"
          title="这里保存你自己的目标 JD，不写入系统岗位库。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-form label-position="top" class="job-description-form">
          <el-form-item label="岗位标题">
            <el-input
              v-model="form.title"
              maxlength="200"
              show-word-limit
              placeholder="例如：Java 后端开发工程师 / 某公司后端岗位"
            />
          </el-form-item>

          <el-form-item label="目标岗位 JD 原文">
            <el-input
              v-model="form.rawText"
              type="textarea"
              :rows="20"
              maxlength="10000"
              show-word-limit
              placeholder="粘贴招聘 JD 原文，包含职责、要求、加分项、经验要求等内容。"
            />
          </el-form-item>

          <div class="job-description-actions">
            <el-button @click="router.push('/job-descriptions')">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">保存目标岗位</el-button>
          </div>
        </el-form>
      </BaseCard>

      <BaseCard title="粘贴建议" subtitle="让后续解析和匹配更稳定。">
        <div class="job-description-guide-list">
          <article>
            <strong>保留完整 JD</strong>
            <p>职责、任职要求、加分项和经验要求都应保留，不要只粘贴岗位标题。</p>
          </article>
          <article>
            <strong>避免敏感信息</strong>
            <p>不要填写个人隐私、内推联系人手机号或未公开薪酬信息。</p>
          </article>
          <article>
            <strong>保存后再解析</strong>
            <p>保存成功会进入详情页，解析后才能和简历进行匹配分析。</p>
          </article>
        </div>
      </BaseCard>
    </section>
  </section>
</template>

<style scoped>
.job-description-create-page {
  display: grid;
  gap: 18px;
}

.job-description-create-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.68fr) minmax(280px, 0.32fr);
  gap: 18px;
  align-items: start;
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

.job-description-guide-list {
  display: grid;
  gap: 14px;
}

.job-description-guide-list article {
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.job-description-guide-list strong {
  color: var(--app-color-text);
}

.job-description-guide-list p {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  line-height: 1.7;
}

@media (max-width: 960px) {
  .job-description-create-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .job-description-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
