-- =============================================================
-- V140: Phase 2 이슈 관계 / 이력 / 감시자
-- Tables: SN_CIRA_ISSUE_LINK, SN_CIRA_ISSUE_SUBTASK,
--         SN_CIRA_ISSUE_LOG, SN_CIRA_ISSUE_WATCHER
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_LINK: 이슈 간 연관 관계
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_LINK
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    SRC_ISSUE_ID VARCHAR
(
    100
) NOT NULL,
    TGT_ISSUE_ID VARCHAR
(
    100
) NOT NULL,
    LINK_TYPE VARCHAR
(
    50
) NOT NULL, -- blocks | is-blocked-by | relates-to | duplicates | etc.
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
    CONSTRAINT pk_ciraIssueLink PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssueLink_01 UNIQUE
(
    SRC_ISSUE_ID,
    TGT_ISSUE_ID,
    LINK_TYPE
),
    CONSTRAINT fk_ciraIssueLink_src
    FOREIGN KEY
(
    SRC_ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueLink_tgt
    FOREIGN KEY
(
    TGT_ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueLink_src ON SN_CIRA_ISSUE_LINK (SRC_ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueLink_tgt ON SN_CIRA_ISSUE_LINK (TGT_ISSUE_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_SUBTASK: 이슈 서브태스크 관계
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_SUBTASK
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PARENT_ISSUE_ID VARCHAR
(
    100
) NOT NULL,
    CHILD_ISSUE_ID VARCHAR
(
    100
) NOT NULL,
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
    CONSTRAINT pk_ciraIssueSubtask PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssueSubtask_01 UNIQUE
(
    PARENT_ISSUE_ID,
    CHILD_ISSUE_ID
),
    CONSTRAINT fk_ciraIssueSubtask_parent
    FOREIGN KEY
(
    PARENT_ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueSubtask_child
    FOREIGN KEY
(
    CHILD_ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueSubtask_parent ON SN_CIRA_ISSUE_SUBTASK (PARENT_ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueSubtask_child ON SN_CIRA_ISSUE_SUBTASK (CHILD_ISSUE_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_LOG: 이슈 필드 변경 이력
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_LOG
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
    FIELD_NM VARCHAR
(
    100
) NOT NULL,
    OLD_VAL TEXT,
    NEW_VAL TEXT,
    CHANGED_BY VARCHAR
(
    100
) NOT NULL,
    CHANGED_AT TIMESTAMP NOT NULL,
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
    CONSTRAINT pk_ciraIssueLog PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssueLog_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueLog_issue ON SN_CIRA_ISSUE_LOG (ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueLog_changedAt ON SN_CIRA_ISSUE_LOG (ISSUE_ID, CHANGED_AT DESC);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_WATCHER: 이슈 감시자
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_WATCHER
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
    USER_ID VARCHAR
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
    CONSTRAINT pk_ciraIssueWatcher PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssueWatcher_01 UNIQUE
(
    ISSUE_ID,
    USER_ID
),
    CONSTRAINT fk_ciraIssueWatcher_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssueWatcher_issue ON SN_CIRA_ISSUE_WATCHER (ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssueWatcher_user ON SN_CIRA_ISSUE_WATCHER (USER_ID);
