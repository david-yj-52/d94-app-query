-- =============================================================
-- 기준정보 설정 (SN_SETTING) + 시스템 역할 시드 (GS_ROLE)
-- =============================================================

CREATE TABLE IF NOT EXISTS SN_SETTING
(
    OBJ_ID        VARCHAR(100) NOT NULL,
    SETTING_KEY   VARCHAR(100) NOT NULL,
    SETTING_VALUE VARCHAR(500),
    -- BaseModel
    SRV_ID        VARCHAR(50)  NOT NULL,
    TENANT        VARCHAR(50)  NOT NULL,
    TRACE_ID      VARCHAR(100) NOT NULL,
    USE_STAT_CD   VARCHAR(40)  NOT NULL DEFAULT 'Usable',
    EVNT_NM       VARCHAR(100) NOT NULL,
    PREV_EVNT_NM  VARCHAR(100) NOT NULL,
    ACT_CD        VARCHAR(255),
    ACT_CM        VARCHAR(255),
    CREATED_BY    VARCHAR(40),
    CREATED_AT    TIMESTAMP    NOT NULL DEFAULT NOW(),
    MODIFIED_BY   VARCHAR(40),
    MODIFIED_AT   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT PK_SN_SETTING PRIMARY KEY (OBJ_ID),
    CONSTRAINT uk_sn_setting_01 UNIQUE (TENANT, SETTING_KEY)
);

COMMENT ON TABLE SN_SETTING IS '기준정보 설정 (테넌트별 키-값, 예: general.logo.text)';

-- 시스템 역할. GS_USER_ROLE.ROLE_ID 는 GS_ROLE.OBJ_ID 를 가리킨다
INSERT INTO GS_ROLE (OBJ_ID, ROLE_NM, DESCR, TRACE_ID, EVNT_NM)
VALUES ('ROLE-ADMIN', 'ADMIN', '시스템 관리자', 'SEED-V540', 'SeedRole'),
       ('ROLE-USER', 'USER', '일반 사용자', 'SEED-V540', 'SeedRole')
ON CONFLICT (ROLE_NM) DO NOTHING;

-- 첫 관리자 지정 (수동 실행):
-- INSERT INTO GS_USER_ROLE (OBJ_ID, USER_ID, ROLE_ID, TRACE_ID, EVNT_NM)
-- SELECT gen_random_uuid()::text, OBJ_ID, 'ROLE-ADMIN', 'MANUAL', 'GrantAdmin' FROM GS_USER WHERE EMAIL = '<관리자 이메일>';
