-- 추출 결과를 확인·저장한 시각. FAILED job(직접 붙여넣기)은 result_json이 원래 비어 있어서
-- "이미 확인했는지"를 result_json만으로 알 수 없다. 같은 job으로 두 번 저장하지 못하게 한다
ALTER TABLE extraction_jobs ADD COLUMN confirmed_at DATETIME(6) NULL;
