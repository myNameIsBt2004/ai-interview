# AI 模拟面试

基于大模型的 **AI 模拟面试** 前后端独立项目：按岗位与简历定制面试官，支持语音问答，结束后生成结构化评估报告。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| 多步面试配置 | 目标岗位 → 个人信息 / 简历 → 面试设置（时长、难度、面试官） |
| 简历解析 | 上传 PDF 等简历，自动提取文本并辅助填写个人信息 |
| AI 对话面试 | 事件驱动（开始 / 作答 / 结束），消息持久化 |
| 语音输入 (ASR) | 浏览器采集麦克风 → 16kHz WAV → 火山录音文件识别；支持按静音自动切句 |
| 语音播报 (TTS) | 默认浏览器 `speechSynthesis`，可选火山引擎音色 |
| 评估报告 | 综合得分、能力雷达、技能矩阵、学习路线、题目解析；支持下载 PDF |
| 面试记录 | 分页查看历史模拟面试 |

---

## 技术栈

**后端** `ai-interview/`

- Java 17 · Spring Boot 3.3 · MyBatis-Plus · MySQL 8
- 火山方舟（Ark）大模型 · PDFBox（简历文本）
- 可选：火山语音 TTS / ASR

**前端** `frontend/`

- Next.js 14 · React 18 · TypeScript · Ant Design 5
- Axios（Cookie Session）· html2canvas / jspdf（报告导出）

---

## 架构示意

```
浏览器 (Next.js :3000)
    │  Cookie Session
    ▼
Spring Boot API (:8101/api)
    ├── 用户 / 模拟面试 / 简历
    ├── 火山方舟 LLM ──► 追问 & 评估报告
    ├── 火山 ASR（可选）──► 语音转文字
    └── 火山 TTS（可选）──► 面试官朗读
    ▼
MySQL (ai_interview)
```

---

## 目录结构

```
├── ai-interview/                 # Spring Boot 后端
│   ├── sql/
│   │   └── create_table.sql      # 唯一初始化脚本：建库建表 + 默认账号
│   └── src/main/java/com/aiinterview/
│       ├── controller/           # 用户 / 面试 / 简历 / ASR / TTS
│       ├── service/              # 业务逻辑
│       ├── manager/              # AI / 火山语音封装
│       └── ...
├── frontend/                     # Next.js 前端
│   └── src/
│       ├── app/interview/        # setup / room / records / report
│       └── libs/                 # request、wavRecorder、speech 等
└── README.md
```

---

## 前置条件

- JDK **17+**
- Maven **3.8+**
- Node.js **18+**
- MySQL **8+**
- [火山引擎方舟](https://console.volcengine.com/ark) API Key（对话模型）
- （可选）[火山语音](https://console.volcengine.com/speech) AppID / Token（ASR、云端 TTS）

---

## 快速开始

### 1. 初始化数据库

只需执行一份脚本：

`ai-interview/sql/create_table.sql`

会创建库 `ai_interview`、`user` / `mock_interview` 表，并写入默认账号。

| 账号 | 密码 |
|------|------|
| `admin` | `12345678` |

### 2. 配置后端密钥

#### 方式 A（推荐）：本地私有配置

```bash
cd ai-interview/src/main/resources
# Windows
copy application-local.yml.example application-local.yml
# macOS / Linux
cp application-local.yml.example application-local.yml
```

编辑 `application-local.yml`（已在 `.gitignore` 中）：

```yaml
ai:
  apiKey: 你的火山方舟APIKey
  model: 你的模型ID或推理接入点ID
  tts:
    provider: browser   # browser | volc
    # provider=volc 时填写语音控制台凭证（可同时给 ASR 用）
    appId: ""
    accessToken: ""
  asr:
    enabled: true
```

启动时启用 `local` profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

IDEA：Run Configuration → Active profiles 填 `local`。

#### 方式 B：环境变量

```bash
# Windows CMD 示例
set AI_API_KEY=你的Key
set AI_MODEL=你的模型ID
mvn spring-boot:run
```

### 3. 启动后端

```bash
cd ai-interview
mvn spring-boot:run
```

接口根路径：`http://localhost:8101/api`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开：`http://localhost:3000`（首页会跳转到面试配置页）。

---

## 使用流程

1. 打开 `/user/login`，使用 `admin / 12345678` 登录（或注册）
2. **开始面试** 三步配置：目标岗位 → 个人信息（可上传简历）→ 面试设置
3. 进入 **面试间**：摄像头 / 麦克风 ASR / 扬声器 TTS / 跳过语音 / 手动输入
4. 结束面试后生成评估报告，可在 **我的记录** 中再次查看并下载 PDF

### 前端页面

| 路径 | 说明 |
|------|------|
| `/user/login` | 登录 |
| `/interview/setup` | 面试配置向导 |
| `/interview/room/[id]` | 面试对话间 |
| `/interview/records` | 我的面试记录 |
| `/interview/report/[id]` | 评估报告 |

---

## 主要 API

基础路径：`http://localhost:8101/api`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/user/register` | 注册 |
| POST | `/user/login` | 登录 |
| GET  | `/user/get/login` | 当前登录用户 |
| POST | `/user/logout` | 退出 |
| POST | `/resume/parse` | 上传简历并解析（multipart `file`） |
| POST | `/mockInterview/add` | 创建模拟面试 |
| GET  | `/mockInterview/get?id=` | 面试详情 |
| POST | `/mockInterview/handleEvent` | 处理 `start` / `chat` / `end` |
| GET  | `/mockInterview/report/get?id=` | 结构化评估报告 |
| POST | `/mockInterview/my/list/page/vo` | 我的面试记录分页 |
| GET  | `/asr/config` | ASR 是否可用等配置 |
| POST | `/asr/recognize` | 上传音频识别为文字 |
| GET  | `/tts/config` | TTS 提供方配置 |
| POST | `/tts/synthesize` | 云端合成语音（`provider=volc`） |

登录态：**HttpSession + Cookie**，前端请求需 `withCredentials: true`。

---

## 语音相关说明

### ASR（语音转文字）

- 前端 `WavRecorder` 采集麦克风，导出 **16kHz mono WAV**
- 支持 **持续录音**：按静音自动切句，边说边识别
- 后端调用火山「录音文件识别」；需在语音控制台为应用开通对应能力
- `appId` / `accessToken` 可复用 TTS 配置，也可单独用 `AI_ASR_*` 环境变量

### TTS（文字转语音）

| `ai.tts.provider` | 行为 |
|-------------------|------|
| `browser`（默认） | 浏览器自带 `speechSynthesis`，无需语音密钥 |
| `volc` | 调用火山 TTS，使用配置的 `voiceType` 等 |

---

## 配置项摘要

| 变量 / 配置 | 说明 |
|-------------|------|
| `AI_API_KEY` / `ai.apiKey` | 方舟大模型 Key（必填） |
| `AI_MODEL` / `ai.model` | 模型或推理接入点 ID |
| `AI_TTS_PROVIDER` | `browser` 或 `volc` |
| `AI_TTS_APP_ID` / `AI_TTS_ACCESS_TOKEN` | 火山语音凭证 |
| `AI_ASR_ENABLED` | 是否启用 ASR |
| `AI_ASR_APP_ID` 等 | 可覆盖 ASR 凭证；默认复用 TTS |

数据库默认连接请按本地环境修改。

---

## 说明与限制
- 麦克风能力需 **HTTPS** 或 `localhost`
- `ScriptProcessorNode` 用于录音处理，兼容性好；后续可迁移至 AudioWorklet
- 报告页含能力雷达、技能矩阵、学习路线、题目解析，并可下载 PDF

---

## 界面展示

