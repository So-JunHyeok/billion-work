# 로컬 개발환경 실행

현재 단계는 기본 프로젝트 구성입니다. 업무 CRUD·로그인·JPA 업무 엔티티는 다음 단계에서 구현합니다.
기존 브라우저 샘플과 이 저장소의 React 코드는 별개이며 샘플 데이터를 자동으로 이관하지 않습니다.

## 구성

| 항목 | 기준 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| 빌드 | Maven 3.9 계열; Docker 빌더는 3.9.9 |
| React | 19.2.0 |
| TypeScript | 5.9.3 |
| Vite | 7.1.12 |
| Node.js | 22.12+ (22.x) 또는 24.x |
| MariaDB | 개발/통합 테스트 이미지 11.4.5 |

기본 버전을 명시적으로 지정했다. 운영 도입 시 지원 기간·보안 업데이트와 호환성을 재확인한다.
프론트와 백엔드는 독립 실행/빌드하며 npm workspace 등 추가 모노레포 도구는 아직 사용하지 않는다.
백엔드 빌드 도구는 Maven으로 구성했다. 시스템 Maven 또는 제공 Docker 빌더를 사용하며 Maven Wrapper는 아직 포함하지 않았다.

## 가장 간단한 실행: DB + 백엔드는 Docker, 프론트는 Node

필요: Git, Docker Desktop(Compose v2 포함), Node.js.
Docker Desktop을 먼저 실행한다. 모든 명령은 별도 표기가 없으면 저장소 루트에서 실행한다.

```sh
git clone https://github.com/So-JunHyeok/billion-work.git
cd billion-work
```

1. `infra/.env.example`을 `infra/.env`로 복사한다.
   - Windows PowerShell: `Copy-Item infra/.env.example infra/.env`
   - macOS/Linux: `cp infra/.env.example infra/.env`
2. `infra/.env`의 `DB_PASSWORD`, `DB_ROOT_PASSWORD`에 서로 다른 개발용 비밀번호를 입력한다. 빈 값이면 Compose가 실행을 거부한다.
3. 아래 명령으로 DB와 백엔드를 실행한다.

```sh
docker compose --env-file infra/.env -f infra/compose.yaml --profile app up -d --build
docker compose --env-file infra/.env -f infra/compose.yaml ps
docker compose --env-file infra/.env -f infra/compose.yaml logs -f backend
```

백엔드 시작 시 Flyway가 기존 V1 DDL을 자동 적용한다. 초기 실행은 이미지·Maven 의존성 다운로드에 시간이 필요하다.
로그 보기 종료는 Ctrl+C이며 컨테이너는 계속 실행된다.

4. 별도 터미널에서 프론트를 실행한다.

```sh
cd frontend
npm ci
npm run dev
```

5. `http://127.0.0.1:5173` 접속. 시작 화면의 연결 상태가 정상인지 확인한다.
   - API 정보: `http://127.0.0.1:8080/api/v1/system/info`
   - DB 포함 상태: `http://127.0.0.1:8080/api/v1/system/health`
   - 프론트의 `/api` 요청은 Vite가 8080으로 전달한다.

이 단계에서는 두 시스템 상태 GET만 공개하며 다른 API는 거절한다. 로그인 UI/API 및 기본 관리자 계정은 아직 없다.
실제 보안 기능을 구현하지 않은 상태에서 임시 관리자 비밀번호를 제공하지 않는다.

## IDE에서 백엔드 실행

필요: Java 21, Maven 3.9.x. IntelliJ 등에서 `backend/pom.xml`을 Maven 프로젝트로 연다.
Docker로 실행 중인 백엔드가 있다면 먼저 `docker compose --env-file infra/.env -f infra/compose.yaml --profile app stop backend`로 정지한다.

DB만 실행:

```sh
docker compose --env-file infra/.env -f infra/compose.yaml up -d db
```

IDE 실행 환경 변수:

```text
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:mariadb://127.0.0.1:3307/billion_work
DB_USERNAME=billion_work
DB_PASSWORD=<infra/.env에 입력한 DB_PASSWORD>
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC
```

`infra/.env`는 Compose의 치환 파일이다. Maven/IDE가 자동으로 읽지 않으므로 환경 변수를 직접 지정한다.
`DB_NAME`, `DB_USERNAME`, `DB_PORT`를 바꿨다면 IDE 값도 맞춘다.

환경 변수 설정 후 `backend`에서 실행:

```sh
mvn spring-boot:run
```

포트 변경 시 `SERVER_PORT`를 설정한다. 프론트는 `API_PROXY_TARGET` 환경 변수로 새 백엔드 주소를 전달한다.
Windows 예: `$env:API_PROXY_TARGET='http://127.0.0.1:8081'` 설정 후 `npm run dev`.

## 빌드·테스트

```sh
# frontend 디렉터리
npm run typecheck
npm run build

# backend 디렉터리 (Java 21 + Maven + 실행 중인 Docker 필요)
mvn verify
```

`FoundationIntegrationTest`는 Testcontainers로 별도 MariaDB 11.4.5를 띄운다. 개발 DB/실제 데이터에 접속하지 않는다.
- Flyway 실행과 업무 테이블 8개 생성
- 시스템 상태 GET 허용, 업무 API 미인증 접근 거절
- 업무별 대기 제출 중복 차단
- 다른 업무에 속한 결과 첨부 연결 차단
- 이전 제출 승인 후 다음 제출 회차 허용

Docker가 없으면 테스트를 자동 건너뛰지 않고 실패한다. 테스트를 하지 않은 상태를 성공으로 표시하지 않기 위한 설정이다.
Dockerfile의 `-DskipTests`는 이미지 빌드에 Docker-in-Docker를 요구하지 않기 위한 것이다. 이미지를 빌드했다고 테스트 통과로 간주하지 않는다.
GitHub Actions에는 프론트 빌드와 백엔드 통합 테스트를 구성했다. 실제 실행 결과는 Actions에서 확인한다.
JPA 의존성과 `ddl-auto=validate`는 준비되어 있지만 아직 엔티티가 없어 전체 테이블 매핑 검증은 다음 단계에 수행한다.

## 데이터와 종료

```sh
# 컨테이너 종료; DB 볼륨 유지
docker compose --env-file infra/.env -f infra/compose.yaml --profile app down
```

`down -v`는 DB 데이터를 삭제하므로 일반 종료에 사용하지 않는다.
이미 초기화한 DB 볼륨은 `.env`의 비밀번호를 바꿔도 기존 계정 비밀번호가 자동 변경되지 않는다.
암호 변경은 DB에서 별도 수행하고 환경 설정을 맞춘다.
초기 DDL을 이미 적용했다면 V1을 수정하지 않고 V2부터 추가한다.

이 Compose는 로컬 개발용이며 DB/백엔드 포트는 127.0.0.1로만 공개한다.
실서비스에서는 HTTPS, 운영 쿠키 설정, 런타임/마이그레이션 계정 분리, 업로드 경로, 백업 등을 별도로 구성한다.
`local` 프로필은 HTTP 개발을 위해 Secure 쿠키만 해제한다. CSRF 검증은 유지한다.

## 공식 참고

- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://vite.dev/guide/
- https://mariadb.com/docs/server/server-management/automated-mariadb-deployment-and-administration/docker-and-mariadb/using-healthcheck-sh
