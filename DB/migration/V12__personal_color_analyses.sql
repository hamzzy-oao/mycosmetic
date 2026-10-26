-- 퍼스널컬러 분석 이력(퍼스널 탭 설계 2절). 사진은 저장하지 않고 브라우저가 뽑은 색값에서 계산한 결과만 남긴다
-- method: CAMERA | MANUAL. MANUAL이면 색 관련 값은 NULL
CREATE TABLE personal_color_analyses (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    method       VARCHAR(10) NOT NULL,
    lab_l        DOUBLE      NULL,
    lab_a        DOUBLE      NULL,
    lab_b        DOUBLE      NULL,
    ita          DOUBLE      NULL,
    hue          DOUBLE      NULL,
    confidence   DOUBLE      NULL,
    quality      JSON        NULL,   -- {passed, issues, score}
    candidates   JSON        NULL,   -- 예: ["SUMMER_COOL","SPRING_WARM"]
    chosen       VARCHAR(20) NOT NULL,
    agreed       BOOLEAN     NULL,   -- 카메라 1순위 후보와 사용자가 고른 것이 같은가
    rule_version VARCHAR(20) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pca_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_pca_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
