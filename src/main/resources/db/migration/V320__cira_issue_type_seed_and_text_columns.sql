-- CIRA 이슈 생성 결함 수정 (docs/test-report.md D7, D5)

-- D7: 이슈 유형이 하나도 없어 클라이언트가 issueTypeId 를 보낼 수 없었다.
--     기본 유형을 넣는다. 이미 같은 이름이 있으면 그대로 둔다 (uk_ciraCiraIssueType_01 = TYPE_NM).
INSERT INTO SN_CIRA_CIRA_ISSUE_TYPE (OBJ_ID, TYPE_NM, ICON, COLOR_CD, DESCR,
                                     SRV_ID, TENANT, TRACE_ID, EVNT_NM, PREV_EVNT_NM, USE_STAT_CD,
                                     CREATED_AT, MODIFIED_AT, CREATED_BY, MODIFIED_BY)
VALUES (gen_random_uuid()::text, 'Task', 'task', '#4BADE8', '일반 작업', 'app-server', 'TSH', 'SEED-V320',
        'SeedIssueType', 'None', 'Usable', now(), now(), 'flyway', 'flyway'),
       (gen_random_uuid()::text, 'Bug', 'bug', '#E5493A', '결함', 'app-server', 'TSH', 'SEED-V320',
        'SeedIssueType', 'None', 'Usable', now(), now(), 'flyway', 'flyway'),
       (gen_random_uuid()::text, 'Story', 'story', '#63BA3C', '사용자 스토리', 'app-server', 'TSH', 'SEED-V320',
        'SeedIssueType', 'None', 'Usable', now(), now(), 'flyway', 'flyway'),
       (gen_random_uuid()::text, 'Epic', 'epic', '#904EE2', '큰 단위 작업', 'app-server', 'TSH', 'SEED-V320',
        'SeedIssueType', 'None', 'Usable', now(), now(), 'flyway', 'flyway')
ON CONFLICT (TYPE_NM) DO NOTHING;

-- D5: ddl-auto=update 가 text 컬럼을 varchar(255) 로 바꿔 255자를 넘는 내용이 저장되지 않았다.
--     본 테이블과 Envers 이력 테이블(*_HIST, ddl-auto 가 만든 곳에만 있음)을 text 로 되돌린다.
--     엔티티에도 columnDefinition = "text" 를 넣어 다시 좁혀지지 않게 한다.
ALTER TABLE IF EXISTS SN_CIRA_ISSUE ALTER COLUMN CONTENT TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_ISSUE_HIST ALTER COLUMN CONTENT TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_COMMENT ALTER COLUMN CONTENT TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_COMMENT_HIST ALTER COLUMN CONTENT TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_NOTIFICATION ALTER COLUMN MSG TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_NOTIFICATION_HIST ALTER COLUMN MSG TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_PROJECT ALTER COLUMN DESCR TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_PROJECT_HIST ALTER COLUMN DESCR TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_GIT_COMMIT ALTER COLUMN MSG TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_GIT_COMMIT_HIST ALTER COLUMN MSG TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_GIT_PR ALTER COLUMN DESCR TYPE TEXT;
ALTER TABLE IF EXISTS SN_CIRA_GIT_PR_HIST ALTER COLUMN DESCR TYPE TEXT;
