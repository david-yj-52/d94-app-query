-- 이메일은 trim + 소문자로만 저장·조회한다 (AuthService.normalizeEmail).
-- 대소문자만 다른 중복 계정이 이미 있으면 UK(EMAIL) 위반으로 실패한다 — 수동 정리 후 재기동.
UPDATE GS_USER SET EMAIL = LOWER(TRIM(EMAIL)) WHERE EMAIL <> LOWER(TRIM(EMAIL));
