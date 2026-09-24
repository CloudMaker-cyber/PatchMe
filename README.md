# 人生补丁包（PatchMe）V3

面向大学生的公开互助社区：发布困扰 → 待回答流被看见 → 获得回复 → 楼主标记有帮助或关闭评论。

**核心产品原则：匿名内容不可被普通用户关联到真实账号或公开主页，该规则由后端 VO 与 SQL 强制保证，而非前端隐藏。**

唯一有效规范见 `docs/v3/`（V2 及旧 Next.js/Supabase 实现已废弃，不在本工程内）。

## 技术栈

| 层 | 选型 |
|---|---|
| 用户端前端 | Vue 3 + TypeScript + Vite + Vue Router + Pinia（自定义 CSS/SCSS） |
| 管理后台 | Vue 3 + Element Plus（后续任务） |
| 后端 | Java 21 + Spring Boot 3.5.x + Spring Security（任务 2）+ JWT |
| 数据访问 | MyBatis-Plus + Flyway |
| 数据库 | MySQL 8 |
| 部署 | Docker Compose 起步，后期迁移自有服务器 |

前后端分离的单体架构：`Vue 前端 → REST/JSON → Spring Boot → MySQL`。

## 目录结构

```text
backend/    Spring Boot 单体后端（com.patchme）
  src/main/java/com/patchme/common/   统一响应、全局异常、CORS 配置
  src/main/resources/db/migration/    Flyway 迁移脚本（V1 为占位基线）
frontend/   Vue 3 用户端
  src/api/        Axios 封装
  src/views/      页面（任务 0 为占位页）
  src/router/     路由
docker-compose.yml   仅本地开发 MySQL
.env.example         环境变量模板（复制为 .env 并填本地随机值，勿提交）
```

## 本地启动

前置：JDK 21、Maven、Node.js LTS、Docker Desktop（见 `docs/v3/04`）。

```bash
# 1. 环境变量
cp .env.example .env        # 把密码换成你本机随机值

# 2. 开发用 MySQL
docker compose up -d

# 3. 后端（http://localhost:8080）
cd backend && mvn spring-boot:run

# 4. 前端（http://localhost:5173，/api 已代理到 8080）
cd frontend && npm install && npm run dev
```

## 验收命令

```bash
cd backend  && mvn test && mvn package   # 后端测试与构建
cd frontend && npm run lint && npm run build   # 前端检查与构建
```

## 开发方式

严格按 `docs/v3/03-开发循环与任务卡.md` 一次只执行一张任务卡；
每轮交付后由开发者运行验收、双账号手工测试并亲自提交 Git。

## 当前进度

- [x] 任务 0：工程基线（本骨架 + 统一响应/异常框架 + 健康检查 `/api/health`）
- [ ] 任务 1：静态页面原型
- [ ] 任务 2：数据库与账号基础（Spring Security/JWT、Flyway 建表）
- [ ] 任务 3–6：见任务卡
