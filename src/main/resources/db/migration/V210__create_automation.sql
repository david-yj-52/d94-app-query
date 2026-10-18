-- =============================================================
-- V210: 자동화 엔진 테이블
-- Tables: SN_CIRA_AUTO_RULE, SN_CIRA_AUTO_EXEC
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_AUTO_RULE: 자동화 규칙 정의
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_AUTO_RULE
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
    RULE_NM VARCHAR
(
    200
) NOT NULL,
    TRIGGER_TYPE VARCHAR
(
    100
) NOT NULL,
    TRIGGER_CONFIG JSONB,
    COND_CONFIG JSONB,
    ACTION_TYPE VARCHAR
(
    100
) NOT NULL,
    ACTION_CONFIG JSONB,
    IS_ACTIVE BOOLEAN NOT NULL DEFAULT TRUE,
    SORT_ORD INTEGER NOT NULL DEFAULT 0,
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
    CONSTRAINT pk_ciraAutoRule PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraAutoRule_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraAutoRule_project ON SN_CIRA_AUTO_RULE (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraAutoRule_active ON SN_CIRA_AUTO_RULE (PROJECT_ID, IS_ACTIVE);

-- ---------------------------------------------------------------
-- SN_CIRA_AUTO_EXECUTION: 자동화 규칙 실행 이력
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_AUTO_EXECUTION
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    RULE_ID VARCHAR
(
    100
) NOT NULL,
    ISSUE_ID VARCHAR
(
    100
),
    EXEC_STAT VARCHAR
(
    50
) NOT NULL, -- SUCCESS | FAILURE | SKIPPED
    EXECUTED_AT TIMESTAMP WITH TIME ZONE NOT NULL,
                              ERR_MSG TEXT,
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
    CONSTRAINT pk_ciraAutoExecution PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraAutoExecution_rule
    FOREIGN KEY
(
    RULE_ID
) REFERENCES SN_CIRA_AUTO_RULE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraAutoExecution_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraAutoExec_rule ON SN_CIRA_AUTO_EXECUTION (RULE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraAutoExec_issue ON SN_CIRA_AUTO_EXECUTION (ISSUE_ID);
