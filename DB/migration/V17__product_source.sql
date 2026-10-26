-- 제품 정보의 출처와 관리자 검수 여부(2026-10-26 제품명 예외처리). 🌐 공유 제품 데이터
-- source: SEED(공식몰 시드) / LINK(올리브영 링크 이름 그대로) / USER(사용자가 직접 입력)
-- verified: 관리자가 확인했거나 믿을 수 있는 출처(SEED·LINK). USER는 FALSE로 시작해 점검함 "새로 등록된 제품"에 뜬다
ALTER TABLE products
    ADD COLUMN source   VARCHAR(10) NOT NULL DEFAULT 'USER' AFTER category,
    ADD COLUMN verified BOOLEAN     NOT NULL DEFAULT FALSE AFTER source;

-- 지금까지 등록한 사람이 없는 제품은 시드로 넣은 것이다(탈퇴한 사용자의 제품도 여기에 섞일 수 있다)
UPDATE products SET source = 'SEED', verified = TRUE WHERE created_by IS NULL;

CREATE INDEX idx_products_verified ON products (verified, archived);
