-- =============================================================
-- V220: Wiki 문서 기능 테이블
-- Tables: SN_CIRA_WIKI_PAGE, SN_CIRA_WIKI_PAGE_VER
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_WIKI_PAGE: Wiki 페이지
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_WIKI_PAGE
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
    PARENT_ID VARCHAR
(
    100
),
    TITLE VARCHAR
(
    500
) NOT NULL,
    CONTENT TEXT,
    CONTENT_HTML TEXT,
    AUTHOR_ID VARCHAR
(
    100
),
    SORT_ORDER INTEGER NOT NULL DEFAULT 0,
    VERSION INTEGER NOT NULL DEFAULT 1,
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
    CONSTRAINT pk_ciraWikiPage PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraWikiPage_project
    FOREIGN KEY
(
    PROJECT_ID
) REFERENCES SN_CIRA_PROJECT
(
    OBJ_ID
),
    CONSTRAINT fk_ciraWikiPage_parent
    FOREIGN KEY
(
    PARENT_ID
) REFERENCES SN_CIRA_WIKI_PAGE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraWikiPage_project ON SN_CIRA_WIKI_PAGE (PROJECT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraWikiPage_parent ON SN_CIRA_WIKI_PAGE (PARENT_ID);
CREATE INDEX IF NOT EXISTS idx_ciraWikiPage_projectUse ON SN_CIRA_WIKI_PAGE (PROJECT_ID, USE_STAT_CD);

-- ---------------------------------------------------------------
-- SN_CIRA_WIKI_PAGE_VER: Wiki 페이지 버전 이력
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_WIKI_PAGE_VER
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    PAGE_ID VARCHAR
(
    100
) NOT NULL,
    VERSION INTEGER NOT NULL,
    CONTENT TEXT,
    EDITED_BY VARCHAR
(
    100
),
    EDITED_AT TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
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
    MODIFIED_AT TIMESTAMP
                        WITH TIME ZONE,
                            CREATED_BY VARCHAR (100),
    MODIFIED_BY VARCHAR
(
    100
),
    CONSTRAINT pk_ciraWikiPageVer PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT fk_ciraWikiPageVer_page
    FOREIGN KEY
(
    PAGE_ID
) REFERENCES SN_CIRA_WIKI_PAGE
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraWikiPageVer_page ON SN_CIRA_WIKI_PAGE_VER (PAGE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraWikiPageVer_pageVer ON SN_CIRA_WIKI_PAGE_VER (PAGE_ID, VERSION);
