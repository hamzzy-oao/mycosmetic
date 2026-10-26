-- 화면은 용량을 모르면 volume을 null로 보낸다(용량 입력은 선택). 빈 문자열 대신 NULL로 저장한다
ALTER TABLE products MODIFY volume VARCHAR(50) NULL;
