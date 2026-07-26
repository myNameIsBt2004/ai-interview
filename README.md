# AI 模拟面试

基于大模型的 **AI 模拟面试** 前后端独立项目：按岗位与简历定制面试官，支持语音问答，结束后生成结构化评估报告。

---

## 功能概览

| 模块               | 说明                                                         |
| ------------------ | ------------------------------------------------------------ |
| 多步面试配置       | 目标岗位 → 个人信息 / 简历 → 面试设置（时长、难度、面试官）  |
| 简历解析           | 上传 PDF 等简历，自动提取文本并辅助填写个人信息              |
| AI 对话面试        | 事件驱动（开始 / 作答 / 结束），消息持久化；开场必请自我介绍 |
| 知识点 RAG（可选） | DashScope Embedding + DashVector：按岗位/技能召回考点，注入面试 System Prompt 引导出题 |
| 语音输入 (ASR)     | 浏览器采集麦克风 → 16kHz WAV → 火山录音文件识别；支持按静音自动切句 |
| 语音播报 (TTS)     | 默认浏览器 `speechSynthesis`，可选火山引擎音色               |
| 评估报告           | 综合得分、能力雷达、技能矩阵、学习路线、题目解析；支持下载 PDF |
| 面试记录           | 分页查看历史模拟面试                                         |

---

## 技术栈
**后端** `ai-interview/`

- Java 17 · Spring Boot 3.3 · MyBatis-Plus · MySQL 8
- 火山方舟（Ark）大模型 · PDFBox（简历文本）
- 可选：火山语音 TTS / ASR
- 可选：阿里云 DashScope Embedding + DashVector（知识点向量库）

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
    ├── 用户 / 模拟面试 / 简历 / 知识点
    ├── 火山方舟 LLM ──► 追问 & 评估报告
    ├── DashScope Embedding + DashVector（可选）──► 考点召回 → 注入 Prompt
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
│   ├── src/main/resources/
│   │   └── knowledge/
│   │       └── seed-sample.json  # 内置样例知识点
│   └── src/main/java/com/aiinterview/
│       ├── controller/           # 用户 / 面试 / 简历 / 知识点 / ASR / TTS
│       ├── service/              # 业务逻辑（含 KnowledgePointService）
│       ├── manager/              # AI / Embedding / DashVector HTTP / 火山语音
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
- （可选）[阿里云 DashScope](https://dashscope.console.aliyun.com/) API Key（Embedding）+ [DashVector](https://dashvector.console.aliyun.com/) Cluster（向量库）

---

## 快速开始

### 1. 初始化数据库

只需执行一份脚本：

`ai-interview/sql/create_table.sql`

会创建库 `ai_interview`、`user` / `mock_interview` 表，并写入默认账号。

| 账号 | 密码 |
|------|------|
| `admin` | `12345678` |


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

数据库默认连接见 `application.yml`（`localhost:3306`，库名 `ai_interview`，用户 `root` / `123456`），请按本地环境修改。

---

## 说明与限制

- 麦克风能力需 **HTTPS** 或 `localhost`
- `ScriptProcessorNode` 用于录音处理，兼容性好；后续可迁移至 AudioWorklet
- 报告页含能力雷达、技能矩阵、学习路线、题目解析，并可下载 PDF

---

## 界面展示
<img width="983" height="790" alt="image-20260724160312224" src="https://github.com/user-attachments/assets/a67b1a2f-70b5-4985-a591-17f57f2a9d3a" />
<img width="991" height="797" alt="63fb8fbfa007acf37cd0e6dfe0d5d064" src="https://github.com/user-attachments/assets/ffb84a92-8316-4ce1-8a54-c9d04d4f4aab" />
<img width="1105" height="641" alt="e459122363340bae2ff22ef29f0ffa3d" src="https://github.com/user-attachments/assets/617c5897-d808-4ffe-ace7-350bf9ac93ce" />
<img width="1870" height="832" alt="3b6d18679037cd77a36de0fb107360c8" src="https://github.com/user-attachments/assets/3b67c10c-e24f-4be5-a0d4-d8249cd72a56" />
<img width="1191" height="732" alt="af2cc4e2027f37576623c6dd5854d496" src="https://github.com/user-attachments/assets/04ec7c43-f58d-4100-8570-d482d066fa5b" />



## 面试报告展示
<img width="1032" height="817" alt="image-20260724160445420" src="https://github.com/user-attachments/assets/09b1be85-1c10-4d30-bf17-547a85d488bf" />
<img width="1047" height="793" alt="image-20260724160531619" src="https://github.com/user-attachments/assets/08b956f5-3f9a-40f1-ab54-a4e06d04bbb2" />
<img width="965" height="808" alt="image-20260724160549115" src="https://github.com/user-attachments/assets/76821a50-8006-4b0f-a837-095fefc1b276" />
<img width="947" height="787" alt="image-20260724160559808" src="https://github.com/user-attachments/assets/1b1b918a-8929-44a6-a1bf-deae68e7e9d5" />




