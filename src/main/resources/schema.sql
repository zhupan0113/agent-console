CREATE TABLE IF NOT EXISTS agent_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NULL,
    kind VARCHAR(32) NULL,
    scope VARCHAR(32) NULL,
    cwd VARCHAR(512) NULL,
    project VARCHAR(255) NULL,
    model VARCHAR(128) NULL,
    provider VARCHAR(64) NULL,
    role VARCHAR(64) NULL,
    status VARCHAR(32) NULL,
    archived TINYINT(1) DEFAULT 0,
    tokens_used BIGINT DEFAULT 0,
    cost_amount DECIMAL(14,4) DEFAULT 0,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    raw_json TEXT NULL,
    UNIQUE KEY uk_agent (platform, external_id),
    KEY idx_agent_project (project)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    external_id VARCHAR(191) NOT NULL,
    thread_id VARCHAR(64) NULL,
    turn_id VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    provider VARCHAR(64) NULL,
    project VARCHAR(255) NULL,
    input_tokens BIGINT DEFAULT 0,
    cached_tokens BIGINT DEFAULT 0,
    cache_write_tokens BIGINT DEFAULT 0,
    output_tokens BIGINT DEFAULT 0,
    reasoning_tokens BIGINT DEFAULT 0,
    total_tokens BIGINT DEFAULT 0,
    credits DECIMAL(14,4) NULL,
    cost_amount DECIMAL(14,4) DEFAULT 0,
    currency VARCHAR(8) DEFAULT 'CNY',
    source VARCHAR(32) NULL,
    confidence VARCHAR(16) NULL,
    price_missing TINYINT(1) DEFAULT 0,
    occurred_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usage (platform, external_id),
    KEY idx_usage_time (occurred_at),
    KEY idx_usage_model (model),
    KEY idx_usage_project (project)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    input_price DECIMAL(14,6) DEFAULT 0,
    cached_price DECIMAL(14,6) DEFAULT 0,
    cache_write_price DECIMAL(14,6) DEFAULT 0,
    output_price DECIMAL(14,6) DEFAULT 0,
    currency VARCHAR(8) DEFAULT 'CNY',
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    remark VARCHAR(255) NULL,
    UNIQUE KEY uk_price (provider, model, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    name VARCHAR(255) NULL,
    description TEXT NULL,
    path VARCHAR(700) NULL,
    path_hash CHAR(64) NOT NULL,
    checksum CHAR(64) NULL,
    enabled TINYINT(1) DEFAULT 1,
    source_type VARCHAR(32) NULL,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_skill (platform, path_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mcp_server (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    transport VARCHAR(32) NULL,
    command VARCHAR(700) NULL,
    args_json TEXT NULL,
    env_json TEXT NULL,
    url VARCHAR(700) NULL,
    enabled TINYINT(1) DEFAULT 1,
    health_status VARCHAR(32) DEFAULT 'unknown',
    config_path VARCHAR(700) NULL,
    path_hash CHAR(64) NOT NULL,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_mcp (platform, path_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mcp_tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    server_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    schema_json TEXT NULL,
    discovered_at DATETIME NULL,
    UNIQUE KEY uk_tool (server_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scan_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NULL,
    trigger_type VARCHAR(16) NULL,
    status VARCHAR(16) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    item_count INT DEFAULT 0,
    message VARCHAR(1000) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scan_cursor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_key VARCHAR(255) NOT NULL,
    file_path VARCHAR(700) NULL,
    file_size BIGINT DEFAULT 0,
    byte_offset BIGINT DEFAULT 0,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_cursor (source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS usage_import (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NULL,
    file_hash CHAR(64) NULL,
    rows_count INT DEFAULT 0,
    credits DECIMAL(14,4) DEFAULT 0,
    status VARCHAR(16) NULL,
    message VARCHAR(1000) NULL,
    imported_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_setting (
    k VARCHAR(128) PRIMARY KEY,
    v VARCHAR(1000) NULL,
    updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO app_setting (k, v, updated_at) VALUES ('trae.credit.cny', '0', NOW());
INSERT IGNORE INTO app_setting (k, v, updated_at) VALUES ('trae.usage.xlsx', '', NOW());
INSERT IGNORE INTO app_setting (k, v, updated_at) VALUES ('scan.auto', 'true', NOW());
