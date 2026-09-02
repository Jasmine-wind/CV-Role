<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ErrorState from '@/components/common/ErrorState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
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
const saveError = ref<string | null>(null)
const actionError = ref<string | null>(null)

const form = reactive({
  baseUrl: '',
  apiKey: '',
  model: '',
})

const configured = computed(() => Boolean(settings.value?.configured))
const active = computed(() => settings.value?.status === 'ACTIVE')
const canSubmit = computed(() =>
  Boolean(form.baseUrl.trim() && form.apiKey.trim() && form.model.trim()),
)
const statusLabel = computed(() => {
  if (!configured.value) return '未配置'
  return active.value ? '已启用' : '已停用'
})

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
    testResult.value = {
      success: false,
      message: error instanceof Error ? error.message : '连接测试失败',
    }
    ElMessage.error(testResult.value.message)
  } finally {
    testing.value = false
    form.apiKey = ''
  }
}

const handleSave = async () => {
  if (!canSubmit.value || saving.value) return
  saving.value = true
  saveError.value = null
  try {
    settings.value = await saveAiProviderSettings(input())
    copySettingsToForm(settings.value)
    testResult.value = null
    ElMessage.success('配置已保存；为保护账号安全，当前仍处于停用状态，需要手动启用')
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : '保存配置失败'
    ElMessage.error(saveError.value)
  } finally {
    saving.value = false
    form.apiKey = ''
  }
}

const handleToggle = async () => {
  if (!configured.value || actionLoading.value) return
  const wasActive = active.value
  actionError.value = null
  actionLoading.value = true
  try {
    settings.value = wasActive ? await disableAiProvider() : await enableAiProvider()
    copySettingsToForm(settings.value)
    ElMessage.success(
      wasActive ? '已停用你的 API 密钥，新任务将使用系统提供的 AI' : '已启用你的 API 密钥',
    )
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '更新状态失败'
    ElMessage.error(actionError.value)
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

  actionError.value = null
  actionLoading.value = true
  try {
    await deleteAiProvider()
    settings.value = await getAiProviderSettings()
    copySettingsToForm(settings.value)
    ElMessage.success('已删除你的 API 密钥')
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '删除失败'
    ElMessage.error(actionError.value)
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
      description="普通岗位分析无需配置。只有使用自己的 API 密钥时，才需要填写这里。"
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
      <div class="settings-layout">
        <section class="settings-configuration" aria-label="Provider configuration">
          <header class="settings-section-header">
            <span class="settings-section-label">PROVIDER CONFIGURATION</span>
            <h2>使用自己的 API 密钥</h2>
            <p>可选配置。密钥不会在页面回显；保存后可以停用、替换或删除。</p>
          </header>

          <p v-if="configured" class="settings-current-key">
            当前密钥：<strong>{{ settings?.maskedApiKey || '已配置' }}</strong> · 替换配置后会自动停用，需要再次显式启用。
          </p>

          <el-form label-position="top" class="settings-form" @submit.prevent="handleSave">
            <el-form-item label="Base URL" required>
              <el-input
                v-model="form.baseUrl"
                placeholder="https://api.example.com/v1"
                autocomplete="url"
              />
              <small>连接地址需使用 HTTPS 和标准 443 端口。</small>
            </el-form-item>

            <el-form-item label="API Key" required>
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="输入后用于测试或保存；保存后不会再次显示"
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
              <el-button v-if="configured" :loading="actionLoading" @click="handleToggle">
                {{ active ? '停用' : '启用' }}
              </el-button>
            </div>
          </el-form>

          <section v-if="testResult" class="settings-test-result" :class="testResult.success ? 'is-success' : 'is-error'" role="status">
            <strong>{{ testResult.success ? '连接测试成功' : '连接测试失败' }}</strong>
            <p>{{ testResult.message }}</p>
          </section>
          <section v-if="saveError" class="settings-test-result is-error" role="alert">
            <strong>保存配置失败</strong>
            <p>{{ saveError }}</p>
          </section>
        </section>

        <aside class="settings-context" aria-label="Provider status and security">
          <section class="settings-context-section">
            <span class="settings-section-label">STATUS</span>
            <dl class="settings-status-list">
              <div><dt>状态</dt><dd :class="active ? 'is-active' : configured ? 'is-disabled' : 'is-muted'">{{ statusLabel }}</dd></div>
              <div><dt>Provider</dt><dd>{{ settings?.providerType || 'OpenAI-compatible' }}</dd></div>
              <div v-if="configured"><dt>Credential</dt><dd>{{ settings?.maskedApiKey || '已配置' }}</dd></div>
            </dl>
          </section>

          <section v-if="actionError" class="settings-context-section settings-action-error" role="alert">
            <span class="settings-section-label">STATUS UPDATE</span>
            <p>{{ actionError }}</p>
          </section>

          <section class="settings-context-section">
            <span class="settings-section-label">SECURITY</span>
            <p>API Key 只用于服务端连接，不会再次回显，也不会进入浏览器日志或页面存储。</p>
            <p>停用 BYOK 后，新任务使用系统提供的 AI；不会静默改写历史任务。</p>
          </section>
        </aside>
      </div>

      <section v-if="configured" class="settings-danger-zone">
        <div>
          <span class="settings-section-label">DANGER ZONE</span>
          <h2>删除密钥</h2>
          <p>删除后新任务会使用系统提供的 AI；已保存的密钥无法恢复。</p>
        </div>
        <el-button type="danger" plain :loading="actionLoading" @click="handleDelete">删除</el-button>
      </section>
    </template>
  </section>
</template>

<style scoped>
.settings-page {
  display: grid;
  gap: var(--app-section-spacing);
}

.settings-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.42fr);
  gap: var(--app-space-8);
  border-top: 1px solid var(--app-border-strong);
  border-bottom: 1px solid var(--app-border-strong);
}

.settings-configuration,
.settings-context {
  min-width: 0;
  padding: var(--app-space-6) 0 var(--app-space-8);
}

.settings-context {
  border-left: 1px solid var(--app-border-strong);
  padding-right: var(--app-space-5);
  padding-left: var(--app-space-6);
}

.settings-section-header {
  display: grid;
  gap: var(--app-space-2);
  margin-bottom: var(--app-space-6);
}

.settings-section-label {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.settings-section-header h2,
.settings-danger-zone h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  line-height: var(--app-line-height-tight);
}

.settings-section-header p,
.settings-context-section p,
.settings-danger-zone p,
.settings-current-key,
.settings-form small,
.settings-test-result p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.settings-form {
  max-width: 680px;
}

.settings-form small {
  display: block;
  margin-top: var(--app-space-1);
}

.settings-current-key {
  margin-bottom: var(--app-space-5);
  color: var(--app-text-secondary);
}

.settings-current-key strong {
  color: var(--app-text);
  font-family: var(--app-font-mono);
  font-weight: 700;
}

.settings-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  margin-top: var(--app-space-2);
}

.settings-test-result {
  display: grid;
  gap: var(--app-space-1);
  margin-top: var(--app-space-5);
  border-top: 1px solid var(--app-border);
  padding-top: var(--app-space-4);
}

.settings-test-result strong {
  color: var(--app-text);
  font-size: var(--app-font-size-sm);
}

.settings-test-result.is-success strong {
  color: var(--app-success);
}

.settings-test-result.is-error strong {
  color: var(--app-danger);
}

.settings-context {
  display: grid;
  align-content: start;
  gap: var(--app-space-8);
}

.settings-context-section {
  display: grid;
  gap: var(--app-space-3);
}

.settings-action-error {
  border-top: 1px solid var(--app-danger);
  padding-top: var(--app-space-4);
}

.settings-action-error .settings-section-label,
.settings-action-error p {
  color: var(--app-danger);
}

.settings-status-list {
  display: grid;
  gap: var(--app-space-3);
  margin: 0;
}

.settings-status-list div {
  display: flex;
  justify-content: space-between;
  gap: var(--app-space-3);
  border-bottom: 1px solid var(--app-border-soft);
  padding-bottom: var(--app-space-3);
}

.settings-status-list dt,
.settings-status-list dd {
  margin: 0;
  font-size: var(--app-font-size-sm);
}

.settings-status-list dt {
  color: var(--app-text-secondary);
}

.settings-status-list dd {
  overflow-wrap: anywhere;
  color: var(--app-text);
  font-weight: 700;
  text-align: right;
}

.settings-status-list dd.is-active {
  color: var(--app-success);
}

.settings-status-list dd.is-disabled {
  color: var(--app-warning);
}

.settings-status-list dd.is-muted {
  color: var(--app-text-muted);
}

.settings-danger-zone {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--app-space-6);
  border-top: 1px solid var(--app-danger);
  padding-top: var(--app-space-5);
}

.settings-danger-zone > div {
  display: grid;
  gap: var(--app-space-2);
}

.settings-danger-zone .settings-section-label,
.settings-danger-zone h2 {
  color: var(--app-danger);
}

@media (max-width: 900px) {
  .settings-layout {
    display: block;
  }

  .settings-context {
    border-top: 1px solid var(--app-border-strong);
    border-left: 0;
    padding: var(--app-space-5) 0 var(--app-space-6);
  }
}

@media (max-width: 640px) {
  .settings-configuration {
    padding-top: var(--app-space-5);
  }

  .settings-actions,
  .settings-actions .el-button,
  .settings-danger-zone .el-button {
    width: 100%;
  }

  .settings-actions {
    display: grid;
  }

  .settings-danger-zone {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
