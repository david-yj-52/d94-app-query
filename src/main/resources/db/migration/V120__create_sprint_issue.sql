-- =============================================================
-- V120: Phase 1 스프린트 & 이슈
-- Tables: SN_CIRA_SPRINT, SN_CIRA_ISSUE
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_SPRINT: 스프린트 정의
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_SPRINT
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PROJECT_ID VARCHAR
(
    100
) NOT NULL,
    SPRINT_NM VARCHAR
(
    200
) NOT NULL,
    GOAL TEXT,
    START_DT DATE,
    END_DT DATE,
    SPRINT_STAT VARCHAR
(
    20
) NOT NULL DEFAULT 'PLANNING', -- PLANNING | ACTIVE | CLOSED
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
    CONSTRAINT pk_ciraSprint PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraSprint_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraSprint_project ON SN_CIRA_SPRINT (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraSprint_stat ON SN_CIRA_SPRINT (PROJECT_ID, SPRINT_STAT);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE: 이슈 (핵심 엔티티)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PROJECT_ID VARCHAR
(
    100
) NOT NULL,
    SPRINT_ID VARCHAR
(
    100
),
    ISSUE_KEY VARCHAR
(
    30
) NOT NULL,
    TITLE VARCHAR
(
    500
) NOT NULL,
    CONTENT TEXT,
    ISSUE_TYPE_ID VARCHAR
(
    100
) NOT NULL,
    STATUS_ID VARCHAR
(
    100
) NOT NULL,
    PRIORITY VARCHAR
(
    20
) NOT NULL DEFAULT 'MEDIUM', -- HIGHEST | HIGH | MEDIUM | LOW | LOWEST
    STORY_PNT NUMERIC
(
    6,
    2
),
    ASSIGNEE_ID VARCHAR
(
    100
),
    REPORTER_ID VARCHAR
(
    100
) NOT NULL,
    DUE_DT DATE,
    STARTED_AT TIMESTAMP,
    RESOLVED_AT TIMESTAMP,
    DELETED_AT TIMESTAMP,
    SEARCH_VECTOR TSVECTOR, -- Full-Text Search 벡터 (GIN 인덱스)
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
    CONSTRAINT pk_ciraIssue PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssue_01 UNIQUE
(
    ISSUE_KEY
),
    CONSTRAINT fk_ciraIssue_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssue_sprint
    FOREIGN KEY
(
    SPRINT_ID
) REFERENCES SN_CIRA_SPRINT
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssue_issueType
    FOREIGN KEY
(
    ISSUE_TYPE_ID
) REFERENCES SN_CIRA_CIRA_ISSUE_TYPE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssue_status
    FOREIGN KEY
(
    STATUS_ID
) REFERENCES SN_CIRA_ISSUE_STATUS
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssue_project ON SN_CIRA_ISSUE (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssue_sprint ON SN_CIRA_ISSUE (SPRINT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssue_status ON SN_CIRA_ISSUE (STATUS_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssue_assignee ON SN_CIRA_ISSUE (ASSIGNEE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssue_assignee ON SN_CIRA_ISSUE (ASSIGNEE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssue_reporter ON SN_CIRA_ISSUE (REPORTER_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssue_priority ON SN_CIRA_ISSUE (PROJECT_ID, PRIORITY);