-- =============================================================
-- V150: Phase 2 댓글 & 첨부파일
-- Tables: SN_CIRA_COMMENT, SN_CIRA_COMMENT_REACTION,
--         SN_CIRA_ATTACHMENT
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_COMMENT: 이슈 댓글
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_COMMENT
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
    AUTHOR_ID VARCHAR
(
    100
) NOT NULL,
    PARENT_ID VARCHAR
(
    100
), -- NULL → 최상위 댓글 / NOT NULL → 답글
    CONTENT TEXT,
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
    CONSTRAINT pk_ciraComment PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraComment_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraComment_parent
    FOREIGN KEY
(
    PARENT_ID
) REFERENCES SN_CIRA_COMMENT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraComment_issue ON SN_CIRA_COMMENT (ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraComment_author ON SN_CIRA_COMMENT (AUTHOR_ID);
CREATE INDEX IF NOT EXISTS idx_ciraComment_parent ON SN_CIRA_COMMENT (PARENT_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_COMMENT_REACTION: 댓글 이모지 반응
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_COMMENT_REACTION
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    COMMENT_ID VARCHAR
(
    100
) NOT NULL,
    USER_ID VARCHAR
(
    100
) NOT NULL,
    REACTION_TYPE VARCHAR
(
    30
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
    CONSTRAINT pk_ciraCommentReaction PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraCommentReaction_01 UNIQUE
(
    COMMENT_ID,
    USER_ID,
    REACTION_TYPE
),
    CONSTRAINT fk_ciraCommentReaction_comment
    FOREIGN KEY
(
    COMMENT_ID
) REFERENCES SN_CIRA_COMMENT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraCommentReaction_comment ON SN_CIRA_COMMENT_REACTION (COMMENT_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_ATTACHMENT: 이슈/댓글 첨부파일
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_ATTACHMENT
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
    COMMENT_ID VARCHAR
(
    100
),
    FILE_NM VARCHAR
(
    255
) NOT NULL,
    FILE_PATH VARCHAR
(
    1000
) NOT NULL, -- S3 key or storage path
    FILE_SIZE BIGINT,
    MIME_TYPE VARCHAR
(
    100
),
    UPLOADED_BY VARCHAR
(
    100
), -- 업로드한 사용자 ID (FK → GS_USER)
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
    CONSTRAINT pk_ciraAttachment PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraAttachment_issue
    FOREIGN KEY
(
    ISSUE_ID
) REFERENCES SN_CIRA_ISSUE
(
    OBJ_ID
),
    CONSTRAINT fk_ciraAttachment_comment
    FOREIGN KEY
(
    COMMENT_ID
) REFERENCES SN_CIRA_COMMENT
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraAttachment_issue ON SN_CIRA_ATTACHMENT (ISSUE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraAttachment_comment ON SN_CIRA_ATTACHMENT (COMMENT_ID);