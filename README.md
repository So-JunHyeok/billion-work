# billion-work

프로젝트 기반 사내 업무지원시스템 MVP.

## 현재 구현 상태

- 완료: 기능·권한·화면·DB·API 설계, 초기 MariaDB DDL
- 추가: React/TypeScript 시작 화면, Spring Boot 기본 서버, JPA/Flyway 설정, MariaDB Compose, 통합 테스트 및 CI
- 다음: 직원 로그인 → 프로젝트·참여자 → 업무 처리·완료 승인 → 댓글·첨부·현황

**실행 방법: [로컬 개발환경 가이드](docs/local-development.md)**

현재 프론트 화면은 백엔드·DB 연결 확인용입니다. 기존 샘플의 업무 기능을 아직 React로 이식하지 않았습니다.
기본 계정·로그인 API는 아직 없으며 상태 확인 외 API는 차단합니다.

## 구성

| 경로 | 내용 |
| --- | --- |
| frontend/ | React + Vite + TypeScript |
| backend/ | Java 21 + Spring Boot + JPA + Flyway (Maven) |
| infra/ | MariaDB 개발용 Docker Compose |
| docs/ | 설계·실행 문서 |
| .github/workflows/ | 빌드·실제 MariaDB 통합 테스트 |

## 설계 문서

- [MVP 화면·권한·API 설계](docs/mvp-design.md)
- [DB 컬럼·제약조건](docs/database-design.md)
- [초기 DDL](backend/src/main/resources/db/migration/V1__init_schema.sql)

## 핵심 업무 규칙

직원 누구나 프로젝트를 생성하며 생성자가 관리자입니다. 업무당 담당자 1명이며,
담당자가 완료 요청한 뒤 요청자가 승인해야 완료됩니다. 같은 프로젝트에서는 업무·댓글·첨부를 공유합니다.
최고관리자는 전체 조회가 가능하지만 대리 승인 권한을 자동으로 갖지 않습니다.

## 저장소 주의사항

실제 직원·고객 자료, 비밀번호, .env 실제 값, 업로드 파일, DB 덤프는 커밋하지 않습니다.
실제 업무 기능·운영 배포 설정까지 완료한 프로젝트가 아닙니다.
