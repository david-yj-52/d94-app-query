-- =============================================================
-- V230: 버전/마일스톤 관리 테이블
-- Tables: SN_CIRA_VERSION, SN_CIRA_ISSUE_VERSION,
--         SN_CIRA_MILESTONE, SN_CIRA_MILESTONE_ISSUE
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_VERSION: 프로젝트 버전 정의
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_VERSION
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
    VERSION_NM VARCHAR
(
    50
) NOT NULL,
    DESCR TEXT,
    STATUS VARCHAR
(
    50
) NOT NULL DEFAULT 'UNRELEASED',
    PLAN_REL_DT DATE,
    RELEASED_AT TIMESTAMP WITH TIME ZONE,
                              -- BaseModel columns
                              SRV_ID VARCHAR (100),
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
    MODIFIED_AT TIMESTAMP
                          WITH TIME ZONE,
                              CREATED_BY VARCHAR (100),
    MODIFIED_BY VARCHAR
(
    100
),
    CONSTRAINT pk_ciraVersion PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraVersion_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
),
    CONSTRAINT uk_ciraVersion_01
    UNIQUE
(
    PROJECT_ID,
    VERSION_NM
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraVersion_project ON SN_CIRA_VERSION (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraVersion_status ON SN_CIRA_VERSION (PROJECT_ID, STATUS);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_VERSION: 이슈-버전 연관
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_VERSION
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
    VERSION_ID VARCHAR
(
    100
) NOT NULL,
    REL_TYPE VARCHAR
(
    50
) NOT NULL,
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
    CONSTRAINT pk_ciraIssueVersion PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueVersion_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueVersion_version
    FOREIGN KEY
(
    VERSION_ID
) REFERENCES SN_CIRA_VERSION
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssueVersion_01
    UNIQUE
(
    ISSUE_ID,
    VERSION_ID,
    REL_TYPE
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueVersion_issue ON SN_CIRA_ISSUE_VERSION (ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueVersion_version ON SN_CIRA_ISSUE_VERSION (VERSION_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_MILESTONE: 마일스톤 정의
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_MILESTONE
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
    MILESTONE_NM VARCHAR
(
    200
) NOT NULL,
    DESCR TEXT,
    DUE_DT DATE,
    STATUS VARCHAR
(
    50
) NOT NULL DEFAULT 'OPEN',
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
    CONSTRAINT pk_ciraMilestone PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraMilestone_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraMilestone_project ON SN_CIRA_MILESTONE (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraMilestone_status ON SN_CIRA_MILESTONE (PROJECT_ID, STATUS);

-- ---------------------------------------------------------------
-- SN_CIRA_MILESTONE_ISSUE: 마일스톤-이슈 연관
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_MILESTONE_ISSUE
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    MILESTONE_ID VARCHAR
(
    100
) NOT NULL,
    ISSUE_ID VARCHAR
(
    100
) NOT NULL,
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
    CONSTRAINT pk_ciraMilestoneIssue PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraMilestoneIssue_milestone
    FOREIGN KEY
(
    MILESTONE_ID
) REFERENCES SN_CIRA_MILESTONE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraMilestoneIssue_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT uk_ciraMilestoneIssue_01
    UNIQUE
(
    MILESTONE_ID,
    ISSUE_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraMilestoneIssue_milestone ON SN_CIRA_MILESTONE_ISSUE (MILESTONE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraMilestoneIssue_issue ON SN_CIRA_MILESTONE_ISSUE (ISSUE_ID);
