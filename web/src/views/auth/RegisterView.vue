<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'

interface RegisterForm {
  username: string
  email: string
  password: string
  nickname: string
}

const formRef = ref<FormInstance>()
const router = useRouter()
const loading = ref(false)

const form = reactive<RegisterForm>({
  username: '',
  email: '',
  password: '',
  nickname: '',
})

const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度应在 3 到 20 个字符之间', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
    { max: 100, message: '邮箱不能超过 100 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度应在 6 到 20 个字符之间', trigger: 'blur' },
  ],
  nickname: [{ max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' }],
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()

  if (!valid) {
    return
  }

  loading.value = true

  try {
    await register({
      username: form.username,
      email: form.email,
      password: form.password,
      nickname: form.nickname || undefined,
    })
    ElMessage.success('注册成功，请登录')
    await router.push('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <RouterLink to="/" class="auth-brand">
        <span>AI</span>
        <strong>简历优化</strong>
      </RouterLink>
      <h1 class="auth-title">注册</h1>
      <p class="auth-subtitle">创建账号后，后续可以上传简历并查看匹配建议。</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="form.username"
            autocomplete="username"
            placeholder="请输入用户名"
          />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model.trim="form.email" autocomplete="email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            autocomplete="new-password"
            placeholder="请输入密码"
            show-password
            type="password"
          />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model.trim="form.nickname" autocomplete="nickname" placeholder="可选" />
        </el-form-item>

        <div class="auth-actions">
          <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">
            注册
          </el-button>
          <p class="auth-footer">
            已有账号？
            <router-link class="auth-link" to="/login">去登录</router-link>
          </p>
        </div>
      </el-form>
    </section>
  </main>
</template>
