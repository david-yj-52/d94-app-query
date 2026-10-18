-- =============================================================
-- V100: Phase 1 Core - 프로젝트 및 멤버
-- Tables: SN_CIRA_PROJECT, SN_CIRA_PROJECT_MEMBER
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_PROJECT: 프로젝트 정의
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_PROJECT
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PROJECT_KEY VARCHAR
(
    20
) NOT NULL,
    PROJECT_NM VARCHAR
(
    200
) NOT NULL,
    DESCR TEXT,
    PROJECT_TYPE VARCHAR
(
    30
) NOT NULL, -- SCRUM | KANBAN | BASIC
    OWNER_ID VARCHAR
(
    100
),
    ISSUE_SEQUENCE INTEGER NOT NULL DEFAULT 0,
    DELETED_AT TIMESTAMP,
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
    CONSTRAINT pk_ciraProject PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraProject_01 UNIQUE
(
    PROJECT_KEY
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraProject_owner ON SN_CIRA_PROJECT (OWNER_ID);
CREATE INDEX IF NOT EXISTS idx_ciraProject_type ON SN_CIRA_PROJECT (PROJECT_TYPE, USE_STAT_CD);

-- ---------------------------------------------------------------
-- SN_CIRA_PROJECT_MEMBER: 프로젝트 멤버
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_PROJECT_MEMBER
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
    USER_ID VARCHAR
(
    100
) NOT NULL,
    ROLE VARCHAR
(
    50
) NOT NULL, -- OWNER | ADMIN | MEMBER | VIEWER
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
    CONSTRAINT pk_ciraProjectMember PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraProjectMember_01 UNIQUE
(
    PROJECT_ID,
    USER_ID
),
    CONSTRAINT fk_ciraProjectMember_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraProjectMember_project ON SN_CIRA_PROJECT_MEMBER (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraProjectMember_user ON SN_CIRA_PROJECT_MEMBER (USER_ID);
