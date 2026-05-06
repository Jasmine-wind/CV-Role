<script setup lang="ts">
import type { UploadFile, UploadProps, UploadUserFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { deleteResume, getResumeList, getResumeParseResult, parseResume, uploadResume } from '@/api/resume'
import type { ResumeListItem, ResumeParseResult, ResumeStructuredContent } from '@/types/resume'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx']

const resumes = ref<ResumeListItem[]>([])
const selectedFile = ref<File | null>(null)
const uploadFiles = ref<UploadUserFile[]>([])
const loading = ref(false)
const uploading = ref(false)
const parsingResumeId = ref<number | null>(null)
const loadingParseResult = ref(false)
const deletingResumeId = ref<number | null>(null)
const activeResume = ref<ResumeListItem | null>(null)
const parseResult = ref<ResumeParseResult | null>(null)

const structuredContent = computed<ResumeStructuredContent | null>(() => {
  if (!parseResult.value?.structuredJson) {
    return null
  }

  try {
    return JSON.parse(parseResult.value.structuredJson) as ResumeStructuredContent
  } catch (error) {
    return null
  }
})

const formatFileSize = (size: number) => {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }

  return `${(size / 1024).toFixed(1)} KB`
}

const formatDateTime = (value: string) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const getFileExtension = (filename: string) => {
  const index = filename.lastIndexOf('.')

  if (index < 0 || index === filename.length - 1) {
    return ''
  }

  return filename.slice(index + 1).toLowerCase()
}

const validateFile = (file: File) => {
  const extension = getFileExtension(file.name)

  if (!ACCEPTED_EXTENSIONS.includes(extension)) {
    ElMessage.error('仅支持 PDF、DOC、DOCX 简历文件')
    return false
  }

  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('简历文件大小不能超过 10 MB')
    return false
  }

  return true
}

const loadResumes = async () => {
  loading.value = true

  try {
    resumes.value = await getResumeList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取简历列表失败')
  } finally {
    loading.value = false
  }
}

const handleFileChange: UploadProps['onChange'] = (uploadFile: UploadFile) => {
  if (!uploadFile.raw) {
    selectedFile.value = null
    return
  }

  if (!validateFile(uploadFile.raw)) {
    selectedFile.value = null
    uploadFiles.value = []
    return
  }

  selectedFile.value = uploadFile.raw
}

const handleFileRemove: UploadProps['onRemove'] = () => {
  selectedFile.value = null
}

const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择简历文件')
    return
  }

  uploading.value = true

  try {
    await uploadResume(selectedFile.value)
    ElMessage.success('上传成功')
    selectedFile.value = null
    uploadFiles.value = []
    await loadResumes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

const resolveParseStatusText = (status: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    PENDING: '待解析',
    PROCESSING: '解析中',
    SUCCESS: '解析成功',
    FAILED: '解析失败',
  }

  return status ? (statusMap[status] ?? status) : '-'
}

const resolveParseStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }

  if (status === 'FAILED') {
    return 'danger'
  }

  return 'info'
}

const selectResume = (resume: ResumeListItem) => {
  activeResume.value = resume
  parseResult.value = null
}

const loadParseResult = async (resume: ResumeListItem) => {
  selectResume(resume)
  loadingParseResult.value = true

  try {
    parseResult.value = await getResumeParseResult(resume.id)
  } catch (error) {
    parseResult.value = null
    ElMessage.warning(error instanceof Error ? error.message : '获取解析结果失败')
  } finally {
    loadingParseResult.value = false
  }
}

const handleParse = async (resume: ResumeListItem) => {
  selectResume(resume)
  parsingResumeId.value = resume.id

  try {
    parseResult.value = await parseResume(resume.id)
    if (parseResult.value.parseStatus === 'FAILED') {
      ElMessage.error(parseResult.value.errorMessage || '解析失败')
    } else {
      ElMessage.success('解析完成')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解析失败')
  } finally {
    parsingResumeId.value = null
  }
}

const handleDelete = async (resume: ResumeListItem) => {
  try {
    await ElMessageBox.confirm(`确认删除「${resume.originalFilename}」吗？`, '删除简历', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (error) {
    return
  }

  deletingResumeId.value = resume.id

  try {
    await deleteResume(resume.id)
    ElMessage.success('删除成功')

    if (activeResume.value?.id === resume.id) {
      activeResume.value = null
      parseResult.value = null
    }

    await loadResumes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  } finally {
    deletingResumeId.value = null
  }
}

const hasStructuredList = (items: string[] | null | undefined) => {
  return Array.isArray(items) && items.length > 0
}

onMounted(() => {
  loadResumes()
})
</script>

<template>
  <main class="resume-page">
    <section class="resume-shell">
      <header class="resume-header">
        <div>
          <h1 class="resume-title">我的简历</h1>
          <p class="resume-subtitle">上传 PDF、DOC 或 DOCX 简历，并查看已上传记录。</p>
        </div>
        <el-button :loading="loading" @click="loadResumes">刷新列表</el-button>
      </header>

      <section class="resume-upload-panel">
        <el-upload
          v-model:file-list="uploadFiles"
          accept=".pdf,.doc,.docx"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-button type="primary">选择简历文件</el-button>
          <template #tip>
            <div class="resume-upload-tip">支持 PDF、DOC、DOCX，最大 10 MB。</div>
          </template>
        </el-upload>

        <el-button type="success" :loading="uploading" @click="handleUpload">上传</el-button>
      </section>

      <el-table v-loading="loading" :data="resumes" class="resume-table" empty-text="暂无简历">
        <el-table-column prop="originalFilename" label="文件名" min-width="220" />
        <el-table-column prop="fileType" label="类型" width="100" />
        <el-table-column label="大小" width="130">
          <template #default="{ row }: { row: ResumeListItem }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="uploadStatus" label="状态" width="120" />
        <el-table-column label="上传时间" width="190">
          <template #default="{ row }: { row: ResumeListItem }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }: { row: ResumeListItem }">
            <div class="resume-actions">
              <el-button
                size="small"
                type="primary"
                :loading="parsingResumeId === row.id"
                @click="handleParse(row)"
              >
                开始解析
              </el-button>
              <el-button size="small" :loading="loadingParseResult && activeResume?.id === row.id" @click="loadParseResult(row)">
                查看结果
              </el-button>
              <el-button
                size="small"
                type="danger"
                :loading="deletingResumeId === row.id"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <section v-if="activeResume" v-loading="loadingParseResult" class="resume-parse-panel">
        <header class="resume-parse-header">
          <div>
            <h2 class="resume-section-title">解析结果</h2>
            <p class="resume-section-subtitle">{{ activeResume.originalFilename }}</p>
          </div>
          <el-tag :type="resolveParseStatusType(parseResult?.parseStatus)">
            {{ resolveParseStatusText(parseResult?.parseStatus) }}
          </el-tag>
        </header>

        <el-alert
          v-if="!parseResult"
          title="当前简历暂无解析结果"
          type="info"
          :closable="false"
          show-icon
        />

        <template v-else>
          <el-alert
            v-if="parseResult.parseStatus === 'FAILED'"
            :title="parseResult.errorMessage || '解析失败'"
            type="error"
            :closable="false"
            show-icon
          />

          <section class="resume-structured-section">
            <h3 class="resume-block-title">基础字段</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="姓名">{{ structuredContent?.name || '-' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ structuredContent?.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ structuredContent?.email || '-' }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDateTime(parseResult.updatedAt || '') }}</el-descriptions-item>
            </el-descriptions>
          </section>

          <section class="resume-structured-section">
            <h3 class="resume-block-title">技能关键词</h3>
            <div v-if="hasStructuredList(structuredContent?.skills)" class="resume-tag-list">
              <el-tag v-for="skill in structuredContent?.skills" :key="skill" type="success">{{ skill }}</el-tag>
            </div>
            <el-empty v-else description="暂无技能关键词" :image-size="72" />
          </section>

          <section class="resume-structured-grid">
            <div>
              <h3 class="resume-block-title">教育经历</h3>
              <ul v-if="hasStructuredList(structuredContent?.education)" class="resume-text-list">
                <li v-for="item in structuredContent?.education" :key="item">{{ item }}</li>
              </ul>
              <el-empty v-else description="暂无教育经历" :image-size="72" />
            </div>

            <div>
              <h3 class="resume-block-title">项目经历</h3>
              <ul v-if="hasStructuredList(structuredContent?.projects)" class="resume-text-list">
                <li v-for="item in structuredContent?.projects" :key="item">{{ item }}</li>
              </ul>
              <el-empty v-else description="暂无项目经历" :image-size="72" />
            </div>

            <div>
              <h3 class="resume-block-title">实习/工作经历</h3>
              <ul v-if="hasStructuredList(structuredContent?.internships)" class="resume-text-list">
                <li v-for="item in structuredContent?.internships" :key="item">{{ item }}</li>
              </ul>
              <el-empty v-else description="暂无实习或工作经历" :image-size="72" />
            </div>
          </section>

          <section class="resume-structured-section">
            <h3 class="resume-block-title">原始文本</h3>
            <pre class="resume-raw-text">{{ parseResult.extractedText || '-' }}</pre>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>

<style scoped>
.resume-page {
  min-height: 100vh;
  padding: 48px 20px;
  background: #f4f7fb;
}

.resume-shell {
  width: min(100%, 1040px);
  margin: 0 auto;
}

.resume-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.resume-title {
  margin: 0;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.resume-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.7;
}

.resume-upload-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 24px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.resume-upload-tip {
  margin-top: 8px;
  color: #667085;
  font-size: 13px;
}

.resume-table {
  border: 1px solid #dde5f0;
  border-radius: 8px;
}

.resume-actions {
  display: flex;
  gap: 8px;
}

.resume-parse-panel {
  margin-top: 20px;
  padding: 24px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.resume-parse-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.resume-section-title {
  margin: 0;
  color: #111827;
  font-size: 20px;
  font-weight: 700;
}

.resume-section-subtitle {
  margin: 6px 0 0;
  color: #667085;
  font-size: 14px;
}

.resume-structured-section {
  margin-top: 20px;
}

.resume-structured-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 20px;
}

.resume-block-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.resume-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.resume-text-list {
  min-height: 88px;
  margin: 0;
  padding: 14px 16px 14px 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  color: #344054;
  line-height: 1.7;
}

.resume-raw-text {
  max-height: 360px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 16px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  color: #344054;
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    monospace;
  font-size: 13px;
  line-height: 1.7;
  background: #f8fafc;
}

@media (max-width: 640px) {
  .resume-header,
  .resume-upload-panel,
  .resume-parse-header {
    align-items: stretch;
    flex-direction: column;
  }

  .resume-actions {
    flex-direction: column;
  }

  .resume-structured-grid {
    grid-template-columns: 1fr;
  }
}
</style>
