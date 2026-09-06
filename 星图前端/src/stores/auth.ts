import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<UserVO | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('token', t)
  }

  function setUser(u: UserVO) {
    user.value = u
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('agent_current_session_id')
  }

  return { token, user, isLoggedIn, setToken, setUser, logout }
})
