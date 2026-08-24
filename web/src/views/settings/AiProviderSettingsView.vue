<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import {
  deleteAiProvider,
  disableAiProvider,
  enableAiProvider,
  getAiProviderSettings,
  saveAiProviderSettings,
  testAiProvider,
  type AiProviderCredential,
} from '@/api/ai-provider'

const loading = ref(true)
const loadFailed = ref(false)
const saving = ref(false)
const testing = ref(false)
const actionLoading = ref(false)
const settings = ref<AiProviderCredential | null>(null)
const testResult = ref<{ success: boolean; message: string } | null>(null)

const form = reactive({
  baseUrl: '',
  apiKey: '',
  model: '',
})

const configured = computed(() => Boolean(settings.value?.configured))
const active = computed(() => settings.value?.status === 'ACTIVE')
const canSubmit = computed(() => Boolean(form.baseUrl.trim() && form.apiKey.trim() && form.model.trim()))

const copySettingsToForm = (value: AiProviderCredential) => {
  form.baseUrl = value.baseUrl || ''
  form.model = value.model || ''
  // The API key intentionally never comes from the API response.
  form.apiKey = ''
}

const load = async () => {
  loading.value = true
  loadFailed.value = false
  try {
    settings.value = await getAiProviderSettings()
    if (settings.value) copySettingsToForm(settings.value)
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

const input = () => ({
  baseUrl: form.baseUrl.trim(),
  apiKey: form.apiKey,
  model: form.model.trim(),
})

const handleTest = async () => {
  if (!canSubmit.value || testing.value) return
  testing.value = true
  testResult.value = null
  try {
    const result = await testAiProvider(input())
    testResult.value = {
      success: result.success,
      message: result.message,
    }
    if (result.success) ElMessage.success('连接测试成功')
    else ElMessage.error(result.message || '连接测试没有通过')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '连接测试失败')
  } finally {
    testing.value = false
    form.apiKey = ''
  }
}

const handleSave = async () => {
  if (!canSubmit.value || saving.value) return
  saving.value = true
  try {
    settings.value = await saveAiProviderSettings(input())
    copySettingsToForm(settings.value)
    testResult.value = null
    ElMessage.success('配置已保存；为保护账号安全，当前仍处于停用状态，需要手动启用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存配置失败')
  } finally {
    saving.value = false
    form.apiKey = ''
  }
}

const handleToggle = async () => {
  if (!configured.value || actionLoading.value) return
  const wasActive = active.value
  actionLoading.value = true
  try {
    settings.value = wasActive ? await disableAiProvider() : await enableAiProvider()
    copySettingsToForm(settings.value)
    ElMessage.success(wasActive ? '已停用你的 API 密钥，新任务将使用系统提供的 AI' : '已启用你的 API 密钥')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新状态失败')
  } finally {
    actionLoading.value = false
  }
}

const handleDelete = async () => {
  if (!configured.value || actionLoading.value) return
  try {
    await ElMessageBox.confirm(
      '删除后，你保存的 API 密钥将被移除且无法恢复；新的岗位分析会使用系统提供的 AI。是否确认删除？',
      '删除你的 API 密钥',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  actionLoading.value = true
  try {
    await deleteAiProvider()
    settings.value = await getAiProviderSettings()
    copySettingsToForm(settings.value)
    ElMessage.success('已删除你的 API 密钥')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  } finally {
    actionLoading.value = false
    form.apiKey = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="settings-page">
    <PageHeader
      eyebrow="账户设置"
      title="AI 设置"
      description="普通岗位分析使用系统提供的 AI，无需任何配置即可完成主流程。只有当你希望使用自己的 API 密钥时，才需要在这里配置。"
    />

    <SkeletonBlock v-if="loading" title :rows="8" />

    <ErrorState
      v-else-if="loadFailed"
      title="AI 设置加载失败"
      description="暂时无法读取你的配置状态，已保存的配置不受影响。"
      action-text="重新加载"
      @action="load"
    />

    <template v-else>
      <section class="settings-card app-card">
        <header class="settings-card-header">
          <div>
            <strong>使用自己的 API 密钥</strong>
            <p>可选配置。API 密钥只在本次表单请求和短生命周期的测试 / 保存过程中使用，页面不会回显或持久化。</p>
          </div>
          <el-tag v-if="!configured" type="info" effect="light">未配置</el-tag>
          <el-tag v-else :type="active ? 'success' : 'warning'" effect="light">
            {{ active ? '已启用' : '已停用' }}
          </el-tag>
        </header>

        <el-alert
          v-if="configured"
          class="settings-alert"
          type="info"
          :closable="false"
          :title="`当前密钥：${settings?.maskedApiKey || '已配置'}；替换配置后会自动停用，需要再次显式启用。`"
        />

        <el-form label-position="top" class="settings-form" @submit.prevent="handleSave">
          <el-form-item label="Base URL" required>
            <el-input
              v-model="form.baseUrl"
              placeholder="https://api.example.com/v1"
              autocomplete="url"
            />
            <small>仅允许 HTTPS、标准域名和 443 端口。</small>
          </el-form-item>

          <el-form-item label="API Key" required>
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="输入后用于测试或保存；页面不会持久化"
            />
          </el-form-item>

          <el-form-item label="Model" required>
            <el-input v-model="form.model" placeholder="例如 gpt-4o-mini" autocomplete="off" />
          </el-form-item>

          <div class="settings-actions">
            <el-button :loading="testing" :disabled="!canSubmit" @click="handleTest">测试连接</el-button>
            <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="handleSave">
              {{ configured ? '保存并替换' : '保存配置' }}
            </el-button>
            <el-button
              v-if="configured"
              :loading="actionLoading"
              :disabled="!configured"
              @click="handleToggle"
            >
              {{ active ? '停用' : '启用' }}
            </el-button>
          </div>
        </el-form>

        <el-alert
          v-if="testResult"
          class="settings-alert"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false"
          :title="testResult.message"
        />

        <footer v-if="configured" class="settings-danger">
          <div>
            <strong>删除密钥</strong>
            <p>删除后新任务会使用系统提供的 AI；已保存的密钥无法恢复。</p>
          </div>
          <el-button type="danger" plain :loading="actionLoading" @click="handleDelete">
            删除
          </el-button>
        </footer>
      </section>
    </template>
  </section>
</template>

<style scoped>
.settings-page {
  display: grid;
  gap: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.settings-card {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.settings-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.settings-card-header strong {
  color: var(--app-text);
  font-size: 16px;
}

.settings-card-header p,
.settings-form small {
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.settings-card-header p {
  margin: 6px 0 0;
}

.settings-form {
  max-width: 640px;
}

.settings-form small {
  display: block;
  margin-top: 5px;
}

.settings-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.settings-alert {
  margin: 0;
}

.settings-danger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: var(--app-radius-md);
  background: var(--app-danger-soft);
}

.settings-danger strong {
  color: var(--app-text);
  font-size: 14px;
}

.settings-danger p {
  margin: 4px 0 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .settings-card-header,
  .settings-danger {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
