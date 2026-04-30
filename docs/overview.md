# Fizz - 任务队列调度服务系统设计

## 项目定位

Fizz 是一个通用的任务队列调度服务，负责接收业务服务提交的作业（Job），按照并行度与串行约束进行调度，通过 HTTP 调用远程服务执行任务（Task），并跟踪作业状态与进度。

**术语约定：**

- **Job（作业）**：业务方提交的一个工作单元，包含一组 Task
- **Task（任务）**：Job 下的最小执行单元，每个 Task 对应一次远程 HTTP 调用

## 技术选型

| 项目     | 选择                    |
| -------- | ----------------------- |
| 语言     | Java 25                 |
| 框架     | Spring Boot 4.x         |
| 构建     | Maven 多模块            |
| 并发模型 | 虚拟线程 (Project Loom) |
| 数据访问 | Spring Data JPA         |
| 数据库   | MySQL                   |
| 主键策略 | UUIDv7（应用层生成）    |

## 模块结构

```txt
fizz/
├── fizz-core/       # 核心模块：通用工具 + 调度引擎 + JPA Entity/Repository + 业务逻辑
│                      包: common / engine / domain / service（各自包名不变）
└── fizz-server/     # 服务器模块：Spring Boot 启动类 + REST API + 配置
│                      包: web / server（各自包名不变）
```

**依赖关系：**

```txt
fizz-core      ← 包含 common、engine、domain、service 全部源码
                   内部分层：common（基础工具）→ engine（接口定义 + 组件）→ domain（JPA）→ service（实现）
fizz-server    → fizz-core（组装业务逻辑，提供 REST API 与 Spring 配置）
```

## 架构模型

采用 **Actor Model** 风格设计，每个组件由一个虚拟线程驱动，通过 inbox + event loop 处理消息。

```
SchedulerCoordinator  (非 Actor, leader 锁循环)
  └→ Scheduler  (根调度器，1 实例)
       └── SchedulingGroup  (每 tenant + jobType 1 个，空闲自毁)
            ├── JobRunner  (每 Job 1 个，运行时存在)
            │   ├── TaskRunner  (每 Task 1 个，vthread 驱动)
            │   └── TaskRunner ...
            └── JobRunner ...
```

**通讯模式：**
- Parent → Child：直接调用 child 的方法（内部 post 到 child 的 inbox）
- Child → Parent：通过构造时注入的 parent 引用调用 `parent.tell(message)`

## 调度驱动模型

**事件驱动：**

| 触发方式    | 场景         | 说明                                                |
| ----------- | ------------ | --------------------------------------------------- |
| 新作业提交  | JobService   | `scheduler.tell(JobSubmitted)` → 路由到 SchedulingGroup |
| 任务完成    | TaskRunner | `parent.tell(TaskSucceeded/TaskFailed)` → 释放槽位，调度下一批 |
| 作业完成    | JobRunner | `parent.tell(JobSucceeded/JobFailed/JobCancelled)` → 释放槽位，激活下一个  |
| 作业取消    | JobService   | `scheduler.tell(CancelJob)` → 级联到 JobRunner   |
| 定时超时    | 保底         | 各组件 inbox.poll(timeout) 防止事件丢失              |

每个组件独立 event loop：`inbox.poll(timeout)` → `handle(message)` → `onIdle()`。有消息时被 `tell()` + `unpark` 唤醒，空闲时 park 在 inbox 上。

## 运行模式

- **单实例运行**：同一时刻只有一个实例可以执行调度
- **滚动更新安全**：通过 MySQL leader 锁 + 心跳实现旧退新进
- **进程恢复**：获取 Leader 锁后重建组件树，恢复悬空任务

## 核心能力

| 能力           | 说明                                                           |
| -------------- | -------------------------------------------------------------- |
| Job 并发度控制 | 按 jobType 配置每租户最大并发 Job 数（内存 Semaphore），不同 JobType 互不影响 |
| Task 并发度控制 | 每个 Job 独立配置（内存 Semaphore）                            |
| 串行约束       | 相同 queueing_key 的作业串行执行                               |
| 业务去重       | 通过 bizKey 参数防重复提交，与 jobType 共同唯一，重复幂等返回已有作业 |
| 延迟执行       | 作业可指定 scheduled_at 延迟启动                               |
| 作业取消       | 标记取消，不再调度新任务                                       |
| 失败重试       | 可配置最大尝试次数，支持固定/指数退避（按作业类型配置）        |
| 轮询型任务     | IN_PROGRESS 不消耗尝试次数，按 retryAfter 指定时间延迟         |
| 状态通知       | Job 状态变更时通知业务服务（可选，按作业类型配置 notify_path） |
| 事件驱动调度   | 新作业/任务完成/取消均即时通过 inbox 唤醒组件                  |
| 避免空转       | 事件驱动 + 定时保底，不轮询数据库                              |
| 动态配置       | 服务地址和作业类型通过 API 动态管理，存储在数据库              |
| 服务多实例     | 同一服务可注册多个实例，调用时轮询使用                         |
| 活跃作业表     | 调度器仅查活跃作业表，不受历史数据量影响                       |
| 乐观并发控制   | 所有数据表使用 version 字段实现 CAS                            |

## 文档索引

- [数据库设计](database-design.md)
- [调度引擎设计](scheduler-engine.md)
- [并发与实例控制](concurrency-control.md)
- [REST API 设计](rest-api.md)
- [服务发现与作业类型](service-discovery.md)
