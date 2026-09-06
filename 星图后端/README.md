# 星图后端

知识星图 Agent v2.0 的后端工程，基于 Spring Boot 3.2 + Maven。

## 技术栈

| 技术 | 用途 |
| --- | --- |
| Spring Boot 3.2.5 | 应用框架 |
| Java 17 | 运行环境 |
| MyBatis-Plus | ORM / 数据访问 |
| Flyway | 数据库迁移（当前 `enabled: false`，采用手动导入） |
| Spring Security + JWT | 认证授权 |
| Redis（Lettuce） | 缓存 / 星图计算锁 |
| PDFBox / POI | PDF、Office 文件文本提取 |
| Hutool / FastJSON2 | 工具库 |

## 数据库配置说明

数据库连接通过 `application.yml` 中的环境变量占位，可灵活覆盖：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | MySQL 地址 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_USER` | `root` | 数据库账号 |
| `DB_PASSWORD` | `root` | 数据库密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | （空） | Redis 密码 |
| `JWT_SECRET` | 内置默认值 | JWT 签名密钥（生产务必覆盖） |
| `FILE_UPLOAD_DIR` | `D:/knowledge-star-map/uploads` | 文件上传目录 |
| `API_KEY_1/2/3` | （空） | 三类 LLM 模型密钥 |

覆盖方式示例：

```bash
export DB_USER=root DB_PASSWORD=你的数据库密码
```

> `application-dev.yml` 为本地环境配置（含真实账号），已被 `.gitignore` 忽略，不入库。

## AI API Key 配置（使用 AI 功能前必配）

AI 能力按用途拆成三组密钥，全部通过环境变量注入（Windows 用 `set`、Linux/Mac 用 `export`）：

| 环境变量 | 对应模型 | 用途 |
| --- | --- | --- |
| `API_KEY_1` | `llm-api-key` | 模型1：文件解析 → 生成知识点 |
| `API_KEY_2` | `chat-llm-api-key` | 模型2：Agent 对话 / 学习规划 / 简历生成 |
| `API_KEY_3` | `ocr-llm-api-key` | 模型3：图片 OCR 文字识别 |

```bash
# 三组 key 可填同一个 OpenAI 兼容平台的密钥，也可分别配置
export API_KEY_1=你的key
export API_KEY_2=你的key
export API_KEY_3=你的key

# 默认接口地址是第三方中转，可换成你自己的 OpenAI 兼容地址（三组可分别指向不同服务）
export LLM_API_URL=https://你的中转地址/v1/chat/completions
export CHAT_LLM_API_URL=https://你的中转地址/v1/chat/completions
export OCR_LLM_API_URL=https://你的中转地址/v1/chat/completions

# 如需更换模型，可在 application.yml 的 app.agent.* 下调整
# llm-model / chat-llm-model / ocr-llm-model
```

> 未配置 Key 时，仅文件解析、Agent 对话、OCR、学习规划、简历生成等 AI 接口不可用，其余接口与项目启动不受影响。

## SQL 脚本导入方式

建表脚本位于 `src/main/resources/db/migration/`，按版本号 `V1__` → `V20__` 顺序执行：

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS knowledge_star_map DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 按序导入全部脚本（Git Bash / bash）
for f in src/main/resources/db/migration/V*.sql; do
  mysql -u root -p knowledge_star_map < "$f"
done
```

> 也可改为自动建表：将 `application.yml` 中 `spring.flyway.enabled` 改为 `true`，后端启动时自动按序执行 migration。

## 启动命令

```bash
cd 星图后端

# 开发环境（需先启动 MySQL 与 Redis）
mvn spring-boot:run

# 打包
mvn clean package -DskipTests

# 运行打包产物
java -jar target/knowledge-star-map-agent-2.0.0-SNAPSHOT.jar
```

后端默认监听 <http://localhost:8080>。

## 接口说明

所有接口统一以 `/api` 为前缀，按 Controller 划分：

| Controller | 基础路径 | 说明 |
| --- | --- | --- |
| `AuthController` | `/api/auth` | 注册 `/register`、登录 `/login`、当前用户 `/me` |
| `UserController` | `/api/user` | 用户信息、学习方向配置 |
| `DomainController` | `/api/domains` | 知识领域 |
| `FileResourceController` | `/api/files` | 文件上传 `/upload`、解析 `/parse`、下载 `/download` |
| `KnowledgeController` | `/api/knowledge` | 知识点 |
| `KnowledgeSearchController` | `/api/search` | 知识点检索 |
| `KnowledgeStarMapController` | `/api/star-map` | 知识星图（含重算 `/recalculate`） |
| `KnowledgeSuggestionController` | `/api/knowledge/suggestions` | 知识补全建议 |
| `ProjectInfoController` | `/api/projects` | 项目信息 |
| `ProjectExperienceController` | `/api/projects` | 项目经历 `/experiences` |
| `ResumeMaterialController` | `/api/resume` | 简历素材 `/templates`、生成 `/generate` |
| `SearchHistoryController` | `/api/search/history` | 搜索历史 |
| `StudyPlanController` | `/api/study-plans` | 学习规划 |
| `AgentSessionController` | `/api/agent/sessions` | Agent 对话（含流式 `/stream`） |

- **认证**：除 `/api/auth/**` 外，其余接口需在请求头携带 `Authorization: Bearer <token>`（登录后获取）。
- **流式接口**：Agent 对话等支持 SSE 流式返回，前端通过 `src/utils/sse.ts` 消费。

## 目录结构

```
星图后端/
├── pom.xml
└── src/
    ├── main/java/com/knowledgestarmap/
    │   ├── KnowledgeStarMapApplication.java   # 启动类
    │   ├── controller/                        # 接口层
    │   ├── service/                           # 业务层
    │   ├── config/                            # 配置（Security/Cors/...）
    │   ├── security/                          # JWT / 安全
    │   └── ...                                # mapper、entity、dto 等
    └── main/resources/
        ├── application.yml                    # 主配置
        ├── application-prod.yml
        └── db/migration/                      # V1~V20 建表脚本
```
