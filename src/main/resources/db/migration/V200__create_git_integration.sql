-- =============================================================
-- V200: Git Integration Tables
-- Tables: SN_CIRA_GIT_REPO, SN_CIRA_GIT_COMMIT, SN_CIRA_GIT_PR
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_GIT_REPO: Git 저장소 연동 정보
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_GIT_REPO
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
    REPO_NM VARCHAR
(
    255
) NOT NULL,
    REPO_URL VARCHAR
(
    500
) NOT NULL,
    PROVIDER VARCHAR
(
    20
) NOT NULL, -- GITHUB | GITLAB
    DEFAULT_BRANCH VARCHAR
(
    100
) NOT NULL DEFAULT 'main',
    WEBHOOK_SECRET VARCHAR
(
    500
),
    ACCESS_TOKEN_ENC VARCHAR
(
    1000
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
    CONSTRAINT pk_ciraGitRepo PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraGitRepo_01 UNIQUE
(
    PROJECT_ID,
    REPO_URL
)
    );

-- ---------------------------------------------------------------
-- SN_CIRA_GIT_COMMIT: Git 커밋 이력
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_GIT_COMMIT
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    REPO_ID VARCHAR
(
    100
) NOT NULL,
    ISSUE_ID VARCHAR
(
    100
), -- nullable FK → SN_CIRA_ISSUE
    COMMIT_HASH VARCHAR
(
    40
) NOT NULL,
    MSG TEXT,
    AUTHOR_NM VARCHAR
(
    100
),
    AUTHOR_EMAIL VARCHAR
(
    255
),
    COMMIT_DT TIMESTAMP WITH TIME ZONE NOT NULL,
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
    CONSTRAINT pk_ciraGitCommit PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraGitCommit_01 UNIQUE
(
    REPO_ID,
    COMMIT_HASH
),
    CONSTRAINT fk_ciraGitCommit_repo
    FOREIGN KEY
(
    REPO_ID
) REFERENCES SN_CIRA_GIT_REPO
(
    OBJ_ID
)
    );

-- ---------------------------------------------------------------
-- SN_CIRA_GIT_PR: Git Pull Request 이력
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_GIT_PR
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    REPO_ID VARCHAR
(
    100
) NOT NULL,
    ISSUE_ID VARCHAR
(
    100
), -- nullable FK → SN_CIRA_ISSUE
    PR_NO INTEGER NOT NULL,
    TITLE VARCHAR
(
    500
) NOT NULL,
    DESCR TEXT,
    PR_STAT VARCHAR
(
    20
) NOT NULL, -- open | closed | merged
    SRC_BRANCH VARCHAR
(
    200
),
    TGT_BRANCH VARCHAR
(
    200
),
    AUTHOR_NM VARCHAR
(
    100
),
    AUTHOR_EMAIL VARCHAR
(
    255
),
    MERGED_AT TIMESTAMP WITH TIME ZONE,
    CLOSED_AT TIMESTAMP WITH TIME ZONE,
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
    CONSTRAINT pk_ciraGitPr PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraGitPr_01 UNIQUE
(
    REPO_ID,
    PR_NO
),
    CONSTRAINT fk_ciraGitPr_repo
    FOREIGN KEY
(
    REPO_ID
) REFERENCES SN_CIRA_GIT_REPO
(
    OBJ_ID
)
    );

-- ---------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ciraGitRepo_projectId ON SN_CIRA_GIT_REPO (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraGitCommit_repoId ON SN_CIRA_GIT_COMMIT (REPO_ID);
CREATE INDEX IF NOT EXISTS idx_ciraGitCommit_issueId ON SN_CIRA_GIT_COMMIT (ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraGitPr_repoId ON SN_CIRA_GIT_PR (REPO_ID);
CREATE INDEX IF NOT EXISTS idx_ciraGitPr_issueId ON SN_CIRA_GIT_PR (ISSUE_ID);
