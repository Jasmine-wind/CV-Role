<script setup lang="ts">
import type { UploadFile, UploadProps, UploadUserFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { getResumeList, uploadResume } from '@/api/resume'
import type { ResumeListItem } from '@/types/resume'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx']

const resumes = ref<ResumeListItem[]>([])
const selectedFile = ref<File | null>(null)
const uploadFiles = ref<UploadUserFile[]>([])
const loading = ref(false)
const uploading = ref(false)

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
      </el-table>
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

@media (max-width: 640px) {
  .resume-header,
  .resume-upload-panel {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
