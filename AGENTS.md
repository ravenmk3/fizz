# Fizz

Fizz 是一个通用的任务队列调度服务，负责接收业务服务提交的作业（Job），按照并行度与串行约束进行调度，
通过 HTTP 调用远程服务执行任务（Task），并跟踪作业状态与进度。

- 技术栈: Java 25 + Spring Boot 4.x + MySQL + Virtual Threads
- 模块: fizz-core → fizz-server

## 文档索引

WIP

## 配置文件

只保留带注释的 `application-sample.yml`
