# 星码空间 CodeOrbit AI

当前版本已完成四个阶段，包含：

- `frontend`：Vue 3 + Vite 工作台、登录注册、路由守卫和独立功能页面
- `backend`：Java 22 + Spring Boot REST API、MySQL 持久化和自动建表
- 第一阶段：用户认证、路由、工作空间、项目基础数据和数据库接入
- 第二阶段：项目、任务、计时、签到、统计、代码文件和模型配置持久化
- 第三阶段：项目管理、AI 生成、代码评审、专注空间、成长数据、知识库入口和响应式布局
- 第四阶段：知识库持久化、工作空间成员与角色、通知中心、项目发布记录与回滚

## 启动前端

```powershell
cd D:\毕设\codeorbit-ai\frontend
npm install
npm run dev
```

打开 `http://127.0.0.1:4173/login`。

## 启动后端

在 IntelliJ IDEA 中打开 `backend` 目录，运行 `com.codeorbit.CodeOrbitApplication`。运行配置需要使用 JDK 22，并设置环境变量 `CODEORBIT_DB_PASSWORD=200400`。如果提示 `8080` 端口被占用，说明后端已经启动，直接访问健康检查即可，或先停止旧的 Java 进程。

```powershell
cd D:\毕设\codeorbit-ai\backend
$env:JAVA_HOME='C:\Java\jdk-22'
$env:CODEORBIT_DB_PASSWORD='200400'
mvn spring-boot:run
```

健康检查：`http://127.0.0.1:8080/actuator/health`

## 当前可演示流程

1. 进入动态工作台。
2. 在代码工作台切换 `index.html`、`style.css` 和 `script.js`。
3. 修改代码，右侧实时预览同步更新。
4. 在 AI Copilot 输入需求，点击“生成代码”。
5. 查看项目进度、代码质量、成长热力图和待处理问题。

当前 AI 生成通过后端模型配置接口工作；模型中心支持配置 OpenAI 兼容接口、本地 Ollama、DeepSeek、通义或中转服务。

发布中心记录预览或生产版本、版本历史和回滚状态；当前预览地址指向本地工作台，外部生产托管可在后续接入云端部署服务。
