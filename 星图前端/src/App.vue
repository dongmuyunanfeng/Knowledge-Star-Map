<template>
  <el-container class="app-layout">
    <el-header v-if="showSidebar" height="60px" class="app-header">
      <div class="header-left">
        <router-link to="/" class="logo">知识星图 Agent</router-link>
      </div>
      <div class="header-right">
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            <span>{{ authStore.user?.nickname || authStore.user?.username }}</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container>
      <el-aside v-if="showSidebar" width="200px" class="app-aside">
        <el-menu :default-active="activeRoute" router class="side-menu">
          <el-menu-item index="/">
            <el-icon><DataAnalysis /></el-icon>
            <span>知识星图</span>
          </el-menu-item>
          <el-menu-item index="/projects">
            <el-icon><FolderOpened /></el-icon>
            <span>项目经历</span>
          </el-menu-item>
          <el-menu-item index="/files">
            <el-icon><Document /></el-icon>
            <span>文件管理</span>
          </el-menu-item>
          <el-menu-item index="/search">
            <el-icon><Search /></el-icon>
            <span>知识检索</span>
          </el-menu-item>
          <el-menu-item index="/agent">
            <el-icon><ChatDotRound /></el-icon>
            <span>Agent对话</span>
          </el-menu-item>
          <el-menu-item index="/study-plan">
            <el-icon><List /></el-icon>
            <span>学习规划</span>
          </el-menu-item>
          <el-menu-item index="/resume">
            <el-icon><Reading /></el-icon>
            <span>简历生成</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  DataAnalysis, Document, Search, ChatDotRound, List, Reading, ArrowDown, User, FolderOpened
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const showSidebar = computed(() => route.path !== '/login')
const activeRoute = computed(() => route.path)

const handleCommand = (command: string) => {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.app-layout {
  height: 100vh;
}
.app-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.logo {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
  text-decoration: none;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #606266;
}
.app-aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}
.side-menu {
  border-right: none;
}
.app-main {
  background: #f5f7fa;
  padding: 12px;
}
</style>
