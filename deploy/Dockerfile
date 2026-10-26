# mycosmetic 운영 이미지. React 빌드와 Flyway SQL(DB/migration/)이 이미 들어 있는 mycosmetic.jar 하나만 담는다.
# 빌드 컨텍스트는 JAR가 있는 폴더다:
#   cd backend && ./gradlew bootJar && cd ..
#   docker build -f deploy/Dockerfile -t mycosmetic:local backend/build/libs
FROM eclipse-temurin:21-jre

# 비루트 사용자로 실행한다
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --chown=app:app mycosmetic.jar /app/mycosmetic.jar

USER app
EXPOSE 8080
# 운영 로그는 JSON(logstash 형식): traceId와 요청 로그 필드가 CloudWatch에서 검색된다
ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/mycosmetic.jar"]
