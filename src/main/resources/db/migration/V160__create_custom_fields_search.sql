-- =============================================================
-- V160: Phase 3 사용자 정의 필드 & 검색 인덱스 & 저장 필터
-- Tables: SN_CIRA_CUSTOM_FIELD, SN_CIRA_ISSUE_CF_VALUE,
--         SN_CIRA_ISSUE_SEARCH_IDX, SN_CIRA_SAVED_FILTER
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_CUSTOM_FIELD: 사용자 정의 필드 정의 (프로젝트별 또는 전역)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_CUSTOM_FIELD
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PROJECT_ID VARCHAR
(
    100
), -- NULL → 전역 필드
    FIELD_NM VARCHAR
(
    100
) NOT NULL,
    FIELD_TYPE VARCHAR
(
    30
) NOT NULL, -- TEXT | NUMBER | DATE | SELECT | MULTI_SELECT | USER
    REQUIRED_YN VARCHAR
(
    1
) NOT NULL DEFAULT 'N',
    OPTIONS JSONB, -- SELECT / MULTI_SELECT 옵션 목록
    SORT_ORD SMALLINT NOT NULL DEFAULT 0,
    -- BaseModel columns
    SRV_ID VARCHAR
(
    100
),
    TENANT VARCHAR
(
    100
),
    TRACE_ID VARCHAR
(
    100
),
    EVNT_NM VARCHAR
(
    100
),
    PREV_EVNT_NM VARCHAR
(
    100
),
    ACT_CD VARCHAR
(
    100
),
    ACT_CM TEXT,
    USE_STAT_CD VARCHAR
(
    20
),
    CREATED_AT TIMESTAMP WITH TIME ZONE,
    MODIFIED_AT TIMESTAMP WITH TIME ZONE,
                              CREATED_BY VARCHAR (100),
    MODIFIED_BY VARCHAR
(
    100
),
    CONSTRAINT pk_ciraCustomField PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraCustomField_01 UNIQUE
(
    PROJECT_ID,
    FIELD_NM
),
    CONSTRAINT fk_ciraCustomField_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraCustomField_project ON SN_CIRA_CUSTOM_FIELD (PROJECT_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_CF_VALUE: 이슈 사용자 정의 필드 값
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_CF_VALUE
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    ISSUE_ID VARCHAR
(
    100
) NOT NULL,
    CUSTOM_FIELD_ID VARCHAR
(
    100
) NOT NULL,
    VAL_TEXT TEXT,
    VAL_NUMBER NUMERIC
(
    20,
    6
),
    VAL_DT DATE,
    VAL_JSON JSONB,
    -- BaseModel columns
    SRV_ID VARCHAR
(
    100
),
    TENANT VARCHAR
(
    100
),
    TRACE_ID VARCHAR
(
    100
),
    EVNT_NM VARCHAR
(
    100
),
    PREV_EVNT_NM VARCHAR
(
    100
),
    ACT_CD VARCHAR
(
    100
),
    ACT_CM TEXT,
    USE_STAT_CD VARCHAR
(
    20
),
    CREATED_AT TIMESTAMP WITH TIME ZONE,
    MODIFIED_AT TIMESTAMP WITH TIME ZONE,
                              CREATED_BY VARCHAR (100),
    MODIFIED_BY VARCHAR
(
    100
),
    CONSTRAINT pk_ciraIssueCfValue PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssueCfValue_01 UNIQUE
(
    ISSUE_ID,
    CUSTOM_FIELD_ID
),
    CONSTRAINT fk_ciraIssueCfValue_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueCfValue_field
    FOREIGN KEY
(
    CUSTOM_FIELD_ID
) REFERENCES SN_CIRA_CUSTOM_FIELD
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueCfValue_issue ON SN_CIRA_ISSUE_CF_VALUE (ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueCfValue_field ON SN_CIRA_ISSUE_CF_VALUE (CUSTOM_FIELD_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_SEARCH_IDX: 이슈 전문 검색 인덱스 (tsvector)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_SEARCH_IDX
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    ISSUE_ID VARCHAR
(
    100
) NOT NULL,
    SEARCH_VEC TSVECTOR,
    -- BaseModel columns
    SRV_ID VARCHAR
(
    100
),
    TENANT VARCHAR
(
    100
),
    TRACE_ID VARCHAR
(
    100
),
    EVNT_NM VARCHAR
(
    100
),
    PREV_EVNT_NM VARCHAR
(
    100
),
    ACT_CD VARCHAR
(
    100
),
    ACT_CM TEXT,
    USE_STAT_CD VARCHAR
(
    20
),
    CREATED_AT TIMESTAMP WITH TIME ZONE,
    MODIFIED_AT TIMESTAMP WITH TIME ZONE,
                              CREATED_BY VARCHAR (100),
    MODIFIED_BY VARCHAR
(
    100
),
    CONSTRAINT pk_ciraIssueSearchIdx PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssueSearchIdx_01 UNIQUE
(
    ISSUE_ID
),
    CONSTRAINT fk_ciraIssueSearchIdx_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueSearchIdx_vec ON SN_CIRA_ISSUE_SEARCH_IDX USING GIN (SEARCH_VEC);

-- ---------------------------------------------------------------
-- SN_CIRA_SAVED_FILTER: 저장된 이슈 검색 필터
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_SAVED_FILTER
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    USER_ID VARCHAR
(
    100
) NOT NULL,
    PROJECT_ID VARCHAR
(
    100
),
    FILTER_NM VARCHAR
(
    200
) NOT NULL,
    JQL_QUERY TEXT,
    SHARED_YN VARCHAR
(
    1
) NOT NULL DEFAULT 'N',
    -- BaseModel columns
    SRV_ID VARCHAR
(
    100
),
    TENANT VARCHAR
(
    100
),
    TRACE_ID VARCHAR
(
    100
),
    EVNT_NM VARCHAR
(
    100
),
    PREV_EVNT_NM VARCHAR
(
    100
),
    ACT_CD VARCHAR
(
    100
),
    ACT_CM TEXT,
    USE_STAT_CD VARCHAR
(
    20
),
    CREATED_AT TIMESTAMP WITH TIME ZONE,
    MODIFIED_AT TIMESTAMP WITH TIME ZONE,
                              CREATED_BY VARCHAR (100),
    MODIFIED_BY VARCHAR
(
    100
),
    CONSTRAINT pk_ciraSavedFilter PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraSavedFilter_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraSavedFilter_user ON SN_CIRA_SAVED_FILTER (USER_ID);
CREATE INDEX IF NOT EXISTS idx_ciraSavedFilter_project ON SN_CIRA_SAVED_FILTER (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraSavedFilter_shared ON SN_CIRA_SAVED_FILTER (SHARED_YN);
