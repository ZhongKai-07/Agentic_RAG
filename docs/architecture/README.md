# Architecture Documentation

Agentic RAG 系统架构文档。所有图表使用 Mermaid 语法，可在 GitHub / VS Code / IntelliJ 中直接渲染。

## 目录

| # | 文档 | 内容 |
|:--|:-----|:-----|
| 01 | [Project Structure](01-project-structure.md) | 完整目录树，每个文件的职责说明 |
| 02 | [Hexagonal Architecture](02-hexagonal-architecture.md) | 六边形分层全景图 + 依赖规则 |
| 03 | [Module Dependency](03-module-dependency.md) | Maven 模块依赖关系图 + 依赖矩阵 |
| 04 | [Bounded Contexts](04-bounded-contexts.md) | 四大限界上下文、聚合根、领域事件流、状态机 |
| 05 | [Data Flow](05-data-flow.md) | 聊天全链路时序图、文档处理流水线、RRF 混合检索、权限模型 |
| 06 | [SPI Adapter Mapping](06-spi-adapter-mapping.md) | Port → Adapter 映射、配置入口、环境切换 |
| 07 | [Frontend Architecture](07-frontend-architecture.md) | React 组件架构、SSE 通信、Vite 代理 |

## 架构一句话概括

> **DDD 六边形 + CQRS + 事件驱动** 的企业 RAG 系统：
> 领域核心零框架依赖，通过 Port 接口隔离外部服务，@Profile 一键切换本地/云端适配器。
> Agent ReAct 循环（Plan → Act → Evaluate → Generate）实现多轮智能检索，
> SSE 流式推送实时 token + 引用到 React 前端。
