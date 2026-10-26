# AWS 배포 절차 (EC2 한 대 + RDS + Nginx + Let's Encrypt)

설계 1장 배포, 2.3, 6.5. 위에서부터 순서대로 따라 한다. 각 단계 끝의 **확인 방법**이 통과해야 다음 단계로 간다.
리전은 서울(`ap-northeast-2`)을 기준으로 적었다. `<도메인>`, `<계정ID>`, `<owner>`는 자기 값으로 바꾼다.

전체 흐름: GitHub `main` 합치기 → Actions가 테스트·JAR 빌드 → Docker 이미지를 GHCR(`ghcr.io/<owner>/mycosmetic:<커밋sha>`)에 올림 →
EC2에 SSH로 `deploy.sh <sha>` 실행 → 헬스체크 실패 시 이전 이미지로 되돌림.

---

## 1. 첫날: 결제 알림 (Billing Alarm)

1. 루트 계정으로 로그인 → **Billing and Cost Management → Budgets → Create budget** → "Monthly cost budget".
2. 예산 금액을 정한다(예: 월 30달러. EC2 t3.small + RDS db.t4g.micro + 탄력적 IP + CloudWatch 기준 대략 20~30달러).
3. 알림 이메일을 넣고 실제 금액 80%, 예측 금액 100%에서 알림을 받게 한다.
4. 루트 계정에 MFA를 켜고, 이후 작업은 관리자 IAM 사용자(또는 IAM Identity Center)로 한다.

**확인 방법**: Budgets 목록에 예산이 보이고 상태가 "OK"다.

## 2. IAM

### 2-1. EC2 인스턴스 역할 `mycosmetic-ec2`

**IAM → Roles → Create role → AWS service: EC2**. 아래 인라인 정책을 붙인다(`AmazonSSMReadOnlyAccess` 대신 `/mycosmetic/prod/*`로 범위를 좁힌 정책).

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadProdParameters",
      "Effect": "Allow",
      "Action": ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"],
      "Resource": [
        "arn:aws:ssm:ap-northeast-2:<계정ID>:parameter/mycosmetic/prod",
        "arn:aws:ssm:ap-northeast-2:<계정ID>:parameter/mycosmetic/prod/*"
      ]
    },
    {
      "Sid": "DecryptSecureString",
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "*",
      "Condition": { "StringEquals": { "kms:ViaService": "ssm.ap-northeast-2.amazonaws.com" } }
    },
    {
      "Sid": "WriteAppLogs",
      "Effect": "Allow",
      "Action": ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"],
      "Resource": "arn:aws:logs:ap-northeast-2:<계정ID>:log-group:/mycosmetic/app:*"
    }
  ]
}
```

**확인 방법**: 5단계 이후 EC2에서 `aws ssm get-parameters-by-path --path /mycosmetic/prod/ --with-decryption --query 'Parameters[*].Name'`이 이름 목록을 돌려준다.

### 2-2. GitHub Actions 배포 권한

배포 워크플로(`.github/workflows/deploy.yml`)는 AWS API를 부르지 않는다. 이미지는 GHCR에 올리고(그 실행의 `GITHUB_TOKEN` 사용), EC2에는 SSH로 들어가 `deploy.sh`를 실행한다. 그래서 **AWS IAM 배포 사용자·OIDC 역할은 만들지 않는다.** 필요한 것은 EC2 접속용 SSH 키뿐이다.

1. 배포 전용 키를 만든다(내 PC): `ssh-keygen -t ed25519 -f mycosmetic-deploy -C github-actions -N ""`
2. 공개키(`mycosmetic-deploy.pub`)는 4단계에서 EC2의 `~ec2-user/.ssh/authorized_keys`에 추가한다.
3. GitHub 저장소 **Settings → Secrets and variables → Actions**에 등록:

| 이름 | 값 |
|---|---|
| `EC2_HOST` | EC2 탄력적 IP 또는 도메인 |
| `EC2_SSH_KEY` | `mycosmetic-deploy` 개인키 파일 내용 전체 |
| `EC2_KNOWN_HOSTS` | 내 PC에서 `ssh-keyscan -t ed25519 <탄력적IP>` 출력 한 줄(서버 위장 방지) |
| `EC2_USER` (선택) | 없으면 `ec2-user` |

4. **Settings → Environments**에서 `production` 환경을 만들고 **Deployment branches and tags**를 **Selected branches and tags → `main`만** 으로 제한한다. 워크플로의 `deploy` 작업에도 `if: github.ref == 'refs/heads/main'`이 있지만, 환경 규칙이 있어야 워크플로 파일을 고친 다른 브랜치에서도 배포 비밀값과 배포를 쓸 수 없다. 위 비밀값을 이 환경의 Environment secrets로 옮기면 더 좋다.
5. TODO: 워크플로의 액션(`actions/checkout@v4`, `docker/build-push-action@v6` 등)은 아직 태그로 불러온다. 태그는 바뀔 수 있으므로 각 액션을 커밋 SHA로 고정하고(`uses: actions/checkout@<40자리 SHA> # v4.x.x`), `.github/dependabot.yml`에 `package-ecosystem: github-actions`를 추가해 Dependabot이 SHA를 올려 주게 한다.

**확인 방법**: Secrets 목록에 `EC2_HOST`, `EC2_SSH_KEY`, `EC2_KNOWN_HOSTS`가 보인다.

> SSH 22번은 4단계에서 "내 IP"만 연다. GitHub Actions 러너 IP는 매번 바뀌므로, 배포가 SSH 연결 시간 초과로 실패하면 22번을 `0.0.0.0/0`으로 열되 **키 인증만 허용**(Amazon Linux 기본값: 비밀번호 로그인 꺼짐)한 상태로 둔다. 더 좁히려면 나중에 SSM Run Command 방식으로 바꾼다.

## 3. RDS MySQL 8.4

1. **VPC → Security groups**에서 먼저 두 개를 만든다.
   - `mycosmetic-ec2-sg`: 규칙은 4단계에서
   - `mycosmetic-rds-sg`: 인바운드 MySQL/Aurora 3306, 소스 = `mycosmetic-ec2-sg`(보안 그룹 ID). 다른 인바운드 없음.
2. **RDS → Parameter groups → Create**: 패밀리 `mysql8.4`, 이름 `mycosmetic-mysql84`. `character_set_server=utf8mb4`, `collation_server=utf8mb4_0900_ai_ci`, `time_zone=Asia/Seoul`로 바꾼다(기본 그룹은 수정할 수 없다).
3. **RDS → Create database** → Standard create → MySQL → 엔진 버전 **8.4.x**.
   - DB 인스턴스 식별자 `mycosmetic`, 마스터 사용자 `admin`, 비밀번호는 비밀번호 관리자에 저장
   - 인스턴스 **db.t4g.micro**, 스토리지 gp3 20GB
   - **퍼블릭 액세스: 아니요**, VPC 보안 그룹 `mycosmetic-rds-sg`만
   - 추가 구성 → 초기 데이터베이스 이름 `mycosmetic`, DB 파라미터 그룹 `mycosmetic-mysql84`, 자동 백업 7일
4. 만들어지면 엔드포인트(`mycosmetic.xxxx.ap-northeast-2.rds.amazonaws.com`)를 적어 둔다.
5. 4단계 EC2에서 앱 전용 계정을 만든다(마스터 계정은 앱에 쓰지 않는다):

```sql
-- EC2에서: sudo dnf install -y mariadb105 && mysql -h <RDS엔드포인트> -u admin -p
CREATE USER 'mycosmetic'@'%' IDENTIFIED BY '<앱 비밀번호>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON mycosmetic.* TO 'mycosmetic'@'%';
```

테이블은 앱이 처음 뜰 때 Flyway가 만든다(`DB/migration/`의 SQL이 JAR 안 `migration/`에 들어 있다). 운영에서는 Flyway `clean`이 꺼져 있다(Spring Boot 기본값).

**확인 방법**: EC2에서 `mysql -h <RDS엔드포인트> -u mycosmetic -p -e "SELECT 1"`이 `1`을 출력하고, 내 PC에서는 같은 명령이 연결되지 않는다(공개 접근 꺼짐).

## 4. EC2 (Amazon Linux 2023, t3.small)

1. **EC2 → Launch instance**: 이름 `mycosmetic`, AMI **Amazon Linux 2023**, 유형 **t3.small**, 키 페어(내 접속용), 스토리지 gp3 20GB.
   - 보안 그룹 `mycosmetic-ec2-sg` 인바운드: **22 = 내 IP**, **80 = 0.0.0.0/0**, **443 = 0.0.0.0/0**
   - 고급 → IAM 인스턴스 프로파일 = `mycosmetic-ec2`, 메타데이터 버전 **V2 only**
2. **탄력적 IP**를 할당해 인스턴스에 연결한다.
3. 접속(`ssh -i 내키.pem ec2-user@<탄력적IP>`) 후:

```bash
sudo dnf update -y
sudo dnf install -y docker nginx
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user          # 다시 로그인해야 적용
sudo mkdir -p /opt/mycosmetic && sudo chown ec2-user:ec2-user /opt/mycosmetic && chmod 700 /opt/mycosmetic
# 2-2에서 만든 배포용 공개키 추가
echo "<mycosmetic-deploy.pub 내용>" >> ~/.ssh/authorized_keys
# 스왑 1GB(t3.small 메모리 2GB 보완)
sudo dd if=/dev/zero of=/swapfile bs=1M count=1024 && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

`aws` CLI와 `curl`은 Amazon Linux 2023에 기본으로 있다.

**확인 방법**: 다시 로그인한 뒤 `docker run --rm hello-world`가 성공하고, `aws sts get-caller-identity`의 Arn에 `assumed-role/mycosmetic-ec2`가 보인다.

## 5. Parameter Store (SecureString)

**Systems Manager → Parameter Store → Create parameter**. 이름은 `/mycosmetic/prod/<환경변수 이름>`, 유형 **SecureString**(KMS 키 `alias/aws/ssm`). `deploy.sh`가 이름의 마지막 부분을 환경변수 이름으로 써서 `/opt/mycosmetic/app.env`(권한 600)를 만든다.

| 파라미터 이름 | 값 |
|---|---|
| `/mycosmetic/prod/SPRING_DATASOURCE_URL` | `jdbc:mysql://<RDS엔드포인트>:3306/mycosmetic?serverTimezone=Asia/Seoul` |
| `/mycosmetic/prod/SPRING_DATASOURCE_USERNAME` | `mycosmetic` |
| `/mycosmetic/prod/MYSQL_PASSWORD` | 3단계 앱 계정 비밀번호 |
| `/mycosmetic/prod/JWT_SECRET` | `openssl rand -base64 48` 출력(디코딩 후 32바이트 이상) |
| `/mycosmetic/prod/ANTHROPIC_API_KEY` | Anthropic 콘솔의 API 키 |
| `/mycosmetic/prod/KAKAO_CLIENT_ID` | 카카오 REST API 키 |
| `/mycosmetic/prod/KAKAO_CLIENT_SECRET` | 카카오 Client Secret |
| `/mycosmetic/prod/MFDS_SERVICE_KEY` | 공공데이터포털 식약처 서비스 키(Decoding) |

- 운영 설정 파일(`application-prod.yml`)은 만들지 않는다. 운영 값은 모두 위 환경변수로 들어가고 나머지는 `application.yml` 기본값을 쓴다(쿠키 `Secure` 켬, Flyway clean 꺼짐).
- `APP_FRONTEND_BASE_URL`(= `app.frontend-base-url`)은 운영에서 **빈 값**(같은 출처)이어야 한다. Parameter Store는 빈 값을 저장할 수 없고 앱 기본값이 이미 빈 값이므로 **등록하지 않는다.**
- 값은 한 줄이어야 한다(탭·줄바꿈 금지, 따옴표로 감싸지 않는다. Docker `--env-file`은 따옴표를 값의 일부로 읽는다).
- 값을 바꾼 뒤에는 다시 배포해야 반영된다(GitHub Actions에서 Deploy 워크플로를 "Re-run" 하거나 EC2에서 `IMAGE_REPO=ghcr.io/<owner>/mycosmetic bash /opt/mycosmetic/deploy.sh $(cat /opt/mycosmetic/current_tag)`).

**확인 방법**: EC2에서 `aws ssm get-parameters-by-path --path /mycosmetic/prod/ --query 'length(Parameters)'`가 `8`이다.

## 6. 도메인, Nginx, Let's Encrypt

1. 도메인 DNS에 A 레코드 `<도메인>` → EC2 탄력적 IP. (Route 53을 쓰면 호스팅 영역에서 추가)
2. 인증서 발급(80번이 비어 있어야 하므로 Nginx를 켜기 전에):

```bash
sudo dnf install -y certbot
sudo certbot certonly --standalone -d <도메인> -m <내 이메일> --agree-tos -n \
  --pre-hook "systemctl stop nginx" --post-hook "systemctl start nginx"
sudo systemctl enable --now certbot-renew.timer   # 자동 갱신(같은 hook으로 잠깐 Nginx를 멈춘다)
```

3. Nginx 설정(`deploy/nginx.conf`: 80 → https 이동, 443 → `127.0.0.1:8080`, `X-Forwarded-Proto` 전달, `client_max_body_size 6m`):

```bash
# 내 PC에서: scp -i 내키.pem deploy/nginx.conf ec2-user@<탄력적IP>:/tmp/
sed 's/YOUR_DOMAIN/<도메인>/g' /tmp/nginx.conf | sudo tee /etc/nginx/conf.d/mycosmetic.conf
sudo nginx -t && sudo systemctl enable --now nginx
```

**확인 방법**: `sudo nginx -t`가 `syntax is ok`, `sudo certbot renew --dry-run`이 성공, 브라우저에서 `http://<도메인>`이 `https://`로 바뀐다(앱 배포 전이라 502는 정상).

## 7. 카카오 Redirect URI

카카오 디벨로퍼스 → 내 애플리케이션 → 카카오 로그인 → **Redirect URI**에 `https://<도메인>/login/oauth2/code/kakao`를 추가한다. 플랫폼 → Web 사이트 도메인에 `https://<도메인>`도 추가한다.

**확인 방법**: 첫 배포 뒤 `https://<도메인>/login`에서 카카오 로그인을 누르면 카카오 동의 화면이 뜨고, 로그인 후 `/shelf`로 돌아온다(KOE006 오류면 Redirect URI가 틀린 것).

## 8. CloudWatch 로그·경보

1. **CloudWatch → Log groups → Create**: 이름 `/mycosmetic/app`, 보존 기간 30일. `deploy.sh`가 Docker `awslogs` 드라이버로 여기에 보낸다(그룹을 자동으로 만들지 않으므로 먼저 만든다).
2. 로그 그룹 → **Metric filters → Create**: 패턴 `{ $.status >= 500 }`, 지표 네임스페이스 `mycosmetic`, 지표 이름 `Http5xx`, 값 `1`, 기본값 `0`. 앱 로그는 JSON(logstash 형식)이고 요청 로그에 `status` 필드가 있다.
3. 지표 `mycosmetic/Http5xx` → **Create alarm**: 통계 **합계(Sum)**, 기간 **5분**, 조건 **> 10**, 누락 데이터는 "정상"으로 처리 → SNS 주제를 새로 만들어 내 이메일 → 이메일의 구독 확인 링크를 누른다.

**확인 방법**: 첫 배포 후 `/mycosmetic/app`에 `mycosmetic-<sha>` 스트림이 생기고 JSON 로그가 보인다. 경보 상태가 "OK"다(처음엔 데이터 부족, 몇 분 뒤 OK).

## 9. 첫 배포와 폰 확인

1. GitHub 저장소 **Settings → Branches → main 보호 규칙**: "Require status checks to pass" → CI의 `backend`, `frontend` 작업을 필수로 지정한다(보안·동시성 테스트가 실패하면 합칠 수 없다).
2. GHCR 패키지가 비공개여도 된다. 배포 워크플로가 SSH로 들어가 그 실행의 `GITHUB_TOKEN`으로 `docker login ghcr.io`를 한다.
3. PR을 `main`에 합치면 Actions의 **Deploy** 워크플로가 돈다. 실패하면 로그에서 `[deploy ...]` 줄을 본다.
4. 폰(사파리/크롬)으로 `https://<도메인>` 접속 → 가입 → 제품 추가 → **바코드 카메라 스캔**이 동작하는지 확인한다(카메라는 https에서만 열린다).

**확인 방법**: `curl -s https://<도메인>/actuator/health`가 `{"status":"UP"}`, 폰에서 바코드를 비추면 번호가 채워진다.

---

## Docker가 있는 PC에서 이미지 확인

```bash
cd backend && ./gradlew bootJar && cd ..
docker build -f deploy/Dockerfile -t mycosmetic:local backend/build/libs
docker run --rm --network host --env-file .env -e SPRING_PROFILES_ACTIVE=e2e mycosmetic:local
curl -s localhost:8080/actuator/health    # {"status":"UP"}
```

Flyway SQL(`DB/migration/`)은 Gradle이 JAR 안에 넣으므로 Docker 빌드 컨텍스트는 `backend/build/libs`(JAR 하나)면 된다.

## 되돌리기

- 자동: `deploy.sh`가 헬스체크(5초 간격 24번)에 실패하면 새 컨테이너를 지우고 `/opt/mycosmetic/current_tag`의 이전 태그로 다시 띄운 뒤 실패로 끝난다.
- 수동: EC2에서 `IMAGE_REPO=ghcr.io/<owner>/mycosmetic bash /opt/mycosmetic/deploy.sh <이전 sha>`.
- DB 마이그레이션은 되돌리지 않는다. 마이그레이션은 이전 버전 앱과도 맞게(컬럼 추가 → 다음 배포에서 삭제) 쓴다.
