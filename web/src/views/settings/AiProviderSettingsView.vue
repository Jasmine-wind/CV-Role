<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
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
const saving = ref(false)
const testing = ref(false)
const actionLoading = ref(false)
const settings = ref<AiProviderCredential | null>(null)
const testResult = ref<{ success: boolean; code?: string; message: string } | null>(null)

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
  try {
    settings.value = await getAiProviderSettings()
    if (settings.value) copySettingsToForm(settings.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '读取 AI Provider 配置失败')
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
      code: result.failureCode,
      message: result.message,
    }
    if (result.success) ElMessage.success('Provider 连接测试成功')
    else ElMessage.error(result.failureCode ? `${result.failureCode}：${result.message}` : result.message)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Provider 连接测试失败')
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
    ElMessage.success('配置已保存；为保护账号安全，当前仍处于停用状态')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存 AI Provider 配置失败')
  } finally {
    saving.value = false
    form.apiKey = ''
  }
}

const handleToggle = async () => {
  if (!configured.value || actionLoading.value) return
  actionLoading.value = true
  try {
    settings.value = active.value ? await disableAiProvider() : await enableAiProvider()
    copySettingsToForm(settings.value)
    ElMessage.success(active.value ? 'AI Provider 已启用' : 'AI Provider 已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新 Provider 状态失败')
  } finally {
    actionLoading.value = false
  }
}

const handleDelete = async () => {
  if (!configured.value || actionLoading.value) return
  try {
    await ElMessageBox.confirm(
      '删除后，新任务会使用系统默认 Provider；历史 BYOK 任务不会被删除，但后续调用会安全失败。',
      '确认删除 Provider 配置',
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
    ElMessage.success('Provider 配置已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除 Provider 配置失败')
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
      title="AI Provider"
      description="可选的账户级 OpenAI-compatible Provider。普通岗位分析流程无需配置此项。"
    />

    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else>
      <el-card class="settings-card" shadow="never">
        <template #header>
          <div class="settings-card-header">
            <div>
              <strong>账户级 Provider（BYOK）</strong>
              <p>API Key 只在本次表单请求和短生命周期测试/保存过程中使用，不会回显。</p>
            </div>
            <el-tag v-if="!configured" type="info">未配置</el-tag>
            <el-tag v-else :type="active ? 'success' : 'warning'">
              {{ active ? '已启用' : '已停用' }}
            </el-tag>
          </div>
        </template>

        <el-alert
          v-if="configured"
          class="settings-alert"
          type="info"
          :closable="false"
          :title="`当前 API Key：${settings?.maskedApiKey || '已配置'}；替换配置会自动停用，需再次显式启用。`"
        />

        <el-form label-position="top" class="settings-form" @submit.prevent="handleSave">
          <el-form-item label="Base URL" required>
            <el-input
              v-model="form.baseUrl"
              placeholder="https://api.example.com/v1"
              autocomplete="url"
            />
            <small>仅允许 HTTPS、标准 DNS hostname 和 443 端口。</small>
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
            <el-button v-if="configured" type="danger" plain :loading="actionLoading" @click="handleDelete">
              删除
            </el-button>
          </div>
        </el-form>

        <el-alert
          v-if="testResult"
          class="settings-alert"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false"
          :title="testResult.code ? `${testResult.code}：${testResult.message}` : testResult.message"
        />
      </el-card>
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
  border: 1px solid var(--color-border-soft, #e5e7eb);
  border-radius: 8px;
}

.settings-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.settings-card-header p,
.settings-form small {
  color: var(--color-text-secondary, #6b7280);
  font-size: 13px;
  line-height: 1.6;
}

.settings-card-header p {
  margin: 8px 0 0;
}

.settings-form {
  max-width: 640px;
  margin-top: 20px;
}

.settings-form small {
  display: block;
  margin-top: 5px;
}

.settings-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}

.settings-alert {
  margin-top: 16px;
}
</style>
