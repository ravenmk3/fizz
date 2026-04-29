# REST API 设计

## 概述

Fizz 对外提供 HTTP API，分为两大类：

1. **作业管理** — 作业（Job）的提交、查询、取消
2. **配置管理** — 服务、服务实例、作业类型的管理

**API 风格：** 全部使用 POST 方法，无路径参数，路径以动作命名（如 `/api/jobs/create`、`/api/services/save` 等）。

## 统一响应格式

所有接口返回统一的消息包装：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

| 字段    | 说明                        |
| ------- | --------------------------- |
| code    | 0 表示成功，非 0 表示错误码 |
| message | 描述信息                    |
| data    | 响应数据，可为 null         |

**错误码规范：**

| code | 含义                           |
| ---- | ------------------------------ |
| 0    | 成功                           |
| 400  | 请求参数错误                   |
| 404  | 资源不存在                     |
| 409  | 冲突（如重复创建、状态不允许） |
| 500  | 内部错误                       |

---

## 一、作业管理 API

### 1.1 提交作业

**POST** `/api/jobs/create`

**请求体：**

```json
{
  "tenantId": "tenant-001",
  "serviceName": "sms-service",
  "jobType": "send-sms",
  "queueingKey": "campaign-123",
  "bizKey": "batch-20260428-001",
  "taskConcurrency": 5,
  "maxAttempts": -1,
  "scheduledAt": "2026-04-28T10:00:00",
  "tasks": [
    { "params": { "phone": "138xxxx1234", "content": "hello" } },
    { "params": { "phone": "139xxxx5678", "content": "world" } }
  ]
}
```

| 字段            | 类型            | 必填 | 说明                                  |
| --------------- | --------------- | ---- | ------------------------------------- |
| tenantId        | String          | 是   | 租户 ID                               |
| serviceName     | String          | 是   | 服务名，需已注册                      |
| jobType         | String          | 是   | 作业类型，需已注册                    |
| queueingKey     | String          | 否   | 排队 key，相同 key 串行执行           |
| bizKey          | String          | 否   | 业务去重键，与 jobType 共同唯一，重复提交幂等返回已存在作业 |
| taskConcurrency | Integer         | 否   | 任务并发度，默认 1                    |
| maxAttempts     | Integer         | 否   | 最大尝试次数，默认 -1（无限）         |
| scheduledAt     | String(ISO8601) | 否   | 延迟执行时间，不填则立即执行          |
| tasks           | Array           | 是   | 任务列表，至少 1 个                   |
| tasks[].params  | Object          | 是   | 任务参数，作为 HTTP Body 传给远程服务 |

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "01969d...",
    "status": "PENDING",
    "totalCount": 2,
    "createdAt": "2026-04-27T15:30:00.000",
    "created": true
  }
}
```

`created` 为 `true` 表示新建作业，`false` 表示 bizKey 重复、返回已存在的作业（幂等）。

**校验规则：**

- `serviceName` 必须在 `fizz_service` 中存在
- `jobType` 必须在 `fizz_job_type` 中存在
- `jobType` 对应的 `service_name` 必须与请求中的 `serviceName` 一致
- `tasks` 不能为空
- `taskConcurrency` > 0
- `bizKey` 非空时按 `(jobType, bizKey)` 查重：已存在则幂等返回已有作业（`created: false`），不重复创建

---

### 1.2 查询作业详情

**POST** `/api/jobs/get`

**请求体：**

```json
{
  "id": "01969d..."
}
```

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "01969d...",
    "tenantId": "tenant-001",
    "serviceName": "sms-service",
    "jobType": "send-sms",
    "queueingKey": "campaign-123",
    "taskConcurrency": 5,
    "maxAttempts": -1,
    "status": "RUNNING",
    "scheduledAt": null,
    "totalCount": 100,
    "completedCount": 45,
    "failedCount": 2,
    "progress": 47,
    "createdAt": "2026-04-27T15:30:00.000",
    "updatedAt": "2026-04-27T15:35:12.000"
  }
}
```

`progress` = `(completedCount + failedCount) * 100 / totalCount`，表示已到达终态的百分比。

---

### 1.3 查询作业列表

**POST** `/api/jobs/list`

**请求体：**

```json
{
  "tenantId": "tenant-001",
  "status": "RUNNING",
  "serviceName": "sms-service",
  "jobType": "send-sms",
  "page": 1,
  "size": 20
}
```

| 参数        | 类型    | 必填 | 说明                        |
| ----------- | ------- | ---- | --------------------------- |
| tenantId    | String  | 否   | 按租户筛选                  |
| status      | String  | 否   | 按状态筛选                  |
| serviceName | String  | 否   | 按服务名筛选                |
| jobType     | String  | 否   | 按作业类型筛选              |
| page        | Integer | 否   | 页码，默认 1                |
| size        | Integer | 否   | 每页大小，默认 20，最大 100 |

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [ ... ],
    "total": 150,
    "page": 1,
    "size": 20
  }
}
```

---

### 1.4 取消作业

**POST** `/api/jobs/cancel`

**请求体：**

```json
{
  "id": "01969d..."
}
```

**行为：**

1. 将作业状态设为 `CANCELLED`
2. 将所有 PENDING 状态的任务设为 `CANCELLED`
3. 不中断正在 RUNNING 的任务（等其自然完成后不再调度新的）
4. 从 `fizz_active_job` 中删除
5. 若 job_type 配置了 `notify_path`，插入 CANCELLED 通知
6. 唤醒调度器（释放并行度/排队约束）

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "01969d...",
    "status": "CANCELLED",
    "cancelledTasks": 55
  }
}
```

**错误情况：**

- 作业不存在：返回 404
- 作业已经是终态（SUCCESS / FAILED / CANCELLED）：返回 409

---

## 二、配置管理 API — 服务

### 2.1 保存服务

**POST** `/api/services/save`

```json
{
  "serviceName": "sms-service"
}
```

### 2.2 查询服务列表

**POST** `/api/services/list`

**请求体：** `{}`（空对象）

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "serviceName": "sms-service",
      "instances": [
        { "id": "01969d...", "scheme": "http", "host": "10.0.1.10", "port": 8080 },
        { "id": "01969e...", "scheme": "http", "host": "10.0.1.11", "port": 8080 }
      ]
    }
  ]
}
```

### 2.3 删除服务

**POST** `/api/services/delete`

```json
{
  "serviceName": "sms-service"
}
```

**约束：** 如果有作业类型引用该服务，返回 409 拒绝删除。

---

## 三、配置管理 API — 服务实例

### 3.1 保存服务实例

**POST** `/api/service-instances/save`

```json
{
  "serviceName": "sms-service",
  "scheme": "http",
  "host": "10.0.1.10",
  "port": 8080
}
```

| 字段        | 类型    | 必填 | 默认值 | 说明                   |
| ----------- | ------- | ---- | ------ | ---------------------- |
| serviceName | String  | 是   | -      | 所属服务名（需已注册） |
| scheme      | String  | 否   | http   | 协议：http / https     |
| host        | String  | 是   | -      | 实例地址               |
| port        | Integer | 是   | -      | 实例端口               |

### 3.2 移除服务实例

**POST** `/api/service-instances/delete`

```json
{
  "id": "01969d..."
}
```

### 3.3 查询服务实例列表

**POST** `/api/service-instances/list`

```json
{
  "serviceName": "sms-service"
}
```

---

## 四、配置管理 API — 作业类型

### 4.1 保存作业类型

**POST** `/api/job-types/save`

```json
{
  "serviceName": "sms-service",
  "jobType": "send-sms",
  "taskPath": "/api/sms/send",
  "httpMethod": "POST",
  "timeoutMs": 30000,
  "backoffStrategy": "FIXED",
  "backoffInitialMs": 10000,
  "backoffMaxMs": 300000,
  "jobConcurrency": 10,
  "taskConcurrency": 5,
  "notifyPath": "/api/sms/job-status"
```

| 字段             | 类型    | 必填 | 默认值 | 说明                                  |
| ---------------- | ------- | ---- | ------ | ------------------------------------- |
| serviceName      | String  | 是   | -      | 所属服务名                            |
| jobType          | String  | 是   | -      | 作业类型标识（唯一）                  |
| taskPath         | String  | 是   | -      | 任务执行 API 路径                     |
| httpMethod       | String  | 否   | POST   | HTTP 方法                             |
| timeoutMs        | Integer | 否   | 30000  | 单次调用超时（毫秒）                  |
| backoffStrategy  | String  | 否   | FIXED  | FIXED / EXPONENTIAL                   |
| backoffInitialMs | Integer | 否   | 10000  | 初始退避时间（毫秒）                  |
| backoffMaxMs     | Integer | 否   | 300000 | 最大退避时间（毫秒）                  |
| jobConcurrency   | Integer | 否   | 10     | 每租户最大并发作业数                  |
| taskConcurrency  | Integer | 否   | 1      | 默认任务并发度（创建作业时可覆盖）      |
| notifyPath       | String  | 否   | null   | 状态通知路径，不填则不通知            |

### 4.2 更新作业类型

**POST** `/api/job-types/update`

```json
{
  "jobType": "send-sms",
  "taskPath": "/api/sms/send",
  "httpMethod": "POST",
  "timeoutMs": 60000,
  "backoffStrategy": "EXPONENTIAL",
  "backoffInitialMs": 5000,
  "backoffMaxMs": 120000,
  "jobConcurrency": 20,
  "taskConcurrency": 10,
  "notifyPath": "/api/sms/job-status"
```

`jobType` 作为标识，不可修改。`serviceName` 不可修改。仅更新请求体中包含的字段。

### 4.3 查询作业类型列表

**POST** `/api/job-types/list`

```json
{
  "serviceName": "sms-service"
}
```

`serviceName` 可选，不填返回全部。

### 4.4 删除作业类型

**POST** `/api/job-types/delete`

```json
{
  "jobType": "send-sms"
}
```

**约束：** 如果有 PENDING 或 RUNNING 状态的作业引用该类型，返回 409 拒绝删除。
