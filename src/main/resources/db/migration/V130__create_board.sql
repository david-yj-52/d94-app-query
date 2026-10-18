-- =============================================================
-- V130: Phase 2 보드 관리
-- Tables: SN_CIRA_BOARD, SN_CIRA_BOARD_COLUMN, SN_CIRA_ISSUE_POSITION
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_BOARD: 칸반/스크럼 보드 정의
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_BOARD
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
    BOARD_NM VARCHAR
(
    200
) NOT NULL,
    BOARD_TYPE VARCHAR
(
    20
) NOT NULL, -- KANBAN | SCRUM
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
    CONSTRAINT pk_ciraBoard PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraBoard_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraBoard_project ON SN_CIRA_BOARD (PROJECT_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_BOARD_COLUMN: 보드 컬럼 (상태 매핑 + WIP 제한)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_BOARD_COLUMN
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    BOARD_ID VARCHAR
(
    100
) NOT NULL,
    STATUS_ID VARCHAR
(
    100
) NOT NULL,
    COLUMN_NM VARCHAR
(
    100
) NOT NULL,
    WIP_LIMIT SMALLINT,
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
    CONSTRAINT pk_ciraBoardColumn PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraBoardColumn_01 UNIQUE
(
    BOARD_ID,
    SORT_ORD
),
    CONSTRAINT fk_ciraBoardColumn_board
    FOREIGN KEY
(
    BOARD_ID
) REFERENCES SN_CIRA_BOARD
(
    OBJ_ID
),
    CONSTRAINT fk_ciraBoardColumn_status
    FOREIGN KEY
(
    STATUS_ID
) REFERENCES SN_CIRA_ISSUE_STATUS
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraBoardColumn_board ON SN_CIRA_BOARD_COLUMN (BOARD_ID);
CREATE INDEX IF NOT EXISTS idx_ciraBoardColumn_status ON SN_CIRA_BOARD_COLUMN (STATUS_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_ISSUE_POSITION: 보드 내 이슈 위치 (드래그&드랍 순서)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ISSUE_POSITION
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
    COLUMN_ID VARCHAR
(
    100
) NOT NULL,
    RANK_STR VARCHAR
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
    CONSTRAINT pk_ciraIssuePosition PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraIssuePosition_01 UNIQUE
(
    ISSUE_ID,
    COLUMN_ID
),
    CONSTRAINT fk_ciraIssuePosition_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraIssuePosition_column
    FOREIGN KEY
(
    COLUMN_ID
) REFERENCES SN_CIRA_BOARD_COLUMN
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraIssuePosition_column ON SN_CIRA_ISSUE_POSITION (COLUMN_ID);
CREATE INDEX IF NOT EXISTS idx_ciraIssuePosition_issue ON SN_CIRA_ISSUE_POSITION (ISSUE_ID);
