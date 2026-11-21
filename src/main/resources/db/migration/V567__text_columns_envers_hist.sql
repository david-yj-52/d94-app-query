-- =============================================================
-- V567: Envers 감사 테이블(_hist)의 같은 6개 컬럼도 TEXT로 (V566 의 짝)
-- _hist 테이블은 ddl-auto 가 만든다(Flyway 에 없음, test-report D6). 그래서 신규 DB 에서는
-- Flyway 시점에 아직 없으므로 IF EXISTS — 이후 Hibernate 가 엔티티의 text 선언대로 만든다.
-- 증상: 이슈 본문 255자 초과 수정 시 sn_cira_issue_log_hist insert 실패(500)
-- =============================================================
ALTER TABLE IF EXISTS SN_CIRA_ISSUE_LOG_HIST ALTER COLUMN OLD_VAL TYPE TEXT, ALTER COLUMN NEW_VAL TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_ISSUE_CF_VALUE_HIST ALTER COLUMN VAL_TEXT TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_SAVED_FILTER_HIST ALTER COLUMN JQL_QUERY TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_SPRINT_HIST ALTER COLUMN GOAL TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_AUDIT_LOG_HIST ALTER COLUMN USER_AGENT TYPE TEXT;
