-- 사용 기록 활용(기능 5, no-ai-features 설계 D). 🔒 사용자 데이터
-- opened_on: 개봉일, pao_months: 개봉 후 사용기한(개월, 1~60). 유통기한 = opened_on + pao_months개월(코드에서 계산)
ALTER TABLE shelf_items
    ADD COLUMN opened_on  DATE NULL AFTER ended_on,
    ADD COLUMN pao_months INT  NULL AFTER opened_on;
