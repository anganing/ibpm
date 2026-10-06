# IBPM 前后端一体项目

本仓库将 IBPM 的后端、前端和项目文档放在同一目录下，便于本地开发、联调和部署。项目基于 ContiNew Admin，并集成 Warm-Flow 流程能力。

## 目录结构

| 目录 | 内容 |
| --- | --- |
| `server/` | Java 17、Spring Boot、Maven 多模块后端 |
| `web/` | Vue 3、TypeScript、Vite 前端 |
| `docs/` | 项目说明、审批流程设计和集成文档 |
| `nginx-1.30.5/` | Windows Nginx 文件及本地联调配置 |

后端和前端各自的详细开发约定见 [`server/AGENTS.md`](server/AGENTS.md) 与 [`web/AGENTS.md`](web/AGENTS.md)，产品说明见 [`server/README.md`](server/README.md) 和 [`web/README.md`](web/README.md)。

## 环境要求

- Java 17
- Node.js 22
- pnpm 9
- MySQL 与 Redis

后端通过仓库内的 Maven Wrapper 构建，无需另行安装 Maven。首次运行前，请按本地环境修改 `server/continew-server/src/main/resources/config/` 下的配置，并准备好 MySQL、Redis 等依赖服务。请勿将个人凭据提交到仓库。

## 本地开发

先在一个终端启动后端：

```powershell
cd server
.\mvnw.cmd -pl continew-server -am spring-boot:run
```

再在另一个终端启动前端：

```powershell
cd web
pnpm install
pnpm dev
```

前端开发环境通过 `/dev-api` 将接口请求转发到本地后端（默认地址为 `http://localhost:8000`）。如果服务地址或端口有变化，请同步调整前端环境配置及后端配置。

## 本地生产构建联调

前端生产环境使用 `/api` 作为接口前缀。仓库中的 Nginx 配置监听 `18081`，提供 `web/dist` 静态文件，并将 `/api/` 请求转发到 `127.0.0.1:8000`。执行前需要先构建前端：

```powershell
cd web
pnpm build
```

该 Nginx 配置包含本机 Windows 路径，换到其他环境时需按实际部署目录调整。生产部署时，请相应配置数据库、Redis、服务地址和凭据，不要沿用本地示例值。

## 项目文档

- [审批流项目介绍](docs/微信公众号-IBPM审批流介绍.md)
- [BPM 开发任务清单](docs/bpm-development-tasks.md)
- [流程图任务清单](docs/bpm-flow-chart-tasks.md)
- [Warm-Flow 集成审核记录](docs/warm-flow-integration-audit-2026-10-06.md)
- [流程任务操作设计](docs/bpm-task-actions-design.md)
- [转办、委派、撤回与终止设计](docs/bpm-transfer-delegate-revoke-terminate-design.md)
- [SaaS 集成说明](docs/bpm-saas-integration.md)
