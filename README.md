# Fizz

通用的任务队列调度服务。通过 HTTP 调用远程服务执行任务，并跟踪作业状态与进度。

## 快速开始

### 环境要求

- Java 25
- MySQL 8.0+
- Maven 3.9+

### 构建

```bash
mvn package -DskipTests
```

### 配置

```bash
cp fizz-server/src/main/resources/application-sample.yml fizz-server/src/main/resources/application.yml
```

编辑 `application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fizz
    username: root
    password: your-password
  jpa:
    hibernate:
      ddl-auto: validate
```

然后在数据库中执行 `docs/database/schema.sql` 中的建表语句。

### 运行

```bash
mvn -pl fizz-server spring-boot:run
```
