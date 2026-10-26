-- 루틴 플래너(기능 2, no-ai-features 설계 A). 🔒 사용자 데이터
-- days: 요일 집합 JSON 배열(예: ["MONDAY","FRIDAY"]). 빈 배열이면 매일
CREATE TABLE routines (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    slot       VARCHAR(10) NOT NULL,               -- MORNING / EVENING
    days       JSON        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_routines_user (user_id),
    CONSTRAINT fk_routines_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 루틴 단계. 내 화장대 항목을 지우면 그 항목이 들어간 단계도 함께 지워진다
CREATE TABLE routine_steps (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    routine_id    BIGINT      NOT NULL,
    shelf_item_id BIGINT      NOT NULL,
    step_type     VARCHAR(20) NOT NULL,            -- CLEANSER ... ETC
    position      INT         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_steps_routine (routine_id, position),
    CONSTRAINT fk_steps_routine FOREIGN KEY (routine_id) REFERENCES routines (id) ON DELETE CASCADE,
    CONSTRAINT fk_steps_shelf_item FOREIGN KEY (shelf_item_id) REFERENCES shelf_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
