# AI 模拟面试（独立运行版）

从 [mianshiya-next @ c568c84](https://github.com/liyupi/mianshiya-next/commit/c568c84b30a77961a85512e2f7bff63c06820d5a) 抽取的 **AI 模拟面试** 前后端，可单独运行。

## 目录结构

```
ai-interview/          # Spring Boot 后端（端口 8101，context-path=/api）
frontend/              # Next.js 前端（端口 3000）
```

## 前置条件

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8+
- 火山引擎方舟 API Key（DeepSeek 等模型）

## 1. 初始化数据库

执行：

`ai-interview/sql/create_table.sql`

会创建库 `ai_interview`、用户表、模拟面试表，以及默认账号：

- 账号：`admin`
- 密码：`12345678`

## 2. 配置后端

**不要把 API Key 写进会提交的 `application.yml`。**

任选一种方式：

### 方式 A（推荐）：本地私有配置

```bash
cd ai-interview/src/main/resources
copy application-local.yml.example application-local.yml
```

编辑 `application-local.yml` 填入真实 Key（该文件已在 `.gitignore` 中）：

```yaml
ai:
  apiKey: 你的火山方舟APIKey
  model: 你的模型ID或推理接入点ID
```

启动时启用 `local` profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

IDEA 里可在 Run Configuration 的 Active profiles 填 `local`。

### 方式 B：环境变量

```bash
set AI_API_KEY=你的Key
set AI_MODEL=你的模型ID
mvn spring-boot:run
```

仓库里的 `application.yml` 只保留占位：

```yaml
ai:
  apiKey: ${AI_API_KEY:}
  model: ${AI_MODEL:deepseek-v3-250324}
```

## 3. 启动后端

```bash
cd ai-interview
mvn spring-boot:run
```

接口根路径：`http://localhost:8101/api`

## 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开：`http://localhost:3000`

## 使用流程

1. 打开 `/user/login`，使用 `admin / 12345678` 登录（或先注册）
2. 进入「开始面试」多步配置：
   - 目标岗位（岗位/年限/薪资/描述）
   - 个人信息（上传简历自动解析 + 补充信息）
   - 面试设置（时长/难度/面试官）
3. 进入面试间：可开关摄像头、麦克风语音转文字、扬声器朗读、跳过语音、手动输入
4. 结束面试后由后端生成结构化评估报告，可在「我的记录」中查看

### 前端主要页面

| 路径 | 说明 |
|------|------|
| `/interview/setup` | 面试配置向导 |
| `/interview/room/[id]` | 面试对话间 |
| `/interview/records` | 我的面试记录 |
| `/interview/report/[id]` | 评估报告 |

### 主要后端接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/login` | 登录 |
| POST | `/api/user/register` | 注册 |
| POST | `/api/resume/parse` | 上传简历并解析（multipart `file`） |
| POST | `/api/mockInterview/add` | 创建模拟面试（含岗位/个人信息/设置） |
| GET  | `/api/mockInterview/get?id=` | 获取面试详情 |
| POST | `/api/mockInterview/handleEvent` | 处理 start/chat/end 事件 |
| GET  | `/api/mockInterview/report/get?id=` | 获取结构化评估报告 |
| POST | `/api/mockInterview/my/list/page/vo` | 我的面试记录分页 |

报告页支持雷达图能力分析、技术技能矩阵、学习路线图、题目解析，并可 **下载 PDF 报告**。

已有库升级请执行：`ai-interview/sql/alter_mock_interview_v2.sql`

## 说明

- 登录态使用 **HttpSession + Cookie**，前端 `withCredentials: true`
- 已去掉原项目中的 Sa-Token / Redis / ES 等与本功能无关的依赖
- 业务逻辑（Prompt、事件流、消息落库）与原 commit 保持一致
