-- 설계 3장 데이터 모델. MySQL 8.0/8.4 공통 문법만 쓴다.
-- 시간은 DATETIME(6)에 UTC로, 날짜는 DATE로, enum은 VARCHAR(20)으로 저장한다.

-- ===== 회원 =====

CREATE TABLE users (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    email              VARCHAR(255) NULL,          -- 카카오 가입자는 없을 수 있다
    password_hash      VARCHAR(100) NULL,          -- BCrypt. 소셜 전용 계정은 없다
    nickname           VARCHAR(50)  NOT NULL,
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)              -- NULL은 여러 개 허용된다
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE social_accounts (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,        -- KAKAO
    provider_user_id VARCHAR(100) NOT NULL,
    email            VARCHAR(255) NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_social_provider_user (provider, provider_user_id),
    CONSTRAINT fk_social_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,               -- SHA-256 hex. 원문은 저장하지 않는다
    family_id  VARCHAR(36) NOT NULL,               -- 로테이션 계보(UUID)
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_family (family_id),            -- 재사용 감지 시 계보 전체 폐기
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== 공유 제품과 성분 =====

CREATE TABLE ingredients (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    standard_name VARCHAR(200) NOT NULL,           -- 식약처 표준명
    english_name  VARCHAR(300) NULL,
    cas_no        VARCHAR(100) NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingredients_standard_name (standard_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 표준명도 한 줄로 넣어서 매칭은 이 테이블만 본다
CREATE TABLE ingredient_aliases (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    ingredient_id    BIGINT       NOT NULL,
    alias            VARCHAR(300) NOT NULL,
    normalized_alias VARCHAR(300) COLLATE utf8mb4_bin NOT NULL,  -- 정확 일치 비교
    PRIMARY KEY (id),
    KEY idx_alias_normalized (normalized_alias),                 -- EXACT·ALIAS 조회
    UNIQUE KEY uk_alias_ingredient_normalized (ingredient_id, normalized_alias),  -- 사전 가져오기 중복 방지
    FULLTEXT INDEX ft_alias (normalized_alias) WITH PARSER ngram, -- FUZZY 후보 검색
    CONSTRAINT fk_alias_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    barcode    VARCHAR(50)  NULL,
    brand      VARCHAR(100) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    volume     VARCHAR(50)  NOT NULL,
    category   VARCHAR(20)  NOT NULL,              -- SKINCARE / MAKEUP
    created_by BIGINT       NULL,                  -- 탈퇴하면 NULL(익명)
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_barcode (barcode),
    CONSTRAINT fk_products_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 성분 버전. 추가만 하고 덮어쓰거나 지우지 않는다
CREATE TABLE ingredient_lists (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    product_id    BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,            -- PENDING / VERIFIED
    fingerprint   VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,  -- SHA-256 hex
    confirm_count INT         NOT NULL DEFAULT 0,
    created_by    BIGINT      NULL,                -- 탈퇴하면 NULL(익명)
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lists_product_fingerprint (product_id, fingerprint),
    CONSTRAINT fk_lists_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_lists_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ingredient_list_items (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    list_id       BIGINT       NOT NULL,
    position      INT          NOT NULL,           -- 전성분 표기 순서
    ingredient_id BIGINT       NULL,               -- UNMATCHED면 NULL
    raw_text      VARCHAR(500) NOT NULL,
    match_type    VARCHAR(20)  NOT NULL,           -- EXACT / ALIAS / FUZZY / UNMATCHED
    PRIMARY KEY (id),
    UNIQUE KEY uk_items_list_position (list_id, position),
    CONSTRAINT fk_items_list FOREIGN KEY (list_id) REFERENCES ingredient_lists (id),
    CONSTRAINT fk_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE list_confirmations (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    list_id    BIGINT      NOT NULL,
    user_id    BIGINT      NULL,                   -- 탈퇴하면 NULL(익명), confirm_count는 그대로
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_confirmations_list_user (list_id, user_id),
    CONSTRAINT fk_confirmations_list FOREIGN KEY (list_id) REFERENCES ingredient_lists (id),
    CONSTRAINT fk_confirmations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== 내 정보 =====

-- 내가 확인한 성분 버전에 고정된다
CREATE TABLE shelf_items (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    product_id         BIGINT        NOT NULL,
    ingredient_list_id BIGINT        NOT NULL,
    verdict            VARCHAR(20)   NOT NULL,     -- GOOD / BAD / NEUTRAL / UNRATED
    memo               VARCHAR(1000) NULL,
    started_on         DATE          NULL,
    ended_on           DATE          NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shelf_user_list (user_id, ingredient_list_id),
    CONSTRAINT fk_shelf_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_shelf_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_shelf_list FOREIGN KEY (ingredient_list_id) REFERENCES ingredient_lists (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE skin_profiles (
    user_id    BIGINT      NOT NULL,
    skin_types JSON        NOT NULL,               -- 예: ["SENSITIVE","DRY"]
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_skin_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 사진은 저장하지 않는다
CREATE TABLE extraction_jobs (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    user_id             BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL,      -- QUEUED / RUNNING / DONE / FAILED
    result_json         JSON        NULL,
    error_code          VARCHAR(50) NULL,
    counts_toward_limit BOOLEAN     NOT NULL,      -- 하루 한도 차감 여부
    created_at          DATETIME(6) NOT NULL,
    finished_at         DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_jobs_user_created (user_id, created_at),  -- 하루 한도 계산
    CONSTRAINT fk_jobs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
