# 服务发现与作业类型

## 概述

Fizz 通过**服务发现**机制获取远程服务的网络地址，通过**作业类型注册**机制获取 HTTP 调用的具体配置（路径、方法、超时、退避策略）。

两者均存储在 MySQL 数据库中，通过 API 动态管理，并在内存中缓存以提升调度性能。

**关键特性：** 一个服务可以有多个实例，调度器通过 Round-Robin 轮询选择实例。

## 一、服务发现

### 架构设计（接口定义于 fizz-core 的 engine 包）

```java
public interface ServiceDiscovery {

    ServiceEndpoint resolve(String serviceName);
}

public record ServiceEndpoint(String scheme, String host, int port) {

    public String baseUrl() {
        return scheme + "://" + host + ":" + port;
    }
}
```

### DatabaseServiceDiscovery — 默认实现（fizz-core 的 service 包）

```java
public class DatabaseServiceDiscovery implements ServiceDiscovery {

    private final ServiceInstanceRepository instanceRepo;
    private final LoadingCache<String, List<ServiceEndpoint>> cache;
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    public DatabaseServiceDiscovery(ServiceInstanceRepository instanceRepo) {
        this.instanceRepo = instanceRepo;
        this.cache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build(this::loadFromDb);
    }

    @Override
    public ServiceEndpoint resolve(String serviceName) {
        List<ServiceEndpoint> endpoints = cache.get(serviceName);
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("No instances found for service: " + serviceName);
        }

        // Round-Robin 轮询
        AtomicInteger counter = roundRobinCounters
            .computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        int index = Math.floorMod(counter.getAndIncrement(), endpoints.size());
        return endpoints.get(index);
    }

    private List<ServiceEndpoint> loadFromDb(String serviceName) {
        return instanceRepo.findByServiceName(serviceName).stream()
            .map(e -> new ServiceEndpoint(e.getScheme(), e.getHost(), e.getPort()))
            .toList();
    }

    /** 服务实例变更时清除缓存。 */
    public void invalidate(String serviceName) {
        cache.invalidate(serviceName);
        roundRobinCounters.remove(serviceName);
    }
}
```

**缓存策略：**

- 最大 100 个服务
- 写入后 30 秒过期，保证配置变更能在 30 秒内生效
- 服务实例通过 API 增删时主动 invalidate

### 多实例轮询

```txt
服务: sms-service
实例:
  ① http://10.0.1.10:8080
  ② http://10.0.1.11:8080
  ③ http://10.0.1.12:8080

调用序列:
  第 1 次 resolve("sms-service") → ①
  第 2 次 resolve("sms-service") → ②
  第 3 次 resolve("sms-service") → ③
  第 4 次 resolve("sms-service") → ①  (循环)
```

### 扩展点

未来如需对接服务注册中心，只需新增实现类：

```java
public class NacosServiceDiscovery implements ServiceDiscovery { ... }
public class ConsulServiceDiscovery implements ServiceDiscovery { ... }
```

通过 Spring 配置切换即可，调度引擎代码无需修改。

---

## 二、作业类型注册

### 架构设计（接口定义于 fizz-core 的 engine 包）

```java
public interface JobTypeRegistry {

    JobTypeConfig get(String jobType);
}

public record JobTypeConfig(
    String serviceName,
    String jobType,
    String taskPath,
    String notifyPath,      // nullable，不为 null 时在 Job 状态变更时发送通知
    String httpMethod,
    int timeoutMs,
    BackoffStrategy backoffStrategy,
    int backoffInitialMs,
    int backoffMaxMs,
    int jobConcurrency,     // 每租户同类型最大并发 Job 数
    int taskConcurrency     // 默认任务并发度（创建作业时若未指定则取此值）
) {}

public enum BackoffStrategy {
    FIXED,
    EXPONENTIAL
}
```

### DatabaseJobTypeRegistry — 默认实现（fizz-core 的 service 包）

```java
public class DatabaseJobTypeRegistry implements JobTypeRegistry {

    private final JobTypeRepository jobTypeRepo;
    private final LoadingCache<String, JobTypeConfig> cache;

    public DatabaseJobTypeRegistry(JobTypeRepository jobTypeRepo) {
        this.jobTypeRepo = jobTypeRepo;
        this.cache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build(this::loadFromDb);
    }

    @Override
    public JobTypeConfig get(String jobType) {
        return cache.get(jobType);
    }

    private JobTypeConfig loadFromDb(String jobType) {
        JobTypeEntity entity = jobTypeRepo.findByJobType(jobType)
            .orElseThrow(() -> new IllegalArgumentException("Job type not found: " + jobType));
        return new JobTypeConfig(
            entity.getServiceName(),
            entity.getJobType(),
            entity.getTaskPath(),
            entity.getNotifyPath(),
            entity.getHttpMethod(),
            entity.getTimeoutMs(),
            entity.getBackoffStrategy(),
            entity.getBackoffInitialMs(),
            entity.getBackoffMaxMs(),
            entity.getJobConcurrency(),
            entity.getTaskConcurrency()
        );
    }

    public void invalidate(String jobType) {
        cache.invalidate(jobType);
    }
}
```

---

## 三、HTTP 调用 URL 组装

TaskRunner 执行任务时，URL 组装逻辑：

```java
// 解析服务地址（多实例轮询）
ServiceEndpoint endpoint = serviceDiscovery.resolve(job.getServiceName());

// 获取作业类型配置
JobTypeConfig config = jobTypeRegistry.get(job.getJobType());

// 组装完整 URL
// 例: http://10.0.1.10:8080/api/sms/send
URI uri = URI.create(endpoint.baseUrl() + config.taskPath());
```

### 完整调用示例

假设配置：

- `fizz_service`: `sms-service`
- `fizz_service_instance`: `http://10.0.1.10:8080`, `http://10.0.1.11:8080`
- `fizz_job_type`: `send-sms` → `taskPath=/api/sms/send, method=POST, timeout=30000ms, notifyPath=/api/sms/job-status`

任务参数：

```json
{ "phone": "138xxxx1234", "content": "验证码：1234" }
```

实际发出的请求（轮询到第一个实例）：

```http
POST http://10.0.1.10:8080/api/sms/send
Content-Type: application/json

{"phone":"138xxxx1234","content":"验证码：1234"}
```

远程服务响应（统一消息包装）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "SUCCESS",
    "message": "短信已发送"
  }
}
```

### 状态通知示例

当 Job 到达终态时，调度器向 notify_path 发送通知：

```http
POST http://10.0.1.11:8080/api/sms/job-status
Content-Type: application/json

{
  "jobId": "01969d...",
  "status": "SUCCESS",
  "totalCount": 100,
  "completedCount": 98,
  "failedCount": 2
}
```

---

## 四、配置变更生效机制

| 操作                   | 生效方式                                   |
| ---------------------- | ------------------------------------------ |
| 新增服务/实例/作业类型 | 立即可用（首次查询加载）                   |
| 修改服务实例           | API 操作时 invalidate 缓存，最多 30 秒生效 |
| 修改作业类型配置       | 同上                                       |
| 删除服务               | 需无关联作业类型                           |
| 删除服务实例           | 即时生效，下次缓存加载不包含该实例         |
| 删除作业类型           | 需无 PENDING/RUNNING 作业引用              |

---

## 五、配置示例

### 场景 1：短信发送（即时成功型，多实例，带状态通知）

```txt
服务: sms-service
实例:
  - http://10.0.1.10:8080
  - http://10.0.1.11:8080

作业类型: send-sms
  taskPath: /api/sms/send
  method: POST
  timeout: 30000ms
  backoff: FIXED, 10000ms
  jobConcurrency: 10
  taskConcurrency: 5
  notifyPath: /api/sms/job-status
```

远程服务收到请求后直接发送短信，返回：

```json
{ "code": 0, "data": { "status": "SUCCESS" } }
```

Job 完成后，调度器向 `/api/sms/job-status` 发送状态通知。

### 场景 2：订单状态查询（轮询型，无状态通知）

```txt
服务: order-service
实例:
  - https://10.0.1.20:8443

作业类型: check-order-status
  taskPath: /api/order/check
  method: POST
  timeout: 60000ms
  backoff: EXPONENTIAL, initial=5000ms, max=120000ms
  jobConcurrency: 5
  taskConcurrency: 2
  notifyPath: null
```

远程服务查询外部系统，可能返回：

```json
{ "code": 0, "data": { "status": "IN_PROGRESS", "retryAfter": "2026-04-28T10:05:30" } }
```

调度器不消耗尝试次数，到指定时间后再次调用。

### 场景 3：数据导出（可能失败需重试）

```txt
服务: export-service
实例:
  - http://10.0.1.30:8080
  - http://10.0.1.31:8080

作业类型: export-data
  taskPath: /api/export/execute
  method: POST
  timeout: 120000ms
  backoff: EXPONENTIAL, initial=10000ms, max=300000ms
  jobConcurrency: 3
  taskConcurrency: 3
  notifyPath: /api/export/job-status
```

远程服务可能返回失败（外部依赖不可用）：

```json
{ "code": 0, "data": { "status": "FAILED", "message": "downstream timeout" } }
```

调度器增加 attempts，按指数退避重试。
