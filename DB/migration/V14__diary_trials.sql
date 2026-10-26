-- 피부 일기와 새 제품 2주 테스트(설계 2026-10-24 A·B). 🔒 사용자 데이터
-- 탈퇴하면 users 외래 키의 ON DELETE CASCADE로 같이 지워진다

-- 하루 한 줄. entry_date는 Asia/Seoul 날짜. tags는 JSON 배열(예: ["TROUBLE","DRYNESS"])
CREATE TABLE skin_diary_entries (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    entry_date DATE         NOT NULL,
    mood       VARCHAR(10)  NOT NULL,            -- GOOD / OK / BAD
    tags       JSON         NOT NULL,
    memo       VARCHAR(200) NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skin_diary_entries (user_id, entry_date),
    CONSTRAINT fk_skin_diary_entries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 화장대 항목 하나에 테스트 하나. 기간 동안의 기록은 피부 일기를 센다. finished_at이 있으면 평가를 마친 것
CREATE TABLE product_trials (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    shelf_item_id BIGINT      NOT NULL,
    started_on    DATE        NOT NULL,
    ends_on       DATE        NOT NULL,
    finished_at   DATETIME(6) NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_trials_shelf_item (shelf_item_id),
    KEY idx_product_trials_user (user_id),
    CONSTRAINT fk_product_trials_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_trials_shelf_item FOREIGN KEY (shelf_item_id) REFERENCES shelf_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
