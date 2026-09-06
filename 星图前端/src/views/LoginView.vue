<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">知识星图 Agent</h2>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="0">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
                登录
              </el-button>
            </el-form-item>
          </el-form>
          <div class="switch-link">
            还没有账号？<a @click="activeTab = 'register'">立即注册</a>
          </div>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="0">
            <el-form-item prop="username">
              <el-input v-model="registerForm.username" placeholder="用户名" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="昵称（可选）" prefix-icon="Avatar" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" style="width: 100%" @click="handleRegister">
                注册
              </el-button>
            </el-form-item>
          </el-form>
          <div class="switch-link">
            已有账号？<a @click="activeTab = 'login'">去登录</a>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const activeTab = ref('login')
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const loginForm = reactive({ username: '', password: '' })
const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerForm = reactive({ username: '', password: '', confirmPassword: '', nickname: '' })
const registerRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少8位', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-zA-Z])(?=.*\d).+$/,
      message: '密码必须包含字母和数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res: any = await userApi.login(loginForm)
    authStore.setToken(res.data)
    try {
      const meRes: any = await userApi.getMe()
      authStore.setUser(meRes.data)
    } catch {
      // getMe 失败不影响登录结果
    }
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    // 错误已由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const { confirmPassword, ...registerData } = registerForm
    const res: any = await userApi.register(registerData)
    authStore.setToken(res.data)
    try {
      const meRes: any = await userApi.getMe()
      authStore.setUser(meRes.data)
    } catch {
      // getMe 失败不影响注册结果，已有 token 可直接使用
    }
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e: any) {
    // 错误已由 request 拦截器统一处理（ElMessage.error）
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 20px;
}
.login-title {
  text-align: center;
  margin-bottom: 24px;
  color: #303133;
}
.switch-link {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin-top: 16px;
}
.switch-link a {
  color: #409eff;
  cursor: pointer;
  text-decoration: none;
}
</style>
