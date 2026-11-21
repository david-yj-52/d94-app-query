-- =============================================================
-- V566: V565 직후 ddl-auto=update 가 varchar(255)로 되돌린 사용자 내용 컬럼 6개를 다시 TEXT로
-- 엔티티에 columnDefinition="text" 를 지정해(같은 배포) 이후 기동에서 되돌리지 않는다.
-- (ACT_CM 은 lib-core BaseModel 소관이라 제외 — Hibernate 가 계속 varchar(255) 로 맞춘다)
-- =============================================================
ALTER TABLE SN_CIRA_ISSUE_LOG ALTER COLUMN OLD_VAL TYPE TEXT, ALTER COLUMN NEW_VAL TYPE TEXT;
ALTER TABLE SN_CIRA_ISSUE_CF_VALUE ALTER COLUMN VAL_TEXT TYPE TEXT;
ALTER TABLE SN_CIRA_SAVED_FILTER ALTER COLUMN JQL_QUERY TYPE TEXT;
ALTER TABLE SN_CIRA_SPRINT ALTER COLUMN GOAL TYPE TEXT;
ALTER TABLE SN_CIRA_AUDIT_LOG ALTER COLUMN USER_AGENT TYPE TEXT;
