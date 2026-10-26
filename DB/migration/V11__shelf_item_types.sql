-- 화장대 분석용 제품 종류(퍼스널 탭 설계 3절). 내 항목에 저장한다(같은 제품도 쓰는 단계가 사람마다 다를 수 있다)
-- step_type: 스킨케어 단계(StepType), makeup_type: LIP | CHEEK | EYE | BASE. NULL = 모름(제품 이름으로 추정)
ALTER TABLE shelf_items
    ADD COLUMN step_type   VARCHAR(20) NULL AFTER shade_hex,
    ADD COLUMN makeup_type VARCHAR(10) NULL AFTER step_type;
