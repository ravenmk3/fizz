CREATE TABLE IF NOT EXISTS `fizz_scheduler_lock`
(
    `id`           INT         NOT NULL DEFAULT 1,
    `instance_id`  VARCHAR(32) NOT NULL COMMENT '持有锁的实例 UUID',
    `acquired_at`  DATETIME(3) NOT NULL COMMENT '获取锁时间',
    `heartbeat_at` DATETIME(3) NOT NULL COMMENT '最后心跳时间',

    PRIMARY KEY (`id`),
    CONSTRAINT `chk_single_row` CHECK (`id` = 1)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '调度器锁';


CREATE TABLE IF NOT EXISTS `fizz_service`
(
    `id`           CHAR(32)     NOT NULL COMMENT 'UUIDv7',
    `service_name` VARCHAR(128) NOT NULL COMMENT '服务名',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_name` (`service_name`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '服务注册';


CREATE TABLE IF NOT EXISTS `fizz_service_instance`
(
    `id`           CHAR(32)     NOT NULL COMMENT 'UUIDv7',
    `service_name` VARCHAR(128) NOT NULL COMMENT '所属服务名',
    `scheme`       VARCHAR(10)  NOT NULL DEFAULT 'http' COMMENT '协议: http / https',
    `host`         VARCHAR(256) NOT NULL COMMENT '实例地址',
    `port`         INT          NOT NULL COMMENT '实例端口',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_host_port` (`service_name`, `host`, `port`),
    KEY `idx_service_name` (`service_name`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '服务实例表';


CREATE TABLE IF NOT EXISTS `fizz_job_type`
(
    `id`                 CHAR(32)     NOT NULL COMMENT 'UUIDv7',
    `job_type`           VARCHAR(128) NOT NULL COMMENT '作业类型标识',
    `service_name`       VARCHAR(128) NOT NULL COMMENT '所属服务名',
    `task_path`          VARCHAR(256) NOT NULL COMMENT '任务执行 API 路径',
    `notify_path`        VARCHAR(256) NULL COMMENT '状态通知 API 路径（可选，NULL 表示不通知）',
    `http_method`        VARCHAR(10)  NOT NULL DEFAULT 'POST' COMMENT 'HTTP 方法',
    `timeout_ms`         INT          NOT NULL DEFAULT 30000 COMMENT '单次 HTTP 调用超时(毫秒)',
    `backoff_strategy`   VARCHAR(16)  NOT NULL DEFAULT 'FIXED' COMMENT '退避策略: FIXED / EXPONENTIAL',
    `backoff_initial_ms` INT          NOT NULL DEFAULT 10000 COMMENT '初始退避时间(毫秒)',
    `backoff_max_ms`     INT          NOT NULL DEFAULT 300000 COMMENT '最大退避时间(毫秒)，指数退避封顶',
    `job_concurrency`    INT          NOT NULL DEFAULT 10 COMMENT '每租户最大并发作业数',
    `task_concurrency`   INT          NOT NULL DEFAULT 1 COMMENT '默认任务并发度（创建作业时若未指定则取此值）',
    `version`            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_type` (`job_type`),
    KEY `idx_service_name` (`service_name`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '作业类型配置';


CREATE TABLE IF NOT EXISTS `fizz_job`
(
    `id`               CHAR(32)     NOT NULL COMMENT 'UUIDv7',
    `tenant_id`        VARCHAR(64)  NOT NULL COMMENT '租户 ID',
    `service_name`     VARCHAR(128) NOT NULL COMMENT '服务名',
    `job_type`         VARCHAR(128) NOT NULL COMMENT '作业类型',
    `queueing_key`     VARCHAR(256) NULL COMMENT '排队 key，相同 key 的作业串行执行',
    `biz_key`          VARCHAR(256) NULL COMMENT '业务去重键，与 job_type 共同唯一',
    `task_concurrency` INT          NOT NULL DEFAULT 1 COMMENT '任务并发度',
    `max_attempts`     INT          NOT NULL DEFAULT -1 COMMENT '任务最大尝试次数，-1 表示无限',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    `scheduled_at`     DATETIME(3)  NULL COMMENT '计划启动时间，NULL 表示立即执行',
    `total_count`      INT          NOT NULL DEFAULT 0 COMMENT '任务总数',
    `completed_count`  INT          NOT NULL DEFAULT 0 COMMENT '已完成任务数(SUCCESS)',
    `failed_count`     INT          NOT NULL DEFAULT 0 COMMENT '已失败任务数(FAILED)',
    `instance_id`      VARCHAR(32)  NULL COMMENT '持有该作业的实例 UUID',
    `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_queueing_key_status` (`queueing_key`, `status`),
    KEY `idx_scheduled_at` (`scheduled_at`),
    UNIQUE KEY `uk_job_type_biz_key` (`job_type`, `biz_key`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '作业';


CREATE TABLE IF NOT EXISTS `fizz_task`
(
    `id`           CHAR(32)     NOT NULL COMMENT 'UUIDv7',
    `job_id`       CHAR(32)     NOT NULL COMMENT '所属作业 ID',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    `attempts`     INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数（仅失败时+1）',
    `available_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次可执行时间',
    `last_result`  VARCHAR(16)  NULL COMMENT '上次执行结果: SUCCESS/FAILED/IN_PROGRESS',
    `last_error`   VARCHAR(512) NULL COMMENT '最后一次执行的错误信息',
    `instance_id`  VARCHAR(32)  NULL COMMENT '执行该任务的实例 UUID',
    `params`       JSON         NOT NULL COMMENT '请求参数，作为 HTTP Body',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    KEY `idx_job_status` (`job_id`, `status`),
    KEY `idx_job_available` (`job_id`, `status`, `available_at`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '任务';


CREATE TABLE IF NOT EXISTS `fizz_active_job`
(
    `id`           CHAR(32)     NOT NULL COMMENT '作业 ID',
    `tenant_id`    VARCHAR(64)  NOT NULL COMMENT '租户 ID（冗余，避免 JOIN）',
    `queueing_key` VARCHAR(256) NULL COMMENT '排队 key（冗余，避免 JOIN）',
    `status`       VARCHAR(16)  NOT NULL COMMENT 'PENDING/RUNNING',
    `scheduled_at` DATETIME(3)  NULL COMMENT '计划启动时间（冗余，避免 JOIN）',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_queueing_key_status` (`queueing_key`, `status`),
    KEY `idx_scheduled_at` (`scheduled_at`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '未结束的活跃作业';


CREATE TABLE IF NOT EXISTS `fizz_job_notification`
(
    `id`           CHAR(32)     NOT NULL COMMENT 'UUIDv7',
    `job_id`       CHAR(32)     NOT NULL COMMENT '作业 ID',
    `job_status`   VARCHAR(16)  NOT NULL COMMENT '通知的 Job 状态: RUNNING/SUCCESS/FAILED/CANCELLED',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '发送状态: PENDING/FAILED（成功则删除记录）',
    `attempts`     INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    `max_attempts` INT          NOT NULL DEFAULT 10 COMMENT '最大尝试次数',
    `available_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次可发送时间',
    `last_error`   VARCHAR(512) NULL COMMENT '最后一次失败的错误信息',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    KEY `idx_status_available` (`status`, `available_at`),
    KEY `idx_job_id` (`job_id`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT '作业状态通知';
