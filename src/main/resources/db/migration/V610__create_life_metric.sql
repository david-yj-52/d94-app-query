-- =============================================================
-- life_metric (삶의 수치화) — 계획: Docs `life_metric 서비스 기획 및 개발 계획서`, CIRA LMTR-1
-- 계획서의 V580 은 ayaan 이 먼저 써서 V610 으로 둔다.
-- 원천: SN_LIFE_METRIC_TEMPLATE, SN_LIFE_METRIC (엔티티, BaseModel)
-- 값: SN_LIFE_METRIC_INPUT(기준값), SN_LIFE_METRIC_LOG(시계열) — JDBC 전용, 지표 삭제 시 CASCADE
-- BaseModel 컬럼 길이는 엔티티와 같게 둔다 (ddl-auto=update 가 엔티티 기준으로 ALTER 하므로).
-- =============================================================

CREATE TABLE IF NOT EXISTS SN_LIFE_METRIC_TEMPLATE
(
    OBJ_ID          VARCHAR(255) NOT NULL,
    CODE            VARCHAR(50)  NOT NULL,
    NAME            VARCHAR(200) NOT NULL,
    INPUT_SCHEMA    TEXT         NOT NULL, -- [{key,label,type(NUMBER|DATE),unit,mode(BASELINE|LOG)}]
    DEFAULT_FORMULA TEXT         NOT NULL,
    OUTPUT_UNIT     VARCHAR(50),
    SRV_ID          VARCHAR(50)  NOT NULL,
    TENANT          VARCHAR(50)  NOT NULL,
    TRACE_ID        VARCHAR(100) NOT NULL,
    EVNT_NM         VARCHAR(100) NOT NULL,
    PREV_EVNT_NM    VARCHAR(100) NOT NULL,
    ACT_CD          VARCHAR(255),
    ACT_CM          VARCHAR(255),
    USE_STAT_CD     VARCHAR(40)  NOT NULL,
    CREATED_AT      TIMESTAMP,
    MODIFIED_AT     TIMESTAMP,
    CREATED_BY      VARCHAR(255),
    MODIFIED_BY     VARCHAR(255),
    CONSTRAINT pk_lifeMetricTemplate PRIMARY KEY (OBJ_ID),
    CONSTRAINT ux_lifeMetricTemplate_code UNIQUE (CODE)
);

CREATE TABLE IF NOT EXISTS SN_LIFE_METRIC
(
    OBJ_ID       VARCHAR(255) NOT NULL,
    OWNER_ID     VARCHAR(200) NOT NULL,
    TEMPLATE_ID  VARCHAR(255) REFERENCES SN_LIFE_METRIC_TEMPLATE (OBJ_ID) ON DELETE SET NULL,
    CODE         VARCHAR(50)  NOT NULL,
    NAME         VARCHAR(200) NOT NULL,
    INPUT_SCHEMA TEXT         NOT NULL,
    FORMULA      TEXT         NOT NULL,
    OUTPUT_UNIT  VARCHAR(50),
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
    CONSTRAINT pk_lifeMetric PRIMARY KEY (OBJ_ID)
);
-- @code 참조의 키: 소유자 안에서 CODE 유일
CREATE UNIQUE INDEX IF NOT EXISTS ux_lifeMetric_code ON SN_LIFE_METRIC (TENANT, OWNER_ID, CODE);

CREATE TABLE IF NOT EXISTS SN_LIFE_METRIC_INPUT
(
    METRIC_ID   VARCHAR(255) NOT NULL REFERENCES SN_LIFE_METRIC (OBJ_ID) ON DELETE CASCADE,
    INPUT_KEY   VARCHAR(50)  NOT NULL,
    NUM_VALUE   DOUBLE PRECISION,
    TEXT_VALUE  VARCHAR(200),
    MODIFIED_AT TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_lifeMetricInput PRIMARY KEY (METRIC_ID, INPUT_KEY)
);

CREATE TABLE IF NOT EXISTS SN_LIFE_METRIC_LOG
(
    LOG_ID      BIGSERIAL PRIMARY KEY,
    METRIC_ID   VARCHAR(255)     NOT NULL REFERENCES SN_LIFE_METRIC (OBJ_ID) ON DELETE CASCADE,
    INPUT_KEY   VARCHAR(50)      NOT NULL,
    NUM_VALUE   DOUBLE PRECISION NOT NULL,
    NOTE        VARCHAR(500),
    RECORDED_AT TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lifeMetricLog_metric ON SN_LIFE_METRIC_LOG (METRIC_ID, RECORDED_AT DESC);

-- 기본 템플릿 3종. 전 tenant 공용이라 TENANT='*' (조회는 tenant 로 거르지 않는다).
INSERT INTO SN_LIFE_METRIC_TEMPLATE (OBJ_ID, CODE, NAME, INPUT_SCHEMA, DEFAULT_FORMULA, OUTPUT_UNIT,
                                     SRV_ID, TENANT, TRACE_ID, EVNT_NM, PREV_EVNT_NM, USE_STAT_CD, CREATED_AT, MODIFIED_AT)
VALUES ('lmt-remaining-life', 'remaining_life', '남은 수명(일)',
        '[{"key":"birth_date","label":"생년월일","type":"DATE","mode":"BASELINE"},{"key":"life_expectancy","label":"기대수명","type":"NUMBER","unit":"세","mode":"BASELINE"}]',
        'life_expectancy * 365.2425 - daysBetween(birth_date, now())', '일',
        'd94-query', '*', 'V610', 'SeedLifeMetricTemplate', 'None', 'Usable', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('lmt-hourly-wage', 'hourly_wage', '내 시급',
        '[{"key":"annual_salary","label":"연봉","type":"NUMBER","unit":"원","mode":"BASELINE"},{"key":"weekly_hours","label":"주당 근무시간","type":"NUMBER","unit":"시간","mode":"BASELINE"}]',
        'annual_salary / (weekly_hours * 52)', '원/시간',
        'd94-query', '*', 'V610', 'SeedLifeMetricTemplate', 'None', 'Usable', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('lmt-car-depreciation', 'car_depreciation', '자동차 km당 비용',
        '[{"key":"purchase_price","label":"구매가","type":"NUMBER","unit":"원","mode":"BASELINE"},{"key":"residual_value","label":"예상 잔존가","type":"NUMBER","unit":"원","mode":"BASELINE"},{"key":"maintenance","label":"유지비","type":"NUMBER","unit":"원","mode":"LOG"},{"key":"distance","label":"주행거리","type":"NUMBER","unit":"km","mode":"LOG"}]',
        '(purchase_price - residual_value + sum(maintenance)) / sum(distance)', '원/km',
        'd94-query', '*', 'V610', 'SeedLifeMetricTemplate', 'None', 'Usable', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (OBJ_ID) DO NOTHING;
