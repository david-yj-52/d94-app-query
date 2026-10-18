-- =============================================================
-- V110: Phase 1 이슈 설정 - 타입 / 상태 / 전환 규칙
-- Tables: SN_CIRA_CIRA_ISSUE_TYPE, SN_CIRA_ISSUE_STATUS,
--         SN_CIRA_ISSUE_TRANSITION
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_CIRA_ISSUE_TYPE: 이슈 유형 정의 (전역)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_CIRA_ISSUE_TYPE
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    TYPE_NM VARCHAR
(
    50
) NOT NULL,
    ICON VARCHAR
(
    100
),
    COLOR_CD VARCHAR
(
    7
),
    DESCR VARCHAR
(
    255
),
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
    CONSTRAINT pk_ciraCiraIssueType PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraCiraIssueType_01 UNIQUE
(
    TYPE_NM
)
    );

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_STATUS: 이슈 상태 (프로젝트별 또는 전역)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_STATUS
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PROJECT_ID VARCHAR
(
    100
), -- NULL → 전역 상태
    STATUS_NM VARCHAR
(
    50
) NOT NULL,
    CATEGORY VARCHAR
(
    20
) NOT NULL, -- TODO | IN_PROGRESS | DONE
    COLOR_CD VARCHAR
(
    7
),
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
    CONSTRAINT pk_ciraIssueStatus PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssueStatus_01 UNIQUE
(
    PROJECT_ID,
    STATUS_NM
),
    CONSTRAINT fk_ciraIssueStatus_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueStatus_project ON SN_CIRA_ISSUE_STATUS (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueStatus_category ON SN_CIRA_ISSUE_STATUS (CATEGORY);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_TRANSITION: 이슈 상태 전환 허용 규칙
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_TRANSITION
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PROJECT_ID VARCHAR
(
    100
), -- NULL → 전역 규칙
    FROM_STATUS_ID VARCHAR
(
    100
), -- NULL → 모든 상태에서 전환 가능
    TO_STATUS_ID VARCHAR
(
    100
) NOT NULL,
    ALLOW_YN VARCHAR
(
    1
) NOT NULL DEFAULT 'Y',
    REQUIRED_ROLE VARCHAR
(
    50
),
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
    CONSTRAINT pk_ciraIssueTransition PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueTransition_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueTransition_toStatus
    FOREIGN KEY
(
    TO_STATUS_ID
) REFERENCES SN_CIRA_ISSUE_STATUS
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueTransition_project ON SN_CIRA_ISSUE_TRANSITION (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueTransition_fromStatus ON SN_CIRA_ISSUE_TRANSITION (FROM_STATUS_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueTransition_toStatus ON SN_CIRA_ISSUE_TRANSITION (TO_STATUS_ID);
