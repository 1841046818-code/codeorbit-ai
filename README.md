# 星码空间 CodeOrbit AI

第一阶段工程骨架，包含：

- `frontend`：Vue 3 + Vite 动态工作台原型
- `backend`：Java 22 + Spring Boot 健康检查服务

## 启动前端

```powershell
cd D:\毕设\codeorbit-ai\frontend
npm install
npm run dev
```

打开 `http://127.0.0.1:5173/`。

## 启动后端

```powershell
cd D:\毕设\codeorbit-ai\backend
mvn spring-boot:run
```

健康检查：`http://127.0.0.1:8080/api/system/health`

## 当前可演示流程

1. 进入动态工作台。
2. 在代码工作台切换 `index.html`、`style.css` 和 `script.js`。
3. 修改代码，右侧实时预览同步更新。
4. 在 AI Copilot 输入需求，点击“生成代码”。
5. 查看项目进度、代码质量、成长热力图和待处理问题。

当前 AI 生成使用前端模拟流程，下一阶段通过 Java 后端接入 Ollama、DeepSeek 或通义千问。
