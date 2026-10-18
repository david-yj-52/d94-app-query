-- =============================================================
-- V170: Phase 3 알림 & 감사 로그
-- Tables: SN_CIRA_NOTIFICATION, SN_CIRA_NOTIF_PREF,
--         SN_CIRA_AUDIT_LOG
-- =============================================================

-- ---------------------------------------------------------------
-- SN_CIRA_NOTIFICATION: 사용자 알림
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_NOTIFICATION
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    USER_ID VARCHAR
(
    100
) NOT NULL,
    NOTIF_TYPE VARCHAR
(
    50
) NOT NULL,
    TITLE VARCHAR
(
    300
) NOT NULL,
    MSG TEXT,
    RESOURCE_TYPE VARCHAR
(
    50
),
    RESOURCE_ID VARCHAR
(
    100
),
    READ_YN VARCHAR
(
    1
) NOT NULL DEFAULT 'N',
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
    CONSTRAINT pk_ciraNotification PRIMARY KEY
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraNotification_user ON SN_CIRA_NOTIFICATION (USER_ID);
CREATE INDEX IF NOT EXISTS idx_ciraNotification_userRead ON SN_CIRA_NOTIFICATION (USER_ID, READ_YN);
CREATE INDEX IF NOT EXISTS idx_ciraNotification_resource ON SN_CIRA_NOTIFICATION (RESOURCE_TYPE, RESOURCE_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_NOTIF_PREF: 알림 수신 설정 (채널 × 이벤트 유형별)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_NOTIF_PREF
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    USER_ID VARCHAR
(
    100
) NOT NULL,
    CHANNEL VARCHAR
(
    30
) NOT NULL, -- IN_APP | EMAIL | SLACK
    EVENT_TYPE VARCHAR
(
    50
) NOT NULL,
    ENABLED_YN VARCHAR
(
    1
) NOT NULL DEFAULT 'Y',
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
    CONSTRAINT pk_ciraNotifPref PRIMARY KEY
(
    OBJ_ID
),
    CONSTRAINT uk_ciraNotifPref_01 UNIQUE
(
    USER_ID,
    CHANNEL,
    EVENT_TYPE
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraNotifPref_user ON SN_CIRA_NOTIF_PREF (USER_ID);

-- ---------------------------------------------------------------
-- SN_CIRA_AUDIT_LOG: 전체 시스템 감사 로그
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SN_CIRA_AUDIT_LOG
(
    OBJ_ID
    VARCHAR
(
    100
) NOT NULL,
    ACTION_TYPE VARCHAR
(
    30
) NOT NULL, -- CREATE | UPDATE | DELETE | LOGIN | etc.
    ACTOR_ID VARCHAR
(
    100
),
    RESOURCE_TYPE VARCHAR
(
    50
) NOT NULL,
    RESOURCE_ID VARCHAR
(
    100
) NOT NULL,
    OLD_SNAPSHOT JSONB,
    NEW_SNAPSHOT JSONB,
    IP_ADDR VARCHAR
(
    45
),
    USER_AGENT TEXT,
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
    CONSTRAINT pk_ciraAuditLog PRIMARY KEY
(
    OBJ_ID
)
    );

CREATE INDEX IF NOT EXISTS idx_ciraAuditLog_actor ON SN_CIRA_AUDIT_LOG (ACTOR_ID);
CREATE INDEX IF NOT EXISTS idx_ciraAuditLog_resource ON SN_CIRA_AUDIT_LOG (RESOURCE_TYPE, RESOURCE_ID);
CREATE INDEX IF NOT EXISTS idx_ciraAuditLog_crte ON SN_CIRA_AUDIT_LOG (CREATED_AT DESC);
