package tsh.d94.app.server.apService.cira;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Catalog;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Field;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.FieldKeys;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.ProjectType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveCustomField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.TypeField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Usage;
import tsh.d94.app.server.data.orm.cira.ciraField.CiraFieldAccess;
import tsh.d94.app.server.data.orm.cira.ciraField.CiraFieldAccess.CfValue;
import tsh.d94.app.server.data.orm.cira.ciraField.CiraFieldAccess.CustomField;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;

/**
 * 프로젝트 타입별 필드 세트 · 사용자 정의 필드 · 프로젝트 유효 필드 · 사람별 목록 컬럼 · Task 사용자 정의 값.
 * 설계: d94-note docs/superpowers/specs/2026-11-21-cira-task-columns-design.md (5.1~5.4)
 */
@Service
@RequiredArgsConstructor
public class CiraFieldService {

	/** 기본 필드 카탈로그 (설계 5.1). d94-web src/lib/mock/fields.ts SYSTEM 과 같게 둔다 (웹 화면은 API 로 받음) */
	static final List<Field> SYSTEM = List.of(
		sys("sys:type", "유형", "SELECT"), sys("sys:key", "키", "TEXT"), sys("sys:summary", "요약", "TEXT"),
		sys("sys:status", "상태", "SELECT"), sys("sys:priority", "우선순위", "SELECT"), sys("sys:assignee", "담당자", "USER"),
		sys("sys:reporter", "보고자", "USER"), sys("sys:storyPoints", "스토리 포인트", "NUMBER"), sys("sys:sprint", "스프린트", "SELECT"),
		sys("sys:dueDate", "마감일", "DATE"), sys("sys:startedAt", "시작일", "DATETIME"), sys("sys:resolvedAt", "해결일", "DATETIME"),
		sys("sys:createdAt", "생성일", "DATETIME"), sys("sys:modifiedAt", "수정일", "DATETIME"));
	static final List<String> FIXED = List.of("sys:type", "sys:key", "sys:summary", "sys:status");
	/** 목록에서 숨길 수 없는 키 (행 식별) */
	static final List<String> ALWAYS_SHOWN = List.of("sys:key", "sys:summary");
	static final Set<String> FIELD_TYPES = Set.of("TEXT", "NUMBER", "DATE", "DATETIME", "SELECT", "MULTI_SELECT", "USER", "URL",
		"CHECKBOX");
	private static final Map<String, Field> SYSTEM_BY_KEY = SYSTEM.stream()
		.collect(Collectors.toMap(Field::fieldKey, Function.identity()));
	private static final int TYPE_NM_MAX = 30;
	private static final int FIELD_NM_MAX = 100;
	private static final ObjectMapper JSON = new ObjectMapper();

	private final CiraFieldAccess access;
	private final SnCiraProjectAccess projectAccess;
	private final SnCiraProjectMemberAccess memberAccess;
	private final GsUserAccess userAccess;
	private final jakarta.persistence.EntityManager entityManager;

	private static Field sys(String key, String name, String fieldType) {
		return new Field(key, name, "sys", fieldType, null, false, true, null, true, null);
	}

	// ── 카탈로그 · 프로젝트 타입 (기준정보, 쓰기는 컨트롤러에서 ADMIN) ─────────

	public Catalog catalog() {
		return new Catalog(SYSTEM, access.globalFields(tenant()).stream()
			.map(cf -> toField(cf, null, true)).toList());
	}

	public List<ProjectType> types() {
		return access.types(tenant()).stream()
			.map(t -> new ProjectType(t.id(), t.typeNm(), t.descr(), t.sortOrd(), access.projectsUsingType(t.id()),
				access.typeSet(t.id()).stream().map(f -> new TypeField(f.fieldKey(), f.defaultVisible())).toList()))
			.toList();
	}

	@Transactional
	public ProjectType createType(SaveType req) {
		String name = typeName(req, null);
		String id = access.insertType(tenant(), name, req.descr());
		access.replaceTypeFields(id, List.of(
			new CiraFieldAccess.TypeField("sys:priority", true), new CiraFieldAccess.TypeField("sys:assignee", true),
			new CiraFieldAccess.TypeField("sys:modifiedAt", true)));
		return type(id);
	}

	@Transactional
	public ProjectType updateType(String id, SaveType req) {
		requireType(id);
		access.updateType(id, typeName(req, id), req.descr());
		return type(id);
	}

	@Transactional
	public void deleteType(String id) {
		requireType(id);
		int used = access.projectsUsingType(id);
		if (used > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이 타입을 쓰는 프로젝트가 " + used + "개 있어 삭제할 수 없습니다");
		}
		access.deleteType(id);
	}

	/** 순서 = 배열 순서. 고정 필드는 항상 포함되므로 저장하지 않는다 */
	@Transactional
	public ProjectType replaceTypeFields(String id, List<TypeField> fields) {
		requireType(id);
		Set<String> globalCf = access.globalFields(tenant()).stream()
			.filter(cf -> cf.projectId() == null).map(cf -> "cf:" + cf.id()).collect(Collectors.toSet());
		Set<String> seen = new LinkedHashSet<>();
		List<CiraFieldAccess.TypeField> rows = new ArrayList<>();
		for (TypeField f : fields) {
			String key = f.fieldKey();
			if (FIXED.contains(key)) {
				continue;
			}
			if (!SYSTEM_BY_KEY.containsKey(key) && !globalCf.contains(key)) {
				throw new IllegalArgumentException("타입 세트에 넣을 수 없는 필드입니다: " + key);
			}
			if (!seen.add(key)) {
				throw new IllegalArgumentException("중복된 필드입니다: " + key);
			}
			rows.add(new CiraFieldAccess.TypeField(key, f.defaultVisible()));
		}
		access.replaceTypeFields(id, rows);
		return type(id);
	}

	/** 프로젝트 생성·수정 시 타입 결정: ID 우선, 없으면 이름(옛 클라이언트), 둘 다 없으면 첫 타입 */
	public CiraFieldAccess.ProjectType resolveProjectType(String typeId, String typeNm) {
		String tenant = tenant();
		CiraFieldAccess.ProjectType t;
		if (typeId != null) {
			t = access.type(tenant, typeId);
		} else if (typeNm != null) {
			t = access.typeByName(tenant, typeNm);
		} else {
			t = access.types(tenant).stream().findFirst().orElse(null);
		}
		if (t == null) {
			throw new IllegalArgumentException("프로젝트 타입이 없습니다. 기준정보에서 타입을 먼저 확인하세요: "
				+ (typeId != null ? typeId : typeNm));
		}
		return t;
	}

	// ── 사용자 정의 필드 ─────────────────────────────

	@Transactional
	public Field createGlobalField(SaveCustomField req) {
		return createField(null, req);
	}

	@Transactional
	public Field updateGlobalField(String id, SaveCustomField req) {
		return updateField(requireField(id, null), req);
	}

	@Transactional
	public void deleteGlobalField(String id) {
		access.deleteCustomField(requireField(id, null).id());
	}

	public Usage usage(String id) {
		int[] u = access.customFieldUsage(requireField(id, null).id());
		return new Usage(u[0], u[1]);
	}

	/** 프로젝트 전용 필드. 만들면 바로 그 프로젝트의 추가 필드가 된다 */
	@Transactional
	public Field createProjectField(String projectId, SaveCustomField req) {
		requireAdmin(projectId);
		Field f = createField(projectId, req);
		List<String> keys = new ArrayList<>(access.projectFields(projectId));
		keys.add(f.fieldKey());
		access.replaceProjectFields(projectId, keys);
		return f;
	}

	@Transactional
	public Field updateProjectField(String projectId, String id, SaveCustomField req) {
		requireAdmin(projectId);
		return updateField(requireField(id, projectId), req);
	}

	@Transactional
	public void deleteProjectField(String projectId, String id) {
		requireAdmin(projectId);
		access.deleteCustomField(requireField(id, projectId).id());
	}

	private Field createField(String projectId, SaveCustomField req) {
		String name = fieldName(req.fieldNm());
		if (req.fieldType() == null || !FIELD_TYPES.contains(req.fieldType())) {
			throw new IllegalArgumentException("지원하지 않는 필드 타입입니다: " + req.fieldType());
		}
		ensureUniqueName(projectId, name, null);
		String id = access.insertCustomField(tenant(), projectId, null, name, req.fieldType(), Boolean.TRUE.equals(req.required()),
			optionsJson(req.fieldType(), req.options()), null, null);
		return toField(access.customField(tenant(), id), null, true);
	}

	private Field updateField(CustomField cf, SaveCustomField req) {
		if (req.fieldType() != null && !req.fieldType().equals(cf.fieldType())) {
			throw new IllegalArgumentException("필드 타입은 만든 뒤 바꿀 수 없습니다");
		}
		String name = fieldName(req.fieldNm());
		ensureUniqueName(cf.projectId(), name, cf.id());
		access.updateCustomField(cf.id(), name, Boolean.TRUE.equals(req.required()), optionsJson(cf.fieldType(), req.options()));
		return toField(access.customField(tenant(), cf.id()), null, true);
	}

	// ── 프로젝트 유효 필드 (설계 5.2) ────────────────────

	/** 고정 → 타입 세트 → 프로젝트 추가 필드. 중복은 앞쪽이 이긴다. 읽기는 프로젝트 멤버 */
	public List<Field> projectFields(String projectId) {
		requireMember(projectId);
		return effective(projectId);
	}

	/** 프로젝트 추가 필드 전체 교체. 고정·타입 세트에 이미 있는 키는 그대로 받아 두고 계산에서 무시한다 */
	@Transactional
	public List<Field> replaceProjectFields(String projectId, List<String> keys) {
		requireAdmin(projectId);
		Map<String, CustomField> cfs = visibleCustomFields(projectId);
		List<String> clean = new ArrayList<>(new LinkedHashSet<>(keys));
		clean.removeIf(FIXED::contains);
		for (String key : clean) {
			if (resolve(key, cfs) == null) {
				throw new IllegalArgumentException("이 프로젝트에서 쓸 수 없는 필드입니다: " + key);
			}
		}
		access.replaceProjectFields(projectId, clean);
		return effective(projectId);
	}

	List<Field> effective(String projectId) {
		SnCiraProjectModel project = projectAccess.findById(projectId);
		Map<String, CustomField> cfs = visibleCustomFields(projectId);
		Map<String, Field> out = new LinkedHashMap<>();
		FIXED.forEach(k -> out.put(k, withSource(SYSTEM_BY_KEY.get(k), "fixed", true)));
		if (project.getProjectTypeId() != null) {
			for (CiraFieldAccess.TypeField tf : access.typeSet(project.getProjectTypeId())) {
				Field f = resolve(tf.fieldKey(), cfs);
				if (f != null) {
					out.putIfAbsent(f.fieldKey(), withSource(f, "type", tf.defaultVisible()));
				}
			}
		}
		for (String key : access.projectFields(projectId)) {
			Field f = resolve(key, cfs);
			if (f != null) {
				out.putIfAbsent(key, withSource(f, "project", true));
			}
		}
		return new ArrayList<>(out.values());
	}

	// ── 사람별 목록 컬럼 ────────────────────────────

	/** 없으면 기본 표시 필드. 있으면 죽은 키를 거르고, 처음 보는 기본 표시 필드는 뒤에 붙인다 (설계 5.3) */
	public FieldKeys columnPref(String projectId) {
		requireMember(projectId);
		List<Field> eff = effective(projectId);
		Set<String> effKeys = eff.stream().map(Field::fieldKey).collect(Collectors.toCollection(LinkedHashSet::new));
		CiraFieldAccess.ColumnPref pref = access.columnPref(currentUserId(), projectId);
		List<String> keys = new ArrayList<>();
		if (pref == null) {
			eff.stream().filter(Field::defaultVisible).forEach(f -> keys.add(f.fieldKey()));
		} else {
			readKeys(pref.fieldKeysJson()).stream().filter(effKeys::contains).distinct().forEach(keys::add);
			List<String> known = readKeys(pref.knownKeysJson());
			eff.stream()
				.filter(f -> f.defaultVisible() && !known.contains(f.fieldKey()) && !keys.contains(f.fieldKey()))
				.forEach(f -> keys.add(f.fieldKey()));
		}
		return new FieldKeys(withAlwaysShown(keys));
	}

	@Transactional
	public FieldKeys saveColumnPref(String projectId, List<String> fieldKeys) {
		requireMember(projectId);
		List<String> effKeys = effective(projectId).stream().map(Field::fieldKey).toList();
		List<String> keys = new ArrayList<>(new LinkedHashSet<>(fieldKeys == null ? List.of() : fieldKeys));
		for (String k : keys) {
			if (!effKeys.contains(k)) {
				throw new IllegalArgumentException("이 프로젝트의 필드가 아닙니다: " + k);
			}
		}
		keys = withAlwaysShown(keys);
		access.saveColumnPref(currentUserId(), projectId, writeJson(keys), writeJson(effKeys));
		return new FieldKeys(keys);
	}

	@Transactional
	public FieldKeys resetColumnPref(String projectId) {
		requireMember(projectId);
		access.deleteColumnPref(currentUserId(), projectId);
		return columnPref(projectId);
	}

	// ── Task 사용자 정의 값 ─────────────────────────

	/** issueId → {"cf:<id>": 값}. 값이 없는 Task 는 빈 맵 */
	public Map<String, Map<String, Object>> values(Collection<String> issueIds) {
		Map<String, Map<String, Object>> out = new HashMap<>();
		Map<String, CustomField> types = new HashMap<>();
		access.values(issueIds).forEach((issueId, rows) -> {
			Map<String, Object> m = new LinkedHashMap<>();
			for (CfValue v : rows) {
				CustomField cf = types.computeIfAbsent(v.customFieldId(), id -> access.customField(tenant(), id));
				if (cf != null) {
					m.put("cf:" + cf.id(), read(cf.fieldType(), v));
				}
			}
			out.put(issueId, m);
		});
		return out;
	}

	/** 유효 필드가 아닌 키·타입 불일치는 400. creating 이면 필수 필드도 검사. null·"" 은 값 삭제 */
	@Transactional
	public void applyValues(String projectId, String issueId, Map<String, Object> values, boolean creating) {
		Map<String, Field> cfFields = effective(projectId).stream().filter(f -> "cf".equals(f.kind()))
			.collect(Collectors.toMap(Field::fieldKey, Function.identity()));
		Map<String, Object> given = values == null ? Map.of() : values;
		if (creating) {
			for (Field f : cfFields.values()) {
				if (f.required() && isEmpty(given.get(f.fieldKey()))) {
					throw new IllegalArgumentException("필수 입력 필드입니다: " + f.name());
				}
			}
		}
		// 전부 검증한 뒤 쓴다. 값 null = 삭제
		Map<String, CfValue> writes = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : given.entrySet()) {
			Field f = cfFields.get(e.getKey());
			if (f == null) {
				throw new IllegalArgumentException("이 프로젝트의 사용자 정의 필드가 아닙니다: " + e.getKey());
			}
			String cfId = e.getKey().substring(3);
			if (isEmpty(e.getValue())) {
				if (f.required()) {
					throw new IllegalArgumentException("필수 입력 필드는 비울 수 없습니다: " + f.name());
				}
				writes.put(cfId, null);
			} else {
				writes.put(cfId, toValue(projectId, issueId, cfId, f, e.getValue()));
			}
		}
		if (writes.isEmpty()) {
			return;
		}
		entityManager.flush(); // 방금 save 한 Task 행이 있어야 값 FK 가 맞는다
		writes.forEach((cfId, v) -> {
			if (v == null) {
				access.deleteValue(issueId, cfId);
			} else {
				access.upsertValue(tenant(), v);
			}
		});
	}

	private CfValue toValue(String projectId, String issueId, String cfId, Field f, Object v) {
		try {
			return switch (f.fieldType()) {
				case "TEXT" -> new CfValue(issueId, cfId, str(v), null, null, null);
				case "URL" -> {
					String s = str(v);
					if (!s.matches("(?i)^https?://\\S+$")) {
						throw new IllegalArgumentException("URL 형식이 아닙니다");
					}
					yield new CfValue(issueId, cfId, s, null, null, null);
				}
				case "NUMBER" -> new CfValue(issueId, cfId, null, new BigDecimal(v.toString()), null, null);
				case "DATE" -> new CfValue(issueId, cfId, null, null, LocalDate.parse(str(v)), null);
				case "DATETIME" -> {
					String s = str(v);
					try {
						OffsetDateTime.parse(s);
					} catch (RuntimeException ex) {
						LocalDateTime.parse(s);
					}
					yield json(issueId, cfId, s);
				}
				case "SELECT" -> {
					String s = str(v);
					if (!f.options().contains(s)) {
						throw new IllegalArgumentException("선택지에 없는 값입니다: " + s);
					}
					yield json(issueId, cfId, s);
				}
				case "MULTI_SELECT" -> {
					if (!(v instanceof List<?> list) || !list.stream().allMatch(x -> x instanceof String s && f.options().contains(s))) {
						throw new IllegalArgumentException("선택지 목록이어야 합니다");
					}
					yield json(issueId, cfId, list.stream().distinct().toList());
				}
				case "USER" -> {
					String userId = str(v);
					memberAccess.findByProjectIdAndUserId(projectId, userId)
						.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
						.orElseThrow(() -> new IllegalArgumentException("프로젝트 멤버가 아닙니다: " + userId));
					yield json(issueId, cfId, userId);
				}
				case "CHECKBOX" -> {
					if (!(v instanceof Boolean b)) {
						throw new IllegalArgumentException("true/false 여야 합니다");
					}
					yield json(issueId, cfId, b);
				}
				default -> throw new IllegalArgumentException("지원하지 않는 필드 타입입니다: " + f.fieldType());
			};
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(f.name() + ": " + e.getMessage(), e);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(f.name() + ": 값 형식이 맞지 않습니다", e);
		}
	}

	private Object read(String fieldType, CfValue v) {
		return switch (fieldType) {
			case "TEXT", "URL" -> v.valText();
			case "NUMBER" -> v.valNumber();
			case "DATE" -> v.valDt() == null ? null : v.valDt().toString();
			default -> {
				try {
					yield v.valJson() == null ? null : JSON.readValue(v.valJson(), Object.class);
				} catch (JsonProcessingException e) {
					yield null;
				}
			}
		};
	}

	// ── 내부 ────────────────────────────────────

	private ProjectType type(String id) {
		return types().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
	}

	private void requireType(String id) {
		if (access.type(tenant(), id) == null) {
			throw new jakarta.persistence.EntityNotFoundException("프로젝트 타입을 찾을 수 없습니다: " + id);
		}
	}

	private String typeName(SaveType req, String selfId) {
		String name = req == null || req.typeNm() == null ? "" : req.typeNm().trim();
		if (name.isEmpty() || name.length() > TYPE_NM_MAX) {
			throw new IllegalArgumentException("타입 이름은 1~" + TYPE_NM_MAX + "자입니다");
		}
		CiraFieldAccess.ProjectType same = access.typeByName(tenant(), name);
		if (same != null && !same.id().equals(selfId)) {
			throw new IllegalArgumentException("이미 있는 타입 이름입니다: " + name);
		}
		return name;
	}

	/** projectId null = 전역 필드만, 아니면 그 프로젝트 전용 필드만 */
	private CustomField requireField(String id, String projectId) {
		CustomField cf = access.customField(tenant(), id);
		if (cf == null || !java.util.Objects.equals(cf.projectId(), projectId)) {
			throw new jakarta.persistence.EntityNotFoundException("사용자 정의 필드를 찾을 수 없습니다: " + id);
		}
		return cf;
	}

	private String fieldName(String raw) {
		String name = raw == null ? "" : raw.trim();
		if (name.isEmpty() || name.length() > FIELD_NM_MAX) {
			throw new IllegalArgumentException("필드 이름은 1~" + FIELD_NM_MAX + "자입니다");
		}
		return name;
	}

	/** 같은 범위(전역 / 그 프로젝트)에서 이름 중복 금지. 전역 null 은 DB UNIQUE 가 못 막는다 */
	private void ensureUniqueName(String projectId, String name, String selfId) {
		boolean dup = (projectId == null ? access.globalFields(tenant()) : access.projectCustomFields(tenant(), projectId)).stream()
			.anyMatch(cf -> java.util.Objects.equals(cf.projectId(), projectId) && cf.fieldNm().equals(name)
				&& !cf.id().equals(selfId));
		if (dup) {
			throw new IllegalArgumentException("이미 있는 필드 이름입니다: " + name);
		}
	}

	private String optionsJson(String fieldType, List<String> options) {
		if (!"SELECT".equals(fieldType) && !"MULTI_SELECT".equals(fieldType)) {
			return null;
		}
		List<String> clean = options == null ? List.of()
			: options.stream().filter(o -> o != null && !o.isBlank()).map(String::trim).distinct().toList();
		if (clean.isEmpty()) {
			throw new IllegalArgumentException("선택형 필드는 선택지가 하나 이상 필요합니다");
		}
		return writeJson(clean);
	}

	private Map<String, CustomField> visibleCustomFields(String projectId) {
		return java.util.stream.Stream.concat(access.globalFields(tenant()).stream(),
				access.projectCustomFields(tenant(), projectId).stream())
			.collect(Collectors.toMap(CustomField::id, Function.identity()));
	}

	private Field resolve(String key, Map<String, CustomField> cfs) {
		if (key.startsWith("sys:")) {
			return SYSTEM_BY_KEY.get(key);
		}
		if (key.startsWith("cf:")) {
			CustomField cf = cfs.get(key.substring(3));
			return cf == null ? null : toField(cf, null, true);
		}
		return null;
	}

	private Field toField(CustomField cf, String source, boolean defaultVisible) {
		List<String> options = cf.optionsJson() == null ? null : readKeys(cf.optionsJson());
		return new Field("cf:" + cf.id(), cf.fieldNm(), "cf", cf.fieldType(), options, cf.required(), false, source,
			defaultVisible, cf.projectId());
	}

	private static Field withSource(Field f, String source, boolean defaultVisible) {
		return new Field(f.fieldKey(), f.name(), f.kind(), f.fieldType(), f.options(), f.required(), f.sortable(), source,
			defaultVisible, f.projectId());
	}

	private static List<String> withAlwaysShown(List<String> keys) {
		List<String> out = new ArrayList<>(keys);
		for (int i = ALWAYS_SHOWN.size() - 1; i >= 0; i--) {
			if (!out.contains(ALWAYS_SHOWN.get(i))) {
				out.add(0, ALWAYS_SHOWN.get(i));
			}
		}
		return out;
	}

	private static boolean isEmpty(Object v) {
		return v == null || (v instanceof String s && s.isBlank()) || (v instanceof List<?> l && l.isEmpty());
	}

	private static String str(Object v) {
		if (!(v instanceof String s)) {
			throw new IllegalArgumentException("문자열이어야 합니다");
		}
		return s.trim();
	}

	private static CfValue json(String issueId, String cfId, Object v) {
		return new CfValue(issueId, cfId, null, null, null, writeJson(v));
	}

	private static List<String> readKeys(String json) {
		try {
			return JSON.readValue(json, new TypeReference<List<String>>() {});
		} catch (JsonProcessingException e) {
			return List.of();
		}
	}

	private static String writeJson(Object v) {
		try {
			return JSON.writeValueAsString(v);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private String currentUserId() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return userAccess.findByEmail(email)
			.orElseThrow(() -> new AccessDeniedException("User not found: " + email)).getObjId();
	}

	private SnCiraProjectMemberModel requireMember(String projectId) {
		return memberAccess.findByProjectIdAndUserId(projectId, currentUserId())
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new AccessDeniedException("프로젝트 멤버가 아닙니다"));
	}

	private void requireAdmin(String projectId) {
		if (!"ADMIN".equals(requireMember(projectId).getRole())) {
			throw new AccessDeniedException("프로젝트 관리자만 변경할 수 있습니다");
		}
	}

	private static String tenant() {
		return TenantContext.get();
	}
}
