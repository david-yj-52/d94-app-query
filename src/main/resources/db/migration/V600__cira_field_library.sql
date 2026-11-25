-- CIRA 사용자 정의 필드 라이브러리: 소속(전역·타입·프로젝트)·출처·작성자, 참조 → 복사 이관
-- 설계: d94-note docs/superpowers/specs/2026-11-25-cira-field-library-design.md
-- 소속: PROJECT_ID·PROJECT_TYPE_ID 둘 다 null = 전역, PROJECT_TYPE_ID = 타입, PROJECT_ID = 프로젝트.
-- 다시 실행해도 결과가 같게 쓴다 (CiraFieldLibraryMigrationIT 가 재실행으로 검증). $$ 블록 없이 문장만 쓴다.

ALTER TABLE SN_CIRA_CUSTOM_FIELD ADD COLUMN IF NOT EXISTS PROJECT_TYPE_ID VARCHAR(100);
ALTER TABLE SN_CIRA_CUSTOM_FIELD ADD COLUMN IF NOT EXISTS SOURCE_FIELD_ID VARCHAR(100); -- 복사해 온 원본. 원본이 지워져도 남도록 FK 없음
ALTER TABLE SN_CIRA_CUSTOM_FIELD ADD COLUMN IF NOT EXISTS CREATED_BY VARCHAR(100);      -- 만든 사용자 objId (null = 프로젝트 ADMIN 만 수정)
ALTER TABLE SN_CIRA_CUSTOM_FIELD DROP CONSTRAINT IF EXISTS fk_ciraCustomField_type;
ALTER TABLE SN_CIRA_CUSTOM_FIELD ADD CONSTRAINT fk_ciraCustomField_type
    FOREIGN KEY (PROJECT_TYPE_ID) REFERENCES SN_CIRA_PROJECT_TYPE (OBJ_ID) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_ciraCustomField_type ON SN_CIRA_CUSTOM_FIELD (PROJECT_TYPE_ID);

-- 이관: 전역 필드(old_id)를 가리키던 곳(scope_id)마다 복사본(new_id)
DROP TABLE IF EXISTS cf_map;
CREATE TEMP TABLE cf_map (old_id VARCHAR(100), scope_id VARCHAR(100), new_id VARCHAR(100));

-- 1. 타입 세트의 전역 참조 → 그 타입의 타입 필드
INSERT INTO cf_map (old_id, scope_id, new_id)
SELECT DISTINCT g.OBJ_ID, tf.PROJECT_TYPE_ID, gen_random_uuid()::text
FROM SN_CIRA_PROJECT_TYPE_FIELD tf
     JOIN SN_CIRA_CUSTOM_FIELD g ON tf.FIELD_KEY = 'cf:' || g.OBJ_ID
WHERE g.PROJECT_ID IS NULL AND g.PROJECT_TYPE_ID IS NULL;

INSERT INTO SN_CIRA_CUSTOM_FIELD (OBJ_ID, PROJECT_ID, PROJECT_TYPE_ID, FIELD_NM, FIELD_TYPE, REQUIRED_YN, OPTIONS, SORT_ORD,
                                  SOURCE_FIELD_ID, TENANT, USE_STAT_CD, EVNT_NM, PREV_EVNT_NM, CREATED_AT, MODIFIED_AT)
SELECT m.new_id, NULL, m.scope_id, g.FIELD_NM, g.FIELD_TYPE, g.REQUIRED_YN, g.OPTIONS, g.SORT_ORD,
       g.OBJ_ID, g.TENANT, 'Usable', 'CopyFieldToType', 'None', now(), now()
FROM cf_map m JOIN SN_CIRA_CUSTOM_FIELD g ON g.OBJ_ID = m.old_id;

UPDATE SN_CIRA_PROJECT_TYPE_FIELD tf SET FIELD_KEY = 'cf:' || m.new_id
FROM cf_map m
WHERE tf.PROJECT_TYPE_ID = m.scope_id AND tf.FIELD_KEY = 'cf:' || m.old_id;

UPDATE SN_CIRA_ISSUE_CF_VALUE v SET CUSTOM_FIELD_ID = m.new_id
FROM cf_map m, SN_CIRA_ISSUE i, SN_CIRA_PROJECT p
WHERE v.CUSTOM_FIELD_ID = m.old_id AND i.OBJ_ID = v.ISSUE_ID AND p.OBJ_ID = i.PROJECT_ID AND p.PROJECT_TYPE_ID = m.scope_id;

-- 2. 세트에도 같은 원본이 있던 프로젝트 추가 필드(유효 필드 계산에서 이미 가려져 있던 항목) → 지우기만
DELETE FROM SN_CIRA_PROJECT_FIELD pf
USING SN_CIRA_PROJECT p, cf_map m
WHERE p.OBJ_ID = pf.PROJECT_ID AND m.scope_id = p.PROJECT_TYPE_ID AND pf.FIELD_KEY = 'cf:' || m.old_id;

-- 3. 나머지 프로젝트 추가 필드의 전역 참조 → 그 프로젝트 필드 (같은 이름이 이미 있으면 " (가져옴)")
DELETE FROM cf_map;
INSERT INTO cf_map (old_id, scope_id, new_id)
SELECT DISTINCT g.OBJ_ID, pf.PROJECT_ID, gen_random_uuid()::text
FROM SN_CIRA_PROJECT_FIELD pf
     JOIN SN_CIRA_CUSTOM_FIELD g ON pf.FIELD_KEY = 'cf:' || g.OBJ_ID
WHERE g.PROJECT_ID IS NULL AND g.PROJECT_TYPE_ID IS NULL;

INSERT INTO SN_CIRA_CUSTOM_FIELD (OBJ_ID, PROJECT_ID, PROJECT_TYPE_ID, FIELD_NM, FIELD_TYPE, REQUIRED_YN, OPTIONS, SORT_ORD,
                                  SOURCE_FIELD_ID, TENANT, USE_STAT_CD, EVNT_NM, PREV_EVNT_NM, CREATED_AT, MODIFIED_AT)
SELECT m.new_id, m.scope_id, NULL,
       CASE WHEN EXISTS (SELECT 1 FROM SN_CIRA_CUSTOM_FIELD x WHERE x.PROJECT_ID = m.scope_id AND x.FIELD_NM = g.FIELD_NM)
            THEN left(g.FIELD_NM, 90) || ' (가져옴)' ELSE g.FIELD_NM END,
       g.FIELD_TYPE, g.REQUIRED_YN, g.OPTIONS, g.SORT_ORD,
       g.OBJ_ID, g.TENANT, 'Usable', 'CopyFieldToProject', 'None', now(), now()
FROM cf_map m JOIN SN_CIRA_CUSTOM_FIELD g ON g.OBJ_ID = m.old_id;

UPDATE SN_CIRA_PROJECT_FIELD pf SET FIELD_KEY = 'cf:' || m.new_id
FROM cf_map m
WHERE pf.PROJECT_ID = m.scope_id AND pf.FIELD_KEY = 'cf:' || m.old_id;

UPDATE SN_CIRA_ISSUE_CF_VALUE v SET CUSTOM_FIELD_ID = m.new_id
FROM cf_map m, SN_CIRA_ISSUE i
WHERE v.CUSTOM_FIELD_ID = m.old_id AND i.OBJ_ID = v.ISSUE_ID AND i.PROJECT_ID = m.scope_id;

DROP TABLE cf_map;
