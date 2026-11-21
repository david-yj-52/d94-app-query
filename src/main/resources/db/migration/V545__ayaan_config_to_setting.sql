-- =============================================================
-- ayaan 전역 정책을 기준정보(SN_SETTING)로 이관: CONFIG_KEY → 'ayaan.' || CONFIG_KEY
-- d94-app-ayaan 내부 API(/config)는 접두사를 뗀 키를 그대로 주고받는다 (AyaanService)
-- =============================================================

INSERT INTO SN_SETTING (OBJ_ID, SETTING_KEY, SETTING_VALUE, SRV_ID, TENANT, TRACE_ID, USE_STAT_CD, EVNT_NM, PREV_EVNT_NM,
                        CREATED_BY, CREATED_AT, MODIFIED_BY, MODIFIED_AT)
SELECT gen_random_uuid()::text, 'ayaan.' || CONFIG_KEY, CONFIG_VALUE, 'app-server', 'TSH', 'MIGRATE-V545', 'Usable',
       'MigrateAyaanConfig', 'None', 'flyway', now(), 'flyway', now()
FROM SN_AYN_CONFIG
ON CONFLICT (TENANT, SETTING_KEY) DO NOTHING;

DROP TABLE SN_AYN_CONFIG;
