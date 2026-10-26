#!/usr/bin/env bash
# EC2에서 실행하는 배포 스크립트. 사용법: deploy.sh <이미지태그>
#   IMAGE_REPO 환경변수로 이미지 저장소를 넘긴다(예: ghcr.io/<owner>/mycosmetic). 없으면 아래 기본값.
#
# 1. Parameter Store /mycosmetic/prod/ 의 값을 받아 /opt/mycosmetic/app.env(권한 600)에 쓴다
# 2. 지금 실행 중인 태그를 기억한다
# 3. 새 컨테이너를 실행한다(127.0.0.1:8080, 로그는 CloudWatch Logs /mycosmetic/app)
# 4. /actuator/health 를 5초 간격으로 최대 24번(2분) 확인한다
# 5. 실패하면 새 컨테이너를 지우고 이전 태그로 다시 실행한 뒤 exit 1
set -euo pipefail

TAG="${1:?사용법: deploy.sh <이미지태그>}"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/hamzzy-oao/mycosmetic}"
APP_DIR=/opt/mycosmetic
ENV_FILE="$APP_DIR/app.env"
TAG_FILE="$APP_DIR/current_tag"
CONTAINER=mycosmetic
SSM_PATH=/mycosmetic/prod/
LOG_GROUP=/mycosmetic/app
HEALTH_URL=http://127.0.0.1:8080/actuator/health

log() { echo "[deploy $(date '+%H:%M:%S')] $*"; }

# EC2 인스턴스 메타데이터(IMDSv2)에서 리전을 얻는다(awslogs·aws cli 공통)
if [ -z "${AWS_REGION:-}" ]; then
	IMDS_TOKEN=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
	AWS_REGION=$(curl -fsS -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" http://169.254.169.254/latest/meta-data/placement/region)
fi
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"

# 1. 비밀값 → app.env. 파라미터 이름의 마지막 부분이 환경변수 이름이다(/mycosmetic/prod/JWT_SECRET → JWT_SECRET)
log "Parameter Store $SSM_PATH 에서 환경변수를 받습니다"
umask 077
TMP_ENV=$(mktemp "$APP_DIR/app.env.XXXXXX")
aws ssm get-parameters-by-path --path "$SSM_PATH" --recursive --with-decryption \
	--query 'Parameters[*].[Name,Value]' --output text \
	| while IFS=$'\t' read -r name value; do
		printf '%s=%s\n' "${name##*/}" "$value"
	done > "$TMP_ENV"
if [ ! -s "$TMP_ENV" ]; then
	rm -f "$TMP_ENV"
	log "Parameter Store에서 값을 하나도 받지 못했습니다. IAM 역할과 경로를 확인하세요"
	exit 1
fi
chmod 600 "$TMP_ENV"
mv "$TMP_ENV" "$ENV_FILE"

# 2. 이전 태그
PREV_TAG=""
if [ -f "$TAG_FILE" ]; then
	PREV_TAG=$(cat "$TAG_FILE")
fi
log "이전 태그: ${PREV_TAG:-없음}, 새 태그: $TAG"

# 새 이미지를 먼저 받아 둔다(받기 실패면 기존 컨테이너를 건드리지 않는다)
docker pull "$IMAGE_REPO:$TAG"

run_container() {
	docker run -d --name "$CONTAINER" \
		--restart unless-stopped \
		--env-file "$ENV_FILE" \
		-p 127.0.0.1:8080:8080 \
		--log-driver awslogs \
		--log-opt awslogs-region="$AWS_REGION" \
		--log-opt awslogs-group="$LOG_GROUP" \
		--log-opt awslogs-stream="$CONTAINER-$1" \
		"$IMAGE_REPO:$1" > /dev/null
}

wait_healthy() {
	for i in $(seq 1 24); do
		if curl -fsS --max-time 4 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
			log "헬스체크 통과 (${i}번째 확인)"
			return 0
		fi
		sleep 5
	done
	return 1
}

# 3. 새 컨테이너 실행(같은 포트를 쓰므로 기존 컨테이너를 먼저 내린다)
docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
log "새 컨테이너 실행: $IMAGE_REPO:$TAG"
run_container "$TAG"

# 4. 헬스체크
if wait_healthy; then
	echo "$TAG" > "$TAG_FILE"
	# 지금 태그와 이전 태그를 뺀 옛 이미지는 지운다
	docker image ls "$IMAGE_REPO" --format '{{.Tag}}' \
		| grep -v -x -e "$TAG" -e "${PREV_TAG:-$TAG}" \
		| xargs -r -I{} docker image rm "$IMAGE_REPO:{}" > /dev/null 2>&1 || true
	log "배포 완료: $TAG"
	exit 0
fi

# 5. 되돌리기
log "헬스체크 실패. 새 컨테이너 로그는 CloudWatch Logs $LOG_GROUP / $CONTAINER-$TAG 에서 확인하세요"
docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
if [ -n "$PREV_TAG" ]; then
	log "이전 태그로 되돌립니다: $PREV_TAG"
	run_container "$PREV_TAG"
	if wait_healthy; then
		log "되돌리기 완료: $PREV_TAG"
	else
		log "이전 태그도 헬스체크에 실패했습니다. 직접 확인이 필요합니다"
	fi
else
	log "이전 태그가 없어 되돌릴 수 없습니다(첫 배포)"
fi
exit 1
