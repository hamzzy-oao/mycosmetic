-- 피부 분석(퍼스널 탭 설계 1절)
-- concerns: 피부 고민 0~3개. 예: ["DRYNESS","PORES"]
ALTER TABLE skin_profiles ADD COLUMN concerns JSON NULL AFTER skin_types;
UPDATE skin_profiles SET concerns = JSON_ARRAY();
ALTER TABLE skin_profiles MODIFY concerns JSON NOT NULL;

-- 기본 타입을 하나로: DRY와 OILY가 함께 있으면 COMBINATION을 더하고,
-- COMBINATION이 있는 행에서는 DRY·OILY를 뺀다(민감성·트러블성 태그는 그대로)
UPDATE skin_profiles
SET skin_types = JSON_ARRAY_APPEND(skin_types, '$', 'COMBINATION')
WHERE JSON_CONTAINS(skin_types, '"DRY"') AND JSON_CONTAINS(skin_types, '"OILY"')
  AND NOT JSON_CONTAINS(skin_types, '"COMBINATION"');
UPDATE skin_profiles
SET skin_types = JSON_REMOVE(skin_types, JSON_UNQUOTE(JSON_SEARCH(skin_types, 'one', 'DRY')))
WHERE JSON_CONTAINS(skin_types, '"COMBINATION"') AND JSON_CONTAINS(skin_types, '"DRY"');
UPDATE skin_profiles
SET skin_types = JSON_REMOVE(skin_types, JSON_UNQUOTE(JSON_SEARCH(skin_types, 'one', 'OILY')))
WHERE JSON_CONTAINS(skin_types, '"COMBINATION"') AND JSON_CONTAINS(skin_types, '"OILY"');

-- 분석 이력. 탈퇴하면 사용자 행과 함께 지워진다
CREATE TABLE skin_analyses (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    answers      JSON        NOT NULL,   -- {selfType, middayFeel, concerns}
    result_types JSON        NOT NULL,   -- 예: ["SENSITIVE","COMBINATION"]
    concerns     JSON        NOT NULL,
    rule_version VARCHAR(20) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_skin_analyses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_skin_analyses_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
