# 知识星图 Agent v2.0

知识引导型智能 Agent 系统：将用户的文件与知识自动解析为「知识点」，构建个人知识星图，并提供 Agent 对话、学习规划、项目经历生成与简历润色等能力。

## 一、项目简介

- 用户上传 PDF / Markdown / Word / 图片等文件，后端解析并抽取知识点
- 知识点按「领域（domain）」聚类，前端以深空星图形式可视化呈现
- 内置 Agent 智能体：对话问答、学习规划、知识补全建议、简历生成
- 前后端分离，REST API 通信，AI 能力接入 OpenAI 兼容接口

## 二、项目架构

```
┌──────────────┐   /api (Vite 代理)   ┌──────────────────────┐
│   星图前端     │ ──────────────────> │       星图后端         │
│  Vue3 + Vite  │   http://localhost  │  Spring Boot 3.2      │
│  :3000        │        :8080        │  MyBatis-Plus         │
└──────────────┘                     │  JWT + Spring Security│
                                     └──────────┬───────────┘
                                                │
                          ┌─────────────────────┼──────────────────┐
                          │ MySQL 8.0           │ Redis            │
                          │ (knowledge_star_map)│ (缓存/星图锁)      │
                          └─────────────────────┴──────────────────┘
```

| 模块 | 技术栈 |
| --- | --- |
| 前端 | Vue 3、Vite 5、TypeScript、Vue Router、Pinia、Element Plus、ECharts、Axios |
| 后端 | Spring Boot 3.2.5、Java 17、MyBatis-Plus、Flyway、Spring Security、JWT、PDFBox、POI |
| 存储 | MySQL 8.0（业务数据）、Redis（缓存 / 分布式锁） |
| AI | OpenAI 兼容接口（deepseek / glm 等模型，通过环境变量配置） |

## 三、环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 后端编译运行 |
| Maven | 3.6+ | 后端构建 |
| Node.js | 18+ | 前端 Vite 5 要求 |
| MySQL | 8.0+ | 业务数据库 |
| Redis | 5+ | 缓存，本地 6379 端口 |

## 四、快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/dongmuyunanfeng/Knowledge-Star-Map.git
cd 知识星图-设计2.0
```

### 2. 初始化数据库（MySQL）

先创建数据库，再按序导入建表脚本（脚本位于 `星图后端/src/main/resources/db/migration/`，`V1__` ~ `V20__`）。

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS knowledge_star_map DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 按版本号顺序导入全部建表脚本（Git Bash / bash 环境）
for f in 星图后端/src/main/resources/db/migration/V*.sql; do
  mysql -u root -p knowledge_star_map < "$f"
done
```

> 提示：默认数据库连接为 `root/root`，可用环境变量覆盖，见后端 README。

### 3. 配置环境变量（数据库 + 你自己的 AI API Key）

后端敏感配置通过环境变量注入。Windows 用 `set`、Linux/Mac 用 `export`：

```bash
# 数据库（若非默认 root/root）
export DB_USER=root DB_PASSWORD=你的密码

# 三组 LLM 模型密钥，分别对应不同能力（可填同一个 OpenAI 兼容平台的 key）
export API_KEY_1=你的key   # 模型1：文件解析 → 生成知识点
export API_KEY_2=你的key   # 模型2：Agent 对话 / 学习规划 / 简历生成
export API_KEY_3=你的key   # 模型3：图片 OCR 文字识别

# 可选：换成你自己的 OpenAI 兼容接口地址（默认指向第三方中转）
export LLM_API_URL=https://你的中转地址/v1/chat/completions
export CHAT_LLM_API_URL=https://你的中转地址/v1/chat/completions
export OCR_LLM_API_URL=https://你的中转地址/v1/chat/completions
```

> 不配置 Key 时仅 AI 相关接口不可用，注册/登录/文件/星图等基础功能正常。

### 4. 启动 Redis

```bash
redis-server
```

### 5. 启动后端（:8080）

```bash
cd 星图后端
mvn spring-boot:run
```

### 6. 启动前端（:3000）

```bash
cd 星图前端
npm install
npm run dev
```

浏览器访问 <http://localhost:3000>。

## 五、项目目录说明

```
知识星图-设计2.0/
├── .gitignore                 # 版本控制忽略规则
├── README.md                  # 本文件
├── 星图前端/                   # Vue3 前端工程
│   ├── README.md
│   ├── package.json
│   ├── vite.config.ts         # 端口 3000 + /api 代理到 8080
│   ├── public/                # 静态资源（galaxy-bg.png 深空背景）
│   └── src/                   # 源码（api/router/stores/views/...）
└── 星图后端/                   # Spring Boot 后端工程
    ├── README.md
    ├── pom.xml
    └── src/
        ├── main/java/com/knowledgestarmap/   # controller/service/...
        └── main/resources/
            ├── application.yml               # 主配置（密钥用环境变量占位）
            ├── application-prod.yml
            └── db/migration/                 # V1~V20 建表脚本
```

## 六、注意事项

1. **数据库脚本在仓库内**：`星图后端/src/main/resources/db/migration/*.sql` 已纳入版本控制，克隆后执行导入命令即可建表；本地数据文件（`*.sql` 备份、`dump.rdb`、`*.db` 等）已被 `.gitignore` 忽略，不会提交。
2. **密钥不入库**：数据库密码、JWT 密钥、LLM API Key 均通过环境变量注入（`application.yml` 使用 `${VAR:default}` 占位），真实密钥请勿写入提交的配置文件。
3. **测试代码不入库**：`星图后端/src/testP2~P9`、`src/test` 等测试目录已忽略；如需恢复测试，从本地历史单独管理。
4. **AI 功能依赖密钥**：文件解析、Agent 对话、OCR、简历生成等能力需要配置 `API_KEY_1/2/3` 等环境变量才能正常调用，缺失时仅影响 AI 相关接口。

更多细节见 [前端 README](星图前端/README.md) 与 [后端 README](星图后端/README.md)。
