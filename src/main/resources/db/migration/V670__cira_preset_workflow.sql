-- V670: 작업 공간 프리셋이 기본 워크플로를 가진다 (설계 2.2 "프리셋에 기본 상태·전이 + 보드 유형").
-- STATUSES = [{"name":"To Do","category":"TODO"}, ...] 순서대로, 전이는 이웃끼리 앞뒤 + 처음 → 마지막.
-- null = 기본 4단계(To Do → In Progress → In Review → Done). 공간을 만들 때 1회 복사되고 이후 연결 없음.
ALTER TABLE SN_CIRA_PROJECT_TYPE ADD COLUMN IF NOT EXISTS STATUSES JSONB;
