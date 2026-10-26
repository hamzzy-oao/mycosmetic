-- E2E 전용 성분 사전 시드(e2e 프로필에서만 spring.flyway.locations에 classpath:e2e가 더해진다).
-- 가짜 추출 결과(fake/extraction-sample.json: 정제수, 글리세린, 나이아신아마이드, 부틸렌글라이콜, 판테놀)가
-- 매칭되면 EXACT·FUZZY·UNMATCHED가 모두 나오도록 일부만 넣는다.
--   정제수, 글리세린, 나이아신아마이드 → EXACT (표준명과 같음)
--   부틸렌글라이콜 → FUZZY (사전에는 '뷰틸렌글라이콜'만 있음. 편집거리 1/7 → 유사도 0.857 ≥ 0.8)
--   판테놀 → UNMATCHED (사전에 없음)
-- 표준명도 ingredient_aliases에 한 줄로 넣는다(매칭은 이 테이블만 본다). normalized_alias는 IngredientNameNormalizer 결과.
INSERT INTO ingredients (id, standard_name, english_name, cas_no, updated_at) VALUES
    (900001, '정제수', 'Water', '7732-18-5', UTC_TIMESTAMP(6)),
    (900002, '글리세린', 'Glycerin', '56-81-5', UTC_TIMESTAMP(6)),
    (900003, '나이아신아마이드', 'Niacinamide', '98-92-0', UTC_TIMESTAMP(6)),
    (900004, '뷰틸렌글라이콜', 'Butylene Glycol', '107-88-0', UTC_TIMESTAMP(6));

INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES
    (900001, '정제수', '정제수'),
    (900001, 'Water', 'water'),
    (900002, '글리세린', '글리세린'),
    (900003, '나이아신아마이드', '나이아신아마이드'),
    (900004, '뷰틸렌글라이콜', '뷰틸렌글라이콜');
