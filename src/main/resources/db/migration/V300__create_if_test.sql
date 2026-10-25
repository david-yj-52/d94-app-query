-- =============================================================
-- V300: 인터페이스 테스트 (UI → UMD → 백엔드 → DB 통신 확인용)
-- Tables: SN_IF_TEST_ITEM
-- 이력 테이블(SN_IF_TEST_ITEM_HIST)은 Envers + ddl-auto 가 만든다 (기존 테이블과 동일).
-- =============================================================

CREATE TABLE IF NOT EXISTS SN_IF_TEST_ITEM
(
    OBJ_ID       VARCHAR(100) NOT NULL,
    ITEM_NM      VARCHAR(100) NOT NULL,
    CONTENT      TEXT,
    STATUS       VARCHAR(50)  NOT NULL DEFAULT 'NEW',
    -- BaseModel columns
    SRV_ID       VARCHAR(50)  NOT NULL,
    TENANT       VARCHAR(50)  NOT NULL,
    TRACE_ID     VARCHAR(100) NOT NULL,
    EVNT_NM      VARCHAR(100) NOT NULL,
    PREV_EVNT_NM VARCHAR(100) NOT NULL,
    ACT_CD       VARCHAR(255),
    ACT_CM       VARCHAR(255),
    USE_STAT_CD  VARCHAR(40)  NOT NULL,
    CREATED_AT   TIMESTAMP,
    MODIFIED_AT  TIMESTAMP,
    CREATED_BY   VARCHAR(255),
    MODIFIED_BY  VARCHAR(255),
    CONSTRAINT pk_ifTestItem PRIMARY KEY (OBJ_ID)
);

CREATE INDEX IF NOT EXISTS idx_ifTestItem_tenant_stat ON SN_IF_TEST_ITEM (TENANT, USE_STAT_CD, CREATED_AT);
