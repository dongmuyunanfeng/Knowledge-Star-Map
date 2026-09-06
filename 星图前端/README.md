# 星图前端

知识星图 Agent v2.0 的前端工程，基于 Vue 3 + Vite + TypeScript。

## 技术栈

| 技术 | 用途 |
| --- | --- |
| Vue 3（Composition API） | 视图框架 |
| Vite 5 | 构建 / 开发服务器 |
| TypeScript | 类型安全 |
| Vue Router 4 | 路由 |
| Pinia | 状态管理（用户态等） |
| Element Plus | UI 组件库 |
| ECharts 5 | 数据可视化 / 星图 |
| Axios | HTTP 请求（`src/utils/request.ts` 统一封装） |

## 依赖安装

```bash
npm install
```

## 开发启动

```bash
npm run dev
```

默认监听 <http://localhost:3000>。

## 打包

```bash
npm run build      # 输出到 dist/（dist 已被 .gitignore 忽略）
npm run preview    # 本地预览打包产物
```

## 环境配置说明

- **端口**：`vite.config.ts` 中 `server.port = 3000`，可在此修改。
- **接口代理**：开发环境下 `/api` 前缀的请求会被代理到后端：

  ```ts
  server: {
    port: 3000,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
  ```

  后端地址变化时，修改 `target` 即可，无需改动业务代码。
- **路径别名**：`@` 指向 `src/` 目录（在 `vite.config.ts` 与 `tsconfig.app.json` 中均已配置）。
- **前端自身无独立密钥**，所有敏感信息（数据库、LLM Key）均在后端配置，前端仅通过代理调用 `/api`。

## 目录结构

```
星图前端/
├── public/
│   └── galaxy-bg.png        # 深空换肤背景图
├── src/
│   ├── api/                 # 接口定义
│   ├── router/              # 路由配置
│   ├── stores/              # Pinia 状态
│   ├── styles/              # 全局样式
│   ├── types/               # TypeScript 类型
│   ├── utils/               # request / sse 等工具
│   └── views/               # 页面视图（登录/星图/文件/对话/简历等）
├── index.html
├── vite.config.ts
└── package.json
```

## 主要页面

| 视图 | 说明 |
| --- | --- |
| `LoginView` | 登录 / 注册 |
| `StarMapView` | 知识星图（深空可视化） |
| `FileManageView` | 文件上传与管理 |
| `KnowledgeSearchView` | 知识点检索 |
| `AgentChatView` | Agent 对话 |
| `StudyPlanView` | 学习规划 |
| `ProjectExperienceView` | 项目经历 |
| `ResumeView` | 简历润色 |
