-- 작업 스레드가 job을 RUNNING으로 바꾼 시각. 정리 스케줄러가 "5분 넘게 RUNNING"인 고아 job을
-- 생성 시각이 아니라 실제 시작 시각으로 판단한다(대기열 50개 때문에 QUEUED로 오래 기다린 job을 잘못 실패시키지 않도록)
ALTER TABLE extraction_jobs ADD COLUMN started_at DATETIME(6) NULL;
