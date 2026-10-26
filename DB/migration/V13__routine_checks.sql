-- 루틴 탭 개편: 화장대 진단에서 저장한 루틴 표시, 오늘 사용 체크. 🔒 사용자 데이터
-- from_diagnosis: 화장대 진단의 "루틴으로 저장"으로 만든 루틴. 다시 저장하면 이 루틴들을 새 초안으로 바꾼다
ALTER TABLE routines
    ADD COLUMN from_diagnosis BOOLEAN NOT NULL DEFAULT FALSE AFTER days;

-- 하루 한 번, 시간대별로 "이 제품을 썼다" 기록. 루틴을 지우거나 다시 저장해도 기록은 남도록 루틴이 아니라 화장대 항목에 묶는다
-- 꾸준히 쓴 제품의 잘맞아요를 더 믿을 수 있게 하는 근거(v-8)
CREATE TABLE routine_checks (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    shelf_item_id BIGINT      NOT NULL,
    used_on       DATE        NOT NULL,            -- Asia/Seoul 날짜
    slot          VARCHAR(10) NOT NULL,            -- MORNING / EVENING
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_routine_checks (shelf_item_id, used_on, slot),
    KEY idx_routine_checks_user (user_id, used_on),
    CONSTRAINT fk_routine_checks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_routine_checks_shelf_item FOREIGN KEY (shelf_item_id) REFERENCES shelf_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
