# Fizz

Fizz 是一个通用的任务队列调度服务，负责接收业务服务提交的作业（Job），按照并行度与串行约束进行调度，通过 HTTP 调用远程服务执行任务（Task），并跟踪作业状态与进度。

- 技术栈: Java 25 + Spring Boot 4.x + MySQL + Virtual Threads
- 模块: fizz-core → fizz-server

## 文档索引

- [系统概述](docs/overview.md) — 定位、术语、Actor 组件模型、核心能力
- [数据库设计](docs/database-design.md) — 表结构、状态流转、ORM 策略
- [调度引擎设计](docs/scheduler-engine.md) — 组件架构、事件驱动、并行度控制、虚拟线程
- [并发与实例控制](docs/concurrency-control.md) — Leader 选举、滚动更新、悬空任务恢复、Semaphore 槽位
- [REST API 设计](docs/rest-api.md) — 作业/服务/实例/作业类型全部接口
- [服务发现与作业类型](docs/service-discovery.md) — 多实例轮询、缓存、URL 组装

## 配置文件

只保留带注释的 `application-sample.yml`
