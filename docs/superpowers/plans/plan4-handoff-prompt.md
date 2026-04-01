# Plan 4 接力 Prompt

> 复制以下内容到新会话中执行。

---

请执行 `Agentic_RAG/docs/superpowers/plans/2026-04-01-plan4-conversation-agent-engine.md` 中定义的 Plan 4（Conversation & Agent Engine）开发计划。

## 背景

这是一个 Java 21 + Spring Boot 3.4.5 + Spring AI 1.0.0 的企业级 RAG 知识库项目，采用 DDD 六边形架构 + 事件驱动。项目已完成 Plan 1-3（基础设施、身份与文档管理、文档处理流水线），现在需要实现 Plan 4：对话与 Agent 引擎。

## 关键约束

1. **`rag-domain` 层零框架依赖** — 只允许纯 Java + `reactor-core`（Flux/Mono），禁止 Spring、JPA 注解
2. **严格遵循已有代码风格** — 阅读现有的 `Document.java`、`DocumentMapper.java`、`DocumentRepositoryAdapter.java`、`AliCloudLlmAdapter.java` 等文件，匹配 getter/setter 风格、命名规范、包结构
3. **SPI 可插拔** — 适配器用 `@Profile("local")` 注解，保持环境切换零代码修改
4. **不要额外添加** Plan 文档中未定义的文件、功能、或"改进"

## 执行方式

1. 先完整阅读 Plan 文档：`docs/superpowers/plans/2026-04-01-plan4-conversation-agent-engine.md`
2. 再阅读 `CLAUDE.md` 了解项目约定
3. 按 Task 1 → Task 13 的依赖顺序逐个执行
4. 每个 Task 完成后，运行文档中指定的 `mvn compile` 验证命令
5. 编译通过后，执行文档中的 `git add` + `git commit` 命令
6. 如果编译失败，诊断并修复错误后再继续
7. 用 Task 工具跟踪进度

## 注意事项

- Plan 文档中的代码是**完整的、可直接使用的**，不需要自行"优化"或重新设计
- 但需要验证代码与现有代码的兼容性（import 路径、方法签名等），如有冲突以现有代码为准进行微调
- Task 9 中 `SpaceAuthorizationService.resolveUserClearance()` — 先检查该方法是否已存在，已存在则跳过
- Task 10 中 `AgentConfig` — 确认 `rag-infrastructure` 的 pom.xml 是否需要添加对 `rag-domain` 中 agent 包的依赖（通常不需要，因为已有 `rag-domain` 依赖）
- 最终的 Task 13 要跑 `mvn clean compile` 全量编译，确保所有模块无错误

开始执行。
