package tsh.d94.app.server.apService.apiToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.*;
import tsh.d94.app.server.apService.mdm.SettingService;
import tsh.d94.app.server.data.orm.apiToken.snApiToken.SnApiTokenAccess;
import tsh.d94.app.server.data.orm.apiToken.snApiToken.SnApiTokenModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;

/** API 토큰 발급·조회·폐기와 요청 인증. 설계: d94-note feature/user/3_api_token/api-token-plan.md */
@Service
@RequiredArgsConstructor
public class ApiTokenService {

	static final String READ = "READ";
	static final String WRITE = "WRITE";
	static final int MAX_USABLE_PER_OWNER = 20;
	static final int DEFAULT_DAYS = 90;
	static final Set<Integer> ALLOWED_DAYS = Set.of(30, 90, 365);
	static final int NAME_MAX = 100;
	static final int DISPLAY_PREFIX_LEN = 12;
	/** SN_SETTING 키 = api-token.{service}.enabled (없으면 허용) */
	static final String SETTING_PREFIX = "api-token.";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SnApiTokenAccess tokens;
	private final GsUserAccess users;
	private final UserDetailsService userDetailsService;
	private final SettingService settings;

	@Transactional
	public IssueResponse issue(String tenant, String owner, IssueRequest req) {
		ApiTokenScope scope = ApiTokenScope.of(req.service());
		String name = req.name() == null ? "" : req.name().trim();
		if (name.isEmpty() || name.length() > NAME_MAX) {
			throw new IllegalArgumentException("토큰 이름은 1~" + NAME_MAX + "자여야 합니다.");
		}
		String access = req.access() == null ? READ : req.access().trim().toUpperCase();
		if (!READ.equals(access) && !WRITE.equals(access)) {
			throw new IllegalArgumentException("access 는 READ 또는 WRITE 여야 합니다.");
		}
		int days = req.expiresInDays() == null ? DEFAULT_DAYS : req.expiresInDays();
		if (!ALLOWED_DAYS.contains(days)) {
			throw new IllegalArgumentException("만료 기간은 30, 90, 365일 중 하나여야 합니다.");
		}
		if (!serviceEnabled(tenant, scope)) {
			throw new AccessDeniedException("관리자가 " + scope.code() + " API 토큰 사용을 막았습니다.");
		}
		LocalDateTime now = LocalDateTime.now();
		if (tokens.countUsableOfOwner(tenant, owner, now) >= MAX_USABLE_PER_OWNER) {
			throw new IllegalArgumentException("사용 중인 토큰은 최대 " + MAX_USABLE_PER_OWNER + "개입니다. 안 쓰는 토큰을 폐기하세요.");
		}

		String raw = newRawToken(scope);
		SnApiTokenModel saved = tokens.save(SnApiTokenModel.builder()
			.ownerId(owner)
			.serviceCd(scope.code())
			.tokenNm(name)
			.tokenHash(sha256(raw))
			.tokenPrefix(raw.substring(0, DISPLAY_PREFIX_LEN))
			.accessCd(access)
			.expiresAt(now.plusDays(days))
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(tenant)
			.traceId("IssueApiToken")
			.useStatCd(UseStatCd.Usable)
			.evtNm("IssueApiToken")
			.prevEvntNm("None")
			.build());
		return new IssueResponse(raw, view(saved));
	}

	/** 폐기하지 않은 토큰(만료 포함 — 화면에서 만료 표시) */
	public List<TokenView> list(String tenant, String owner) {
		return tokens.findActiveOfOwner(tenant, owner).stream().map(ApiTokenService::view).toList();
	}

	/** 남의 토큰·없는 토큰은 404 */
	@Transactional
	public void revoke(String tenant, String owner, String id) {
		SnApiTokenModel token = tokens.findOwned(id, tenant, owner)
			.filter(t -> t.getRevokedAt() == null)
			.orElseThrow(() -> new EntityNotFoundException("토큰을 찾을 수 없습니다."));
		token.setRevokedAt(LocalDateTime.now());
		token.setPrevEvntNm(token.getEvtNm());
		token.setEvtNm("RevokeApiToken");
		tokens.save(token);
	}

	public List<ServiceStatus> services(String tenant) {
		Map<String, String> group = settings.getGroup(tenant, SETTING_PREFIX);
		return Arrays.stream(ApiTokenScope.values())
			.map(s -> new ServiceStatus(s.code(), enabled(group, s)))
			.toList();
	}

	public List<ServiceStatus> setServiceEnabled(String tenant, String service, Boolean enabled) {
		if (enabled == null) {
			throw new IllegalArgumentException("enabled 값이 필요합니다.");
		}
		ApiTokenScope scope = ApiTokenScope.of(service);
		settings.putGroup(tenant, SETTING_PREFIX, Map.of(scope.code() + ".enabled", enabled.toString()));
		return services(tenant);
	}

	/**
	 * 요청 인증. 토큰 없음·불일치·만료·폐기·소유자 정지 → 401, 서비스 꺼짐·범위 밖·READ 로 쓰기 → 403.
	 * path 는 context-path 를 뺀 정규화 경로.
	 */
	@Transactional
	public AuthResult authenticate(String raw, String method, String path) {
		SnApiTokenModel token = raw.startsWith(ApiTokenScope.TOKEN_PREFIX)
			? tokens.findByTokenHash(sha256(raw)).orElse(null) : null;
		LocalDateTime now = LocalDateTime.now();
		if (token == null || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
			return new Denied(401, "API_TOKEN_INVALID", "유효하지 않은 API 토큰입니다.");
		}
		boolean ownerUsable = users.findByEmail(token.getOwnerId())
			.map(u -> u.getUseStatCd() == UseStatCd.Usable)
			.orElse(false);
		if (!ownerUsable) {
			return new Denied(401, "API_TOKEN_INVALID", "유효하지 않은 API 토큰입니다.");
		}
		ApiTokenScope scope;
		try {
			scope = ApiTokenScope.of(token.getServiceCd());
		} catch (IllegalArgumentException e) {
			return new Denied(401, "API_TOKEN_INVALID", "유효하지 않은 API 토큰입니다.");
		}
		if (!serviceEnabled(token.getTenant(), scope)) {
			return new Denied(403, "API_TOKEN_SERVICE_DISABLED", "관리자가 이 서비스의 API 토큰 사용을 막았습니다.");
		}
		if (!scope.allows(path)) {
			return new Denied(403, "API_TOKEN_SCOPE", "이 토큰으로 호출할 수 없는 API 입니다.");
		}
		if (READ.equals(token.getAccessCd()) && !"GET".equals(method) && !"HEAD".equals(method)) {
			return new Denied(403, "API_TOKEN_READ_ONLY", "읽기 전용 토큰입니다.");
		}
		tokens.touchLastUsed(token.getObjId(), now);
		return new Authenticated(userDetailsService.loadUserByUsername(token.getOwnerId()), token.getTenant(),
			token.getTokenPrefix());
	}

	private boolean serviceEnabled(String tenant, ApiTokenScope scope) {
		return enabled(settings.getGroup(tenant, SETTING_PREFIX), scope);
	}

	private static boolean enabled(Map<String, String> group, ApiTokenScope scope) {
		return !"false".equalsIgnoreCase(group.get(scope.code() + ".enabled"));
	}

	static String newRawToken(ApiTokenScope scope) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return ApiTokenScope.TOKEN_PREFIX + scope.code() + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String sha256(String raw) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static TokenView view(SnApiTokenModel t) {
		return new TokenView(t.getObjId(), t.getTokenNm(), t.getServiceCd(), t.getAccessCd(), t.getTokenPrefix(),
			t.getExpiresAt(), t.getLastUsedAt(), t.getCreatedAt());
	}
}
