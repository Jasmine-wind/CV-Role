<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthShell from '@/components/auth/AuthShell.vue'

interface LoginForm {
  account: string
  password: string
}

const formRef = ref<FormInstance>()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive<LoginForm>({
  account: '',
  password: '',
})

const rules: FormRules<LoginForm> = {
  account: [
    { required: true, message: '请输入用户名或邮箱', trigger: 'blur' },
    { min: 3, max: 100, message: '长度应在 3 到 100 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 72, message: '长度应在 6 到 72 个字符之间', trigger: 'blur' },
  ],
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()

  if (!valid) {
    return
  }

  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/app'
    await router.push(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  }
}
</script>

<template>
  <AuthShell>
    <h1 class="auth-title">登录</h1>
    <p class="auth-subtitle">使用用户名或邮箱登录，继续为目标岗位准备简历。</p>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名或邮箱" prop="account">
          <el-input
            v-model.trim="form.account"
            autocomplete="username"
            placeholder="请输入用户名或邮箱"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            type="password"
          />
        </el-form-item>

        <div class="auth-actions">
          <el-button type="primary" size="large" :loading="authStore.loading" @click="handleSubmit">
            登录
          </el-button>
          <p class="auth-footer">
            还没有账号？
            <router-link class="auth-link" to="/register">去注册</router-link>
          </p>
        </div>
    </el-form>
  </AuthShell>
</template>
