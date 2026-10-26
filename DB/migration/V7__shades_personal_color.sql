-- 색상과 내 톤(기능 6, no-ai-features 설계 E). 🔒 사용자 데이터
-- shade_name·shade_hex: 내 항목에 저장한다(같은 제품도 사람마다 호수가 다르다). shade_hex는 #RRGGBB(대문자로 저장)
-- personal_color: SPRING_WARM, SUMMER_COOL, AUTUMN_WARM, WINTER_COOL. NULL = 선택 안 함
ALTER TABLE shelf_items
    ADD COLUMN shade_name VARCHAR(50) NULL AFTER pao_months,
    ADD COLUMN shade_hex  CHAR(7)     NULL AFTER shade_name;

ALTER TABLE skin_profiles
    ADD COLUMN personal_color VARCHAR(20) NULL AFTER skin_types;
