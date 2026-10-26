-- 로컬 MySQL(MySQL80)에 앱용·테스트용 DB와 mycosmetic 계정을 만든다.
-- 직접 실행하지 말고 setup-local-db.ps1로 실행한다(.env의 MYSQL_PASSWORD를 넣어 준다).
-- 여러 번 실행해도 된다. 비밀번호는 .env 값으로 다시 맞춘다.
CREATE DATABASE IF NOT EXISTS mycosmetic CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mycosmetic_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'mycosmetic'@'localhost' IDENTIFIED BY '__MYSQL_PASSWORD__';
ALTER USER 'mycosmetic'@'localhost' IDENTIFIED BY '__MYSQL_PASSWORD__';
GRANT ALL PRIVILEGES ON mycosmetic.* TO 'mycosmetic'@'localhost';
GRANT ALL PRIVILEGES ON mycosmetic_test.* TO 'mycosmetic'@'localhost';
SHOW DATABASES LIKE 'mycosmetic%';
