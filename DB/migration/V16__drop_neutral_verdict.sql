-- 평가에서 '보통'(NEUTRAL)을 없앤다(2026-10-24 사용자 요청). 이미 '보통'으로 둔 항목은 '평가 전'으로 돌린다.
-- 맞음·안 맞음만 성분 분석에 쓰이므로 결과는 달라지지 않는다. 🔒 사용자 데이터
UPDATE shelf_items SET verdict = 'UNRATED' WHERE verdict = 'NEUTRAL';
