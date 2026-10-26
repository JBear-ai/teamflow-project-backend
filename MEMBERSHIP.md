# ProjectMembership 배포 및 검증

생성자는 이력으로 보존하고, 접근 권한은 project_memberships만 사용합니다. 생성자는 더 이상 영구 관리자 권한을 갖지 않습니다.

| 작업 | LEAD | CO_MANAGER | MEMBER |
|---|---|---|---|
| 프로젝트/팀원 조회 | 허용 | 허용 | 허용 |
| 프로젝트 수정 | 허용 | 허용 | 금지 |
| 가입 회원 이메일로 추가 (MEMBER) | 허용 | 허용 | 금지 |
| 역할 변경 | 허용 | 금지 | 금지 |
| 다른 MEMBER 제거 | 허용 | 허용 | 금지 |
| 다른 CO_MANAGER/LEAD 제거 | 허용 | 금지 | 금지 |
| 프로젝트 삭제 | 허용 | 금지 | 금지 |

마지막 LEAD 강등/제거는 409, 자기 자신 제거는 403입니다. LEAD는 다른 LEAD가 있으면 자신의 역할을 변경할 수 있습니다. 비참여자는 404, 미인증은 401, 권한 부족은 403입니다. 세션/CSRF 정책을 유지합니다.

## API

- GET /api/projects, GET /api/projects/{id}: 참여 프로젝트와 myRole 반환
- GET /api/projects/{id}/members: memberId/email/name/role/joinedAt 목록
- POST /api/projects/{id}/members: {"email":"member@example.com"}, 성공 201, 중복 409
- PUT /api/projects/{id}/members/{memberId}/role: {"role":"CO_MANAGER"}, 성공 200
- DELETE /api/projects/{id}/members/{memberId}: 성공 204

## P1/P2 동시성

모든 기존 프로젝트 변경은 동일한 projects 행을 SELECT FOR UPDATE로 잠근 후 회원/권한/대상/LEAD 수를 조회합니다. 잠금은 트랜잭션 커밋까지 유지됩니다. **잠금 이전에 조회를 추가하거나, 이미 조회한 외부 트랜잭션에서 변경 서비스를 호출하지 마세요.** MySQL REPEATABLE READ에서 이전 snapshot을 만들면 잠금 대기 후에도 오래된 멤버십을 읽습니다. 현재 컨트롤러는 각각 새로운 서비스 트랜잭션으로 진입합니다.

MySqlMembershipTests는 별도 연결로 프로젝트 잠금을 선점하고 performance_schema.data_lock_waits에서 두 요청이 실제 대기함을 확인한 뒤 잠금을 해제합니다. 수정 전 순서에서는 중복 추가 제약 위반과 두 LEAD 동시 강등이 재현됩니다. 수정 후 한 요청 성공/다른 요청 409 및 최소 1 LEAD를 검증합니다. DB unique(project_id,member_id)는 최종 방어선입니다. 직접 DB 쓰기는 이 직렬화 규약을 우회하므로 서비스 가동 중 이관/수동 쓰기를 하지 않습니다.

## 기존 데이터 이관 (승인 후 별도 실행)

1. 운영 변경은 별도 승인 대상입니다. DB 백업을 만들고 복구 리허설을 수행합니다. 기존 앱과 모든 쓰기 프로세스를 중지합니다.
2. 운영과 같은 MySQL 버전/설정의 복제 DB에서 먼저 리허설합니다. projects.creator_id 및 members.id가 존재하고 BIGINT 형식/부호가 일치하는지, 관련 테이블이 InnoDB인지 확인합니다. membership 테이블이 이미 있으면 SHOW CREATE TABLE로 unique/FK/check/index를 확인합니다. CREATE TABLE IF NOT EXISTS는 기존 잘못된 스키마를 수정하지 않습니다.
3. migration/001_project_memberships.sql을 실행합니다. MySQL DDL은 암묵적 커밋이므로 파일 전체를 하나의 원자적 트랜잭션으로 간주하지 않습니다. 오류 시 후속 실행을 중지하고 원인을 확인합니다.
4. 유효한 creator가 있고 멤버십이 전혀 없는 프로젝트만 LEAD로 추가합니다. 기존 멤버십이 있는 프로젝트의 역할은 변경하지 않습니다. creator_id와 프로젝트 내용은 수정/삭제하지 않습니다. 재실행 시 제거/강등된 생성자를 복원하지 않습니다.
5. null/유실된 creator 프로젝트는 보존하되 접근 불가 상태로 남깁니다. 담당자 확인 및 명시적 승인 없이 임의 사용자에게 할당하지 않습니다. 기존 membership에 LEAD가 없는 프로젝트도 보고 후 별도 복구 승인 대상으로 둡니다.
6. 아래 사후 점검과 SQL 파일의 결과를 확인합니다. 유효한 생성자 프로젝트 중 멤버십 없는 건수 및 활성 팀의 LEAD 없는 건수가 0이어야 합니다. 이관 전후 프로젝트/회원 수, 이름/설명/creator_id, 기존 membership 역할을 대조합니다.
7. 새 백엔드는 SPRING_JPA_HIBERNATE_DDL_AUTO=validate로 기동하여 스키마 검증 후 프론트를 배포합니다. 운영의 자동 update에 이관을 맡기지 않습니다. 운영 DB에는 테스트를 실행하지 않습니다.
8. 롤백이 필요하면 앱을 중지하고 복구 계획을 검토합니다. 구버전은 creator 기반 권한이므로 새 멤버십에서 제거한 생성자에게 접근을 되돌릴 수 있습니다. 단순 구버전 재배포를 안전한 권한 롤백으로 간주하지 마세요. 테이블 DROP으로 되돌리지 않습니다.

```sql
SELECT p.id FROM projects p JOIN members m ON m.id=p.creator_id
WHERE NOT EXISTS (SELECT 1 FROM project_memberships pm WHERE pm.project_id=p.id);
SELECT project_id FROM project_memberships GROUP BY project_id
HAVING SUM(role='LEAD')=0;
SELECT project_id, member_id, COUNT(*) FROM project_memberships
GROUP BY project_id, member_id HAVING COUNT(*)>1;
```

## 테스트 실행

JDK 21: `./gradlew test` (Windows: `gradlew.bat test`). 기본 테스트는 H2이며 운영 설정과 분리된 src/test/resources/application.properties를 사용합니다.

MySQL 테스트는 명시적 환경 변수가 있을 때만 실행합니다. **전용 폐기 가능 빈 DB 두 개**를 준비합니다. membership 테스트는 create-drop, migration 테스트는 빈 스키마에 fixture를 생성하므로 재실행 시 새로운 빈 migration 스키마가 필요합니다.

- TEAMFLOW_MYSQL_TEST_URL: jdbc:mysql://127.0.0.1:13316/membership_test?allowPublicKeyRetrieval=true&useSSL=false (로컬 테스트 전용 예시)
- TEAMFLOW_MYSQL_MIGRATION_URL: jdbc:mysql://127.0.0.1:13316/migration_test?allowPublicKeyRetrieval=true&useSSL=false
- TEAMFLOW_MYSQL_TEST_USER / TEAMFLOW_MYSQL_TEST_PASSWORD: 테스트 전용 계정 (소스/로그에 실제 비밀번호 저장 금지)
- 해당 계정은 테스트 스키마 DDL/DML 및 performance_schema.data_lock_waits 조회 권한이 필요합니다.

GitHub Actions는 폐기 가능한 MySQL 8.4 서비스를 만들어 H2/MySQL 테스트를 함께 실행합니다. 환경 변수가 없으면 MySQL 테스트는 skip되므로 H2 성공만을 MySQL 검증으로 보고하지 않습니다.

## 남은 배포 전 수동 검증

- 실제 운영 버전/격리 수준/sql_mode/문자 정렬 및 기존 데이터 복제본에서 migration 검증, null/유실 creator 소유자 확인
- MySQL lock wait timeout, 부하 및 두 앱 인스턴스 동시 요청 검증 (현재 테스트는 한 앱 프로세스의 독립 DB 트랜잭션)
- 두 브라우저 세션에서 초대→역할 변경→제거, 세션 만료, CSRF 갱신, CORS 확인
- 제거/강등된 사용자의 이미 열린 화면에서 새 요청이 403/404로 거절되는지 확인
- 기본 브랜치 병합, 운영 SQL 실행 및 배포는 사용자 승인 후 진행
