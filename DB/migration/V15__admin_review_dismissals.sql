-- 관리자 점검함(설계 2026-10-24 점검함)에서 "괜찮음"으로 넘긴 문제. 🌐 공유 제품 데이터에 대한 관리자 기록
-- signature: 넘길 때의 값(예: 제품명). 값이 바뀌면 다시 점검함에 뜬다
CREATE TABLE admin_review_dismissals (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_id   BIGINT       NOT NULL,
    issue_type   VARCHAR(30)  NOT NULL,
    signature    VARCHAR(300) NOT NULL,
    dismissed_by BIGINT       NULL,
    dismissed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_review_dismissals (product_id, issue_type),
    CONSTRAINT fk_admin_review_dismissals_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_review_dismissals_user FOREIGN KEY (dismissed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
