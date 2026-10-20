# DB 상세 설계 및 적용 안내 v0.1

작성일: 2026-10-20. [MVP 기능 설계](mvp-design.md)의 물리 DB 상세화.
DDL: [V1__init_schema.sql](../backend/src/main/resources/db/migration/V1__init_schema.sql)

## 적용 상태
- 8개 업무 테이블 / 77개 컬럼. Flyway 자체 이력 테이블은 업무 테이블 수에 포함하지 않는다.
- MariaDB 10.11 이상을 설계 기준으로 삼았다. 실제 운영 버전과 Flyway/JDBC/JPA 버전은 구현 단계에서 고정하고 같은 버전으로 검증한다.
- 현재 환경에 MariaDB 서버·클라이언트와 Docker가 없어 실제 DDL 실행은 수행하지 않았다.
- 테이블 생성 순서, FK 대상/컬럼, 컬럼 중복, 제약조건 이름 중복은 정적 검사했다. 실제 SQL 구문·버전 호환성·JPA 매핑 검증은 미완료다.
- 운영 DB에 적용한 상태가 아니다. 빈 개발 DB에서 먼저 적용·검증해야 한다.

## 공통 규칙
- PK는 signed BIGINT AUTO_INCREMENT, Java Long; API ID는 문자열로 전달한다.
- 테이블은 명시적 대문자 TB_* 이름, 컬럼은 소문자 snake_case. JPA @Table/@Column을 명시하고 실제 naming strategy가 이름을 바꾸지 않는지 통합 테스트한다.
- utf8mb4_unicode_ci. login_id는 서비스에서 앞뒤 공백 제거, 소문자 ASCII ID 정규화; 중복은 DB UNIQUE로 최종 검증한다.
- status/role/event_type은 대문자 enum 이름으로 저장. JPA EnumType.STRING + 서비스 검증. DB collation의 대소문자 비교만으로 정확한 코드 표기를 보장한다고 가정하지 않는다.
- storage_key는 원본명이 아닌 소문자 UUID 기반 ASCII 키다.
- DATETIME(6)은 시간대 정보를 담지 않는다. JVM/JDBC/DB 연결을 UTC로 맞추고 서버가 UTC 값을 지정한다. DB CURRENT_TIMESTAMP 기본값·ON UPDATE는 사용하지 않는다.
- due_date는 한국 달력 날짜로 해석하는 LocalDate다.
- BOOLEAN은 Java Boolean/boolean에 대응한다. 실제 JDBC 타입 매핑은 ddl-auto=validate로 확인한다.
- 입력 길이는 서비스에서 먼저 검사하고 DB에서도 주요 TEXT 필드를 제한한다. JPA 길이/컬럼 타입은 DDL과 일치시킨다.
- 모든 FK는 RESTRICT. DB cascade delete, JPA CascadeType.REMOVE/orphanRemoval로 이력을 지우지 않는다.
- 수정 가능한 집합 루트와 참여자에 version을 둔다. 댓글·파일·이력·제출은 업무 집합을 통해 변경한다.

## 컬럼 사전

표의 기본값 '-'는 DB 기본값 없음이다. 값은 서버에서 지정한다.
계산 열 pending_task_id는 서비스 입력 대상이 아니며 DB가 생성한다.

### TB_USER — 직원 계정

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| login_id | VARCHAR(100) | N | - | 로그인 ID; 서비스에서 소문자 ASCII 정규화 |
| password_hash | VARCHAR(255) | N | - | 비밀번호 해시 |
| name | VARCHAR(100) | N | - | 직원 표시명 |
| system_role | VARCHAR(20) | N | DEFAULT 'EMPLOYEE' | 시스템 권한 |
| active | BOOLEAN | N | DEFAULT TRUE | 계정 활성 |
| password_change_required | BOOLEAN | N | DEFAULT TRUE | 초기 비밀번호 변경 필요 |
| created_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| updated_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| version | BIGINT | N | DEFAULT 0 | JPA 낙관적 잠금 버전 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
UNIQUE KEY uk_user_login (login_id);
CONSTRAINT ck_user_role CHECK (system_role IN ('EMPLOYEE','SUPER_ADMIN'));
CONSTRAINT ck_user_flags CHECK (active IN (0,1) AND password_change_required IN (0,1));
CONSTRAINT ck_user_version CHECK (version >= 0);
CONSTRAINT ck_user_text CHECK (CHAR_LENGTH(TRIM(login_id)) > 0 AND CHAR_LENGTH(TRIM(name)) > 0 AND CHAR_LENGTH(password_hash) > 0);
```

### TB_PROJECT — 프로젝트

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| name | VARCHAR(100) | N | - | 프로젝트명 |
| description | TEXT | Y | - | 설명, 최대 2000자 |
| created_by | BIGINT | N | - | 생성자 |
| status | VARCHAR(20) | N | DEFAULT 'ACTIVE' | ACTIVE/ARCHIVED |
| created_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| updated_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| version | BIGINT | N | DEFAULT 0 | JPA 낙관적 잠금 버전 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
KEY ix_project_creator (created_by);
CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT ck_project_status CHECK (status IN ('ACTIVE','ARCHIVED'));
CONSTRAINT ck_project_text CHECK (CHAR_LENGTH(TRIM(name)) > 0 AND (description IS NULL OR CHAR_LENGTH(description) <= 2000));
CONSTRAINT ck_project_version CHECK (version >= 0);
```

### TB_PROJECT_MEMBER — 프로젝트 참여자

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| project_id | BIGINT | N | - | 프로젝트 |
| user_id | BIGINT | N | - | 직원 |
| role | VARCHAR(20) | N | DEFAULT 'MEMBER' | 프로젝트 역할 |
| active | BOOLEAN | N | DEFAULT TRUE | 참여 활성 |
| joined_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| left_at | DATETIME(6) | Y | - | 참여 해제 시각 |
| version | BIGINT | N | DEFAULT 0 | JPA 낙관적 잠금 버전 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
UNIQUE KEY uk_member_project_user (project_id,user_id);
KEY ix_member_user_active (user_id,active,project_id);
CONSTRAINT fk_member_project FOREIGN KEY (project_id) REFERENCES TB_PROJECT (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT ck_member_role CHECK (role IN ('MEMBER','MANAGER'));
CONSTRAINT ck_member_active CHECK ((active = 1 AND left_at IS NULL) OR (active = 0 AND left_at IS NOT NULL AND left_at >= joined_at));
CONSTRAINT ck_member_version CHECK (version >= 0);
```

### TB_TASK — 업무

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| project_id | BIGINT | N | - | 소속 프로젝트 |
| requester_id | BIGINT | N | - | 요청자; 변경 불가 |
| assignee_id | BIGINT | N | - | 담당자 1명 |
| title | VARCHAR(200) | N | - | 업무명 |
| description | TEXT | Y | - | 요청 내용 최대 10000자 |
| status | VARCHAR(30) | N | DEFAULT 'REQUESTED' | 업무 상태 |
| due_date | DATE | Y | - | 한국 날짜 기준 기한 |
| completed_at | DATETIME(6) | Y | - | 현재 최종 승인 시각 |
| canceled_at | DATETIME(6) | Y | - | 취소 시각 |
| created_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| updated_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| version | BIGINT | N | DEFAULT 0 | JPA 낙관적 잠금 버전 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
KEY ix_task_project_status_due (project_id,status,due_date,id);
KEY ix_task_assignee_status_due (assignee_id,status,due_date,id);
KEY ix_task_requester_status_updated (requester_id,status,updated_at,id);
KEY ix_task_project_completed (project_id,completed_at,id);
CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES TB_PROJECT (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_task_requester FOREIGN KEY (requester_id) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT ck_task_status CHECK (status IN ('REQUESTED','IN_PROGRESS','REVIEW_REQUESTED','COMPLETED','CANCELED'));
CONSTRAINT ck_task_terminal CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL AND canceled_at IS NULL) OR (status = 'CANCELED' AND canceled_at IS NOT NULL AND completed_at IS NULL) OR (status IN ('REQUESTED','IN_PROGRESS','REVIEW_REQUESTED') AND completed_at IS NULL AND canceled_at IS NULL));
CONSTRAINT ck_task_text CHECK (CHAR_LENGTH(TRIM(title)) > 0 AND (description IS NULL OR CHAR_LENGTH(description) <= 10000));
CONSTRAINT ck_task_version CHECK (version >= 0);
```

### TB_TASK_SUBMISSION — 완료 제출 회차

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| task_id | BIGINT | N | - | 업무 |
| round_no | INT | N | - | 업무 내 제출 회차, 1부터 |
| submitted_by | BIGINT | N | - | 제출자 |
| result | TEXT | N | - | 처리 결과 최대 10000자 |
| due_date_snapshot | DATE | Y | - | 제출 당시 기한 |
| submitted_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| status | VARCHAR(30) | N | DEFAULT 'PENDING' | 제출 상태 |
| resolved_by | BIGINT | Y | - | 승인/보완/철회/취소 처리자 |
| resolved_at | DATETIME(6) | Y | - | 제출 종결 시각 |
| resolution_reason | TEXT | Y | - | 보완/철회/취소 사유 최대 2000자 |
| pending_task_id | BIGINT | Y | GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN task_id ELSE NULL END) PERSISTENT | DB 계산 열; 업무별 대기 제출 1건 강제; JPA 쓰기 금지 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
UNIQUE KEY uk_submission_round (task_id,round_no);
UNIQUE KEY uk_submission_pending (pending_task_id);
UNIQUE KEY uk_submission_id_task (id,task_id);
KEY ix_submission_submitter (submitted_by);
KEY ix_submission_resolver (resolved_by);
CONSTRAINT fk_submission_task FOREIGN KEY (task_id) REFERENCES TB_TASK (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_submission_submitter FOREIGN KEY (submitted_by) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_submission_resolver FOREIGN KEY (resolved_by) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT ck_submission_round CHECK (round_no >= 1);
CONSTRAINT ck_submission_status CHECK (status IN ('PENDING','APPROVED','CHANGES_REQUESTED','WITHDRAWN','CANCELED'));
CONSTRAINT ck_submission_result CHECK (CHAR_LENGTH(TRIM(result)) > 0 AND CHAR_LENGTH(result) <= 10000);
CONSTRAINT ck_submission_resolution CHECK ((status = 'PENDING' AND resolved_by IS NULL AND resolved_at IS NULL AND resolution_reason IS NULL) OR (status <> 'PENDING' AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL AND resolved_at >= submitted_at));
CONSTRAINT ck_submission_reason CHECK ((resolution_reason IS NULL OR CHAR_LENGTH(resolution_reason) <= 2000) AND (status NOT IN ('CHANGES_REQUESTED','WITHDRAWN','CANCELED') OR (resolution_reason IS NOT NULL AND CHAR_LENGTH(TRIM(resolution_reason)) > 0)));
```

### TB_TASK_COMMENT — 업무 댓글

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| task_id | BIGINT | N | - | 업무 |
| author_id | BIGINT | N | - | 작성자 |
| body | TEXT | N | - | 댓글 최대 2000자 |
| created_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
UNIQUE KEY uk_comment_id_task (id,task_id);
KEY ix_comment_task_created (task_id,created_at,id);
KEY ix_comment_author (author_id);
CONSTRAINT fk_comment_task FOREIGN KEY (task_id) REFERENCES TB_TASK (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT ck_comment_body CHECK (CHAR_LENGTH(TRIM(body)) > 0 AND CHAR_LENGTH(body) <= 2000);
```

### TB_TASK_FILE — 첨부파일 메타데이터

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| task_id | BIGINT | N | - | 업무 |
| submission_id | BIGINT | Y | - | 결과 제출 회차 |
| comment_id | BIGINT | Y | - | 댓글 |
| kind | VARCHAR(20) | N | - | REQUEST/RESULT/COMMENT |
| original_name | VARCHAR(255) | N | - | 다운로드 표시명 |
| storage_key | VARCHAR(255) | N | - | 서버 생성 ASCII UUID 저장키 |
| content_type | VARCHAR(150) | N | - | 서버 판별 MIME |
| size_bytes | BIGINT | N | - | 바이트; 최대 10485760 |
| uploaded_by | BIGINT | N | - | 업로더 |
| created_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |
| deleted_at | DATETIME(6) | Y | - | 논리 삭제 시각 |
| deleted_by | BIGINT | Y | - | 삭제자 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
UNIQUE KEY uk_file_storage (storage_key);
KEY ix_file_task_kind (task_id,deleted_at,kind);
KEY ix_file_submission_task (submission_id,task_id);
KEY ix_file_comment_task (comment_id,task_id);
KEY ix_file_uploader (uploaded_by);
KEY ix_file_deleter (deleted_by);
CONSTRAINT fk_file_task FOREIGN KEY (task_id) REFERENCES TB_TASK (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_file_submission_task FOREIGN KEY (submission_id,task_id) REFERENCES TB_TASK_SUBMISSION (id,task_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_file_comment_task FOREIGN KEY (comment_id,task_id) REFERENCES TB_TASK_COMMENT (id,task_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_file_uploader FOREIGN KEY (uploaded_by) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_file_deleter FOREIGN KEY (deleted_by) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT ck_file_kind CHECK ((kind = 'REQUEST' AND submission_id IS NULL AND comment_id IS NULL) OR (kind = 'RESULT' AND submission_id IS NOT NULL AND comment_id IS NULL) OR (kind = 'COMMENT' AND submission_id IS NULL AND comment_id IS NOT NULL));
CONSTRAINT ck_file_size CHECK (size_bytes BETWEEN 0 AND 10485760);
CONSTRAINT ck_file_delete CHECK ((deleted_at IS NULL AND deleted_by IS NULL) OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL AND deleted_at >= created_at));
CONSTRAINT ck_file_text CHECK (CHAR_LENGTH(TRIM(original_name)) > 0 AND CHAR_LENGTH(TRIM(storage_key)) > 0 AND CHAR_LENGTH(TRIM(content_type)) > 0);
```

### TB_TASK_HISTORY — 업무 변경 이력

| 컬럼 | 타입 | NULL | 기본값/생성 방식 | 의미 |
| --- | --- | --- | --- | --- |
| id | BIGINT | N | AUTO_INCREMENT | 기본키 |
| task_id | BIGINT | N | - | 업무 |
| actor_id | BIGINT | N | - | 수행자 |
| event_type | VARCHAR(50) | N | - | 사건 코드 |
| before_value | LONGTEXT | Y | - | 변경 전 JSON |
| after_value | LONGTEXT | Y | - | 변경 후 JSON |
| reason | TEXT | Y | - | 사유 최대 2000자 |
| created_at | DATETIME(6) | N | - | UTC 사건 시각; 서버에서 지정 |

**키·인덱스·제약조건**

```sql
PRIMARY KEY (id);
KEY ix_history_task_created (task_id,created_at,id);
KEY ix_history_actor (actor_id);
CONSTRAINT fk_history_task FOREIGN KEY (task_id) REFERENCES TB_TASK (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT fk_history_actor FOREIGN KEY (actor_id) REFERENCES TB_USER (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CONSTRAINT ck_history_before CHECK (before_value IS NULL OR JSON_VALID(before_value));
CONSTRAINT ck_history_after CHECK (after_value IS NULL OR JSON_VALID(after_value));
CONSTRAINT ck_history_reason CHECK (reason IS NULL OR CHAR_LENGTH(reason) <= 2000);
CONSTRAINT ck_history_event CHECK (event_type IN ('CREATED','UPDATED','ASSIGNEE_CHANGED','DUE_DATE_CHANGED','STARTED','UNSTARTED','SUBMITTED','APPROVED','CHANGES_REQUESTED','WITHDRAWN','REOPENED','CANCELED','COMMENT_ADDED','FILE_ADDED','FILE_DELETED'));
```

## 동시성과 서비스 트랜잭션

1. 업무 변경 API는 요청 version과 DB version을 검증한다.
2. 요청 첨부 개수 변경, 제출 회차 생성, 승인/보완/철회/취소는 같은 TB_TASK 행을 먼저 잠그고 상태·권한·version을 재검증한다.
3. 잠금은 JPA PESSIMISTIC_WRITE 등으로 일관되게 획득한다. @Version도 유지하며 업무 행이 반드시 변경/버전 증가되게 구현한다.
4. 잠금 확보 후 다음 round_no를 계산하고 제출·상태·이력을 동일 트랜잭션에 반영한다.
5. 파일만 변경한 경우에도 업무 updated_at/version을 갱신한다. 파일 원문 읽기는 DB 트랜잭션 밖에서 준비한다.
6. 제약조건 위반·낙관적 잠금 충돌은 상황에 맞는 409로 변환한다. 권한 검증 실패는 403.
7. 회원 비활성화와 업무 배정/완료 요청 등이 경쟁하지 않도록 서비스 간 공통 잠금 순서를 정한다: 관련 사용자 ID 오름차순 → 프로젝트 → 업무. 복수 행 잠금은 ID 순서를 통일한다.
8. 프로젝트 관리자 최소 1명은 프로젝트 행 잠금 아래 검사한다. 최고관리자 최소 1명 보호는 직원 관리 트랜잭션을 직렬화할 별도 공통 잠금 전략이 구현 시 필요하다.

### DB가 보장하는 항목
- 존재하지 않는 사용자/프로젝트/업무 참조 차단.
- 프로젝트 참여자 중복 및 제출 회차 중복 차단.
- pending_task_id 계산 열 + UNIQUE로 업무당 PENDING 제출 최대 1개. 종결 제출은 NULL이므로 여러 회차 보존 가능.
- 첨부의 (submission_id, task_id), (comment_id, task_id) 복합 FK로 다른 업무의 결과·댓글 연결 차단.
- REQUEST/RESULT/COMMENT 소속 컬럼 조합, 파일 크기, 삭제자·삭제 시각 짝 검증.
- 업무 완료·취소 상태와 해당 사건 시각의 일치.
- 제출 종결자·시각·필수 사유, JSON 이력 형식 검증.

### 서비스가 보장하는 항목
- 요청자만 승인, 담당자만 제출 등 행위 권한.
- 활성 참여자만 업무 등록·배정, 프로젝트 보관 상태의 쓰기 제한.
- 허용된 상태 전이와 TB_TASK.status ↔ 현재 PENDING 제출의 일치.
- 파일당 확장자·실제 형식 검사, 그룹별 최대 5개, 저장소 작업과 DB 실패 정리.
- 마지막 관리자 해제 방지, 미완료 업무 담당자·요청자 비활성화 제한.
- 요청자 불변, 제출/댓글 첨부 불변, 업무 삭제 금지, 이력 append-only.
- 파일의 물리 삭제 재시도: deleted_at 행과 실제 파일을 비교해 정리. 미등록 고아 파일은 유예 시간을 둔 저장소 대조 작업으로 정리하며 서비스 로그로 추적한다.
- 사용자 이름을 변경하면 조회 시 현재 표시명이 나온다. 과거 시점의 이름 보존이 필요해지면 별도 스냅샷 컬럼을 후속 마이그레이션으로 추가한다.

## JPA 매핑 주의사항
- version: @Version. 자동 생성 id: GenerationType.IDENTITY.
- pending_task_id는 엔티티에서 생략하거나 insertable=false, updatable=false로 읽기 전용 매핑.
- result/description/body 등의 TEXT와 before_value/after_value LONGTEXT는 실제 MariaDB JDBC 타입에 맞춰 명시적으로 매핑한다.
- 복합 FK의 task_id는 TB_TASK_FILE의 단일 컬럼을 재사용한다. 중복 쓰기 매핑을 피하고 연관 매핑을 읽기 전용으로 두거나 스칼라 ID + 서비스 조회 방식으로 구현한다.
- 업무 상태와 제출 상태는 각각 다른 enum으로 정의한다.
- JOIN으로 댓글·파일을 함께 fetch하여 업무 페이지가 중복되지 않도록 목록 DTO와 상세 조회를 분리한다.
- Spring Security 서버 세션용 테이블은 이번 업무 DDL에 포함하지 않는다. 단일 서버 메모리 세션 기준이며 세션 영속화/다중 서버 도입 시 별도 설계한다.

## 적용·변경 절차
1. 별도 빈 개발 DB를 utf8mb4로 준비하고 접근 계정은 환경 변수로 제공한다.
2. 마이그레이션 계정과 런타임 계정 권한은 배포 단계에서 분리한다.
3. Flyway가 backend/src/main/resources/db/migration/V1__init_schema.sql을 적용하도록 설정한다.
4. 실제 API 실행 전 ddl-auto=validate로 엔티티와 DB를 검증한다. 아직 Spring Boot 설정/빌드는 생성하지 않았다.
5. 이미 적용된 V1을 수정하지 않는다. 변경은 V2__...sql 이후 파일로 누적한다.
6. DDL 실패를 전체 트랜잭션 롤백으로 복구할 수 있다고 가정하지 않는다. 비어 있는 일회성 개발 DB에서 실패했다면 원인을 수정하고 그 개발 DB만 재생성한다. 운영 적용 실패는 변경 이력과 DB 상태를 조사해 별도 복구한다.
7. 사전 업무 테이블이 있는 DB에는 무조건 적용하거나 baseline을 임의 지정하지 않는다.
8. 관리자 계정·샘플 데이터·비밀번호는 V1에 포함하지 않는다.

## 실제 MariaDB에서 남은 검증
| 검증 | 정상 입력 | 실패해야 하는 입력 |
| --- | --- | --- |
| 최초 적용 | 빈 DB에 테이블 8개 생성 | 기존 테이블에 재실행 |
| 계정 | 서로 다른 정규화 ID | 동일 ID |
| 참여 | 한 프로젝트에 사용자 1행 | 동일 사용자 중복 |
| 제출 회차 | 1회차 종결 후 2회차 대기 | 동일 회차, 대기 제출 2건 |
| 제출 종결 | 승인자·승인시각 기록 | 종결인데 처리자 없음 |
| 첨부 소속 | 해당 업무 결과에 파일 연결 | 다른 업무 제출/댓글 연결 |
| 파일 제한 | 0~10485760 bytes | 음수, 10485761 bytes |
| 업무 상태 | COMPLETED + completed_at | 완료인데 승인시각 없음 |
| 이력 | 유효 JSON/null | 손상된 JSON |
| FK 삭제 | 업무 없는 개발용 사용자 정리 | 업무 참조 사용자 물리 삭제 |
| 동시 승인 | 한 요청 성공 | 나머지 충돌 처리 |
| 시간·문자 | 한글·이모지·UTC 마이크로초 | 앱 표시/DB 값 불일치 여부 점검 |

## 공식 문법 근거
- [MariaDB CONSTRAINT](https://mariadb.com/docs/server/reference/sql-statements/data-definition/constraint)
- [MariaDB Generated Columns](https://mariadb.com/docs/server/reference/sql-statements/data-definition/create/generated-columns)

위 문법을 참고해 설계했으며 실제 런타임 테스트를 대체하지 않는다.
