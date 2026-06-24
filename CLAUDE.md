# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 仓库定位

Flingsagent 是一个企业级 Agentic RAG 平台，覆盖文档入库到智能问答的全链路。后端 Spring Boot 3 + Java 17 多模块 Maven 工程，前端 React 18 + Vite + TypeScript。

## 常用命令

### 后端（Maven 多模块）

```bash
# 在仓库根目录构建全部模块（注：spotless 会在 compile 阶段自动给 .java 加 Apache 2.0 头）
./mvnw clean install -DskipTests

# 只构建可启动模块
./mvnw -pl bootstrap -am package -DskipTests
./mvnw -pl mcp-server -am package -DskipTests

# 启动主应用（端口 9090，context-path /api/flingsagent）
# 注意：不要加 -am，否则反应器会把 packaging=pom 的父项目也拉进来跑 spring-boot:run 失败
# 第一次跑前先 `./mvnw clean install -DskipTests` 把 framework/infra-ai 装到本地仓库
./mvnw -pl bootstrap spring-boot:run

# 启动 MCP Server（端口 9099，被主应用通过 rag.mcp.servers 调用）
./mvnw -pl mcp-server spring-boot:run

# 单测
./mvnw -pl bootstrap test
./mvnw -pl bootstrap test -Dtest=ConversationMessageServiceTests
./mvnw -pl bootstrap test -Dtest=MultiQuestionRewriteServiceTests#methodName
```

`surefire` 配置了 mockito-core 作为 javaagent，加测试时无需额外参数。

### 前端

```bash
cd frontend
npm install
npm run dev      # Vite 开发服务器，5173，/api → http://localhost:9090
npm run build
npm run lint     # ESLint --max-warnings 0
npm run format   # Prettier
```

注意 `frontend/TESTING.md` 中关于 proxy target 的描述是历史的 8080，实际配置在 [vite.config.ts](frontend/vite.config.ts) 是 9090。

### 基础设施

`resources/docker/` 下有 Milvus、RocketMQ 的 compose 文件；`resources/docker/lightweight/` 是内存受限场景的轻量版。Postgres 需要单独装并安装 pgvector 扩展，初始化脚本：

```bash
psql -h 127.0.0.1 -U postgres -d ragent -f resources/database/schema_pg.sql
psql -h 127.0.0.1 -U postgres -d ragent -f resources/database/init_data_pg.sql
# 升级旧版本时按需执行 upgrade_v1.0_to_v1.1.sql / upgrade_v1.1_to_v1.2.sql
```

## 架构概览

### 四模块分层

依赖方向：`bootstrap` → `framework`、`bootstrap` → `infra-ai`、`mcp-server` 独立。

- **framework**：业务无关的横切基础设施。`exception/` 三级异常（Client/Service/Remote）+ [GlobalExceptionHandler.java](framework/src/main/java/com/flings/ai/flingsagent/framework/web/GlobalExceptionHandler.java)；`idempotent/` 双维度幂等（Submit/Consume）；`distributedid/` Snowflake；`context/` UserContext + ApplicationContextHolder + TTL 透传约定；`trace/` `@RagTraceRoot` / `@RagTraceNode` 注解和 `RagTraceContext`；`web/` `Results` + `SseEmitterSender`（线程安全 SSE 封装）；`mq/` RocketMQ 事务消息抽象。业务模块只引依赖+加注解，零样板。
- **infra-ai**：屏蔽模型供应商差异。`chat/` 多家 OpenAI 风格的 `ChatClient` 实现（Bailian、Ollama、AIHubMix、SiliconFlow）+ `RoutingLLMService`（基于优先级 + 三态熔断的路由）+ `LlmFirstPacketProbe`（首包探测）+ `ProbeStreamBridge`（首包前缓冲、首包后透传）。`embedding/`、`rerank/` 同样的路由模式。`model/` 通用路由组件：`ModelSelector` + `ModelHealthStore`（三态熔断器 CLOSED/OPEN/HALF_OPEN） + `ModelRoutingExecutor`。新增模型供应商：实现 `ChatClient` / `EmbeddingClient` / `RerankClient`，配置 `ai.chat.candidates` 即参与路由。
- **bootstrap**：业务实现。包结构按业务域划分：
  - `rag/` —— 问答核心。`core/intent` 树形意图体系；`core/rewrite` 问题重写/拆分/术语映射；`core/retrieve` 多通道并行检索 + 后处理流水线；`core/memory` 滑动窗口 + 摘要压缩的对话记忆；`core/mcp` MCP 客户端 + 工具注册表；`core/prompt` 提示词组装；`core/guidance` 意图歧义引导；`service/pipeline/StreamChatPipeline.java` 是 SSE 流式问答的总编排；`service/ratelimit/` 队列式排队限流（Redis ZSET + Pub/Sub）；`aop/RagTraceAspect.java` 把 `@RagTraceNode` 落库到 `t_rag_trace_node`。
  - `knowledge/` —— 知识库/文档/分块的 CRUD + 定时扫描调度（`schedule/`）+ 文档分块的事务消息消费（`mq/`）。
  - `ingestion/` —— 节点编排的入库 Pipeline。`node/` 是模板方法基类 `IngestionNode` 的子类（Fetcher/Parser/Enhancer/Chunker/Enricher/Indexer）；`engine/IngestionEngine.java` 按数据库配置驱动 + `ConditionEvaluator` 条件分支；`strategy/fetcher/` 各种数据源拉取实现。
  - `core/parser`、`core/chunk` —— 通用解析（Tika/Markdown）与分块策略（FixedSize / StructureAware），可被 ingestion 节点和知识库分块流程复用。
  - `admin/` —— 仪表盘聚合查询。
  - `user/` —— Sa-Token 集成的用户体系。
- **mcp-server**：独立的 MCP 工具服务，9099 端口，目前内置 Sales/Ticket/Weather 三个示例 `MCPToolExecutor`。主应用通过 `rag.mcp.servers` 配置发现并由 LLM 路由调用。

### 三个核心可扩展点

新增能力都按照"实现接口 + `@Component` 注册"，无需改框架：

| 接口 | 位置 | 作用 |
|---|---|---|
| `SearchChannel` | `rag/core/retrieve/channel/` | 新检索通道（向量/意图/未来 ES 关键词等），并行调度 |
| `SearchResultPostProcessor` | `rag/core/retrieve/postprocessor/` | 后处理链（去重 → Rerank → 自定义版本过滤等） |
| `IngestionNode` | `ingestion/node/` | 文档入库 Pipeline 节点（模板方法） |
| `MCPToolExecutor` | `mcp-server` 侧 | MCP 工具实现，被 `DefaultMcpToolRegistry` 自动发现 |
| `ChatClient` / `EmbeddingClient` / `RerankClient` | `infra-ai/` | 新模型供应商 |

详细架构和扩展示例见 [docs/multi-channel-retrieval.md](docs/multi-channel-retrieval.md) 和 [docs/quick-start.md](docs/quick-start.md)。

### 一次问答的核心链路

[StreamChatPipeline.java](bootstrap/src/main/java/com/flings/ai/flingsagent/rag/service/pipeline/StreamChatPipeline.java) 编排：排队限流 → 加载记忆（并行加载摘要+历史） → 意图识别 → 问题重写/拆分 → 多通道并行检索 → 后处理（去重/Rerank） → Prompt 组装 → 路由调用 LLM（带首包探测+熔断降级） → SSE 流式回写 → 异步落库消息 + 摘要更新。整条链路通过 `@RagTraceNode` 落库到 `t_rag_trace_run` / `t_rag_trace_node`，前端 admin/traces 页面可视化。

### 八个独立线程池

[ThreadPoolExecutorConfig.java](bootstrap/src/main/java/com/flings/ai/flingsagent/rag/config/ThreadPoolExecutorConfig.java) 按负载特征定义了 8+ 个 Bean（`mcpBatchExecutor`、`ragContextExecutor`、`ragRetrievalExecutor`、`innerRetrievalExecutor`、`intentClassifyExecutor`、`memorySummaryExecutor`、`modelStreamExecutor`、`chatEntryExecutor`、`knowledgeChunkExecutor`、`memoryLoadExecutor`）。**所有线程池都用 `TtlExecutors.getTtlExecutor` 包装**，否则 `UserContext` / `RagTraceContext` 不会在异步线程中透传。新增异步逻辑务必复用现有 Executor 而不是临时 `new ThreadPoolExecutor`。

### 向量库可切换

`rag.vector.type=pg|milvus`。`RetrieverService` 两个实现：[PgRetrieverService](bootstrap/src/main/java/com/flings/ai/flingsagent/rag/core/retrieve/PgRetrieverService.java) 走 pgvector（默认）、[MilvusRetrieverService](bootstrap/src/main/java/com/flings/ai/flingsagent/rag/core/retrieve/MilvusRetrieverService.java) 走 Milvus。改向量库不需要碰业务代码。

## 代码风格 & 约定

- **Apache 2.0 license header 必加**：`pom.xml` 配置了 `spotless-maven-plugin` 的 `apply` goal 绑定到 `compile` 阶段，运行 `mvn compile` 会自动给新文件加上 `resources/format/copyright.txt`，所以新建 `.java` 时不必手写头，但**编辑现有文件不要删头**。
- **Lombok 大量使用**：`@Data`、`@Builder`、`@Slf4j`、`@RequiredArgsConstructor`。`lombok.config` 设置了 `copyableAnnotations += @Qualifier`，构造器注入 + `@Qualifier` 指定 Bean 名称是惯用法。
- **MyBatis Plus**：Mapper 扫描包在 [FlingsagentApplication.java](bootstrap/src/main/java/com/flings/ai/flingsagent/FlingsagentApplication.java) 显式列出（rag/ingestion/knowledge/user 四个 `dao.mapper`）；新增 Mapper 包要同步追加。
- **Snowflake 主键**：实体类用 `String id`，`CustomIdentifierGenerator` 自动填充。
- **响应封装**：Controller 统一返回 `Result<T>` / `Results.success(...)`；异常抛 `ClientException` / `ServiceException`，由 `GlobalExceptionHandler` 统一处理。

## 关键配置入口

[application.yaml](bootstrap/src/main/resources/application.yaml)：

- `server.port=9090`，`context-path=/api/flingsagent` —— 所有 REST 接口前缀都带 `/api/flingsagent`。
- `ai.providers.*` 配各家服务商的 url + apiKey（环境变量 `BAILIAN_API_KEY` 等），`ai.chat.candidates` / `ai.embedding.candidates` / `ai.rerank.candidates` 用优先级列表配候选模型。
- `ai.selection.failure-threshold` + `open-duration-ms` 控制熔断器阈值。
- `rag.rate-limit.global` 队列式排队限流参数；`rag.memory.*` 控制摘要触发的轮次/字数。
- `rag.search.channels.*` 配多通道检索的触发阈值（`vector-global.confidence-threshold` 等）。
- 默认 Redis 密码是 `123456`，本地起 Redis 时记得加 `requirepass`。

## 已知坑

- 主应用与 mcp-server 是两个独立的 Spring Boot 进程，开发完整功能需要都启动；只跑 bootstrap 时所有非知识类意图的 MCP 调用都会失败。
- pgvector 扩展必须先在数据库里 `CREATE EXTENSION vector`，否则 `schema_pg.sql` 执行不过。
- 新增异步任务忘记用 `TtlExecutors` 包装的 Executor，会导致 trace 日志和登录用户上下文丢失，链路追踪页会出现孤立节点。
- `surefire` 的 `argLine` 已经包含 `@{argLine}`，本地用 IDE 跑测试时 IDE 配置不要覆盖掉它，否则 mockito javaagent 不生效。
