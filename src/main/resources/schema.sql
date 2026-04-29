-- DPDK Core Analyzer 数据库表结构
-- 兼容 H2 (dev) 和 MySQL (prod)

CREATE TABLE IF NOT EXISTS upload_files (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(20) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_hash       VARCHAR(64) NOT NULL,
    storage_path    VARCHAR(500) NOT NULL,
    upload_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20) DEFAULT 'UPLOADED'
);

CREATE TABLE IF NOT EXISTS parse_tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    gdb_log_file_id BIGINT NOT NULL,
    exec_file_id    BIGINT NOT NULL,
    task_name       VARCHAR(200),
    status          VARCHAR(20) DEFAULT 'PENDING',
    parse_version   VARCHAR(20),
    total_threads   INT DEFAULT 0,
    crash_signal    VARCHAR(50),
    fault_address   VARCHAR(18),
    error_message   TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    FOREIGN KEY (gdb_log_file_id) REFERENCES upload_files(id),
    FOREIGN KEY (exec_file_id) REFERENCES upload_files(id)
);

CREATE TABLE IF NOT EXISTS thread_stacks (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT NOT NULL,
    thread_id     VARCHAR(100) NOT NULL,
    thread_name   VARCHAR(200),
    os_thread_id  VARCHAR(20),
    is_lcore      BOOLEAN DEFAULT FALSE,
    lcore_id      INT,
    crash_thread  BOOLEAN DEFAULT FALSE,
    stack_depth   INT DEFAULT 0,
    raw_header    TEXT,
    FOREIGN KEY (task_id) REFERENCES parse_tasks(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS frame_details (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    thread_id       BIGINT NOT NULL,
    frame_index     INT NOT NULL,
    raw_line        TEXT,
    address         VARCHAR(18),
    function_name   VARCHAR(500),
    source_file     VARCHAR(500),
    source_line     INT,
    offset_in_func  VARCHAR(50),
    args            TEXT,
    resolved        BOOLEAN DEFAULT FALSE,
    is_dpdk_func    BOOLEAN DEFAULT FALSE,
    confidence      INT DEFAULT 0,
    FOREIGN KEY (thread_id) REFERENCES thread_stacks(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS parse_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT NOT NULL,
    log_level   VARCHAR(10) DEFAULT 'INFO',
    stage       VARCHAR(50),
    message     TEXT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES parse_tasks(id) ON DELETE CASCADE
);
