-- ayaan: Claude 플러그인 자동 설치 목록 (형식: 플러그인@마켓플레이스|git URL, 쉼표 구분)
INSERT INTO SN_AYN_CONFIG (CONFIG_KEY, CONFIG_VALUE, SRV_ID, TENANT, TRACE_ID, EVNT_NM, PREV_EVNT_NM, USE_STAT_CD, CREATED_AT, MODIFIED_AT, CREATED_BY, MODIFIED_BY)
VALUES ('CLAUDE_PLUGINS',
        'ponytail@ponytail|https://github.com/JCAO98/PonyTaill_AgentSkill.git,superpowers@superpowers-marketplace|https://github.com/obra/superpowers-marketplace.git',
        'app-server', 'TSH', 'SEED-V520', 'SeedAyaan', 'None', 'Usable', now(), now(), 'flyway', 'flyway')
ON CONFLICT (CONFIG_KEY) DO NOTHING;
