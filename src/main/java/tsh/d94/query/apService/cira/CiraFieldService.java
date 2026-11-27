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

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Catalog;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Field;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.FieldKeys;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.LibraryItem;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.ProjectType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveCustomField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.TypeField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Usage;
import tsh.d94.app.server.data.orm.cira.ciraField.CiraFieldAccess;
import tsh.d94.app.server.data.orm.cira.ciraField.CiraFieldAccess.CfValue;
import tsh.d94.app.server.data.orm.cira.ciraField.CiraFieldAccess.CustomField;
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
	private final SpaceGuard spaces;
	private final SnCiraProjectMemberAccess memberAccess;
	private final GsUserAccess userAccess;
	private final jakarta.persistence.EntityManager entityManager;

	private static Field sys(String key, String name, String fieldType) {
		return new Field(key, name, "sys", fieldType, null, false, true, null, true, null, null, null, null, null, false);
	}

	// ── 카탈로그 · 프로젝트 타입 (기준정보, 쓰기는 컨트롤러에서 ADMIN) ─────────

	public Catalog catalog() {
		List<CustomField> globals = access.globalFields(tenant());
		Map<String, List<Field>> typeFields = new LinkedHashMap<>();
		access.types(tenant()).forEach(t -> typeFields.put(t.id(),
			access.typeFields(tenant(), t.id()).stream().map(cf -> toField(cf, null, true, null)).toList()));
		Map<String, Integer> copies = new LinkedHashMap<>();
		globals.forEach(g -> copies.put(g.id(), access.copiesOf(g.id())));
		return new Catalog(SYSTEM, globals.stream().map(cf -> toField(cf, null, true, null)).toList(), typeFields, copies);
	}

	public List<ProjectType> types() {
		return access.types(tenant()).stream()
			.map(t -> new ProjectType(t.id(), t.typeNm(), t.descr(), t.sortOrd(), access.spacesUsingPreset(t.id()),
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
		// 프리셋은 만들 때 1회 복사라 이걸로 만든 공간이 있어도 지울 수 있다 (공간 설정·복사본 필드는 그대로)
		// 타입 필드 값의 FK 때문에 CASCADE 가 막히지 않도록 타입 필드를 먼저 지운다
		access.typeFields(tenant(), id).forEach(cf -> access.deleteCustomField(cf.id()));
		access.deleteType(id);
	}

	/** 순서 = 배열 순서. 고정 필드는 항상 포함되므로 저장하지 않는다 */
	@Transactional
	public ProjectType replaceTypeFields(String id, List<TypeField> fields) {
		requireType(id);
		Set<String> ownCf = access.typeFields(tenant(), id).stream().map(cf -> "cf:" + cf.id()).collect(Collectors.toSet());
		Set<String> globalCf = access.globalFields(tenant()).stream().map(cf -> "cf:" + cf.id()).collect(Collectors.toSet());
		Set<String> seen = new LinkedHashSet<>();
		List<CiraFieldAccess.TypeField> rows = new ArrayList<>();
		for (TypeField f : fields) {
			String key = f.fieldKey();
			if (FIXED.contains(key)) {
				continue;
			}
			if (globalCf.contains(key)) {
				throw new IllegalArgumentException("전역 필드는 먼저 타입으로 가져와야 세트에 넣을 수 있습니다: " + key);
			}
			if (!SYSTEM_BY_KEY.containsKey(key) && !ownCf.contains(key)) {
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

	/** 프로젝트 생성 시 기본 공간 프리셋: ID 우선, 없으면 이름(옛 클라이언트), 둘 다 없으면 첫 프리셋 */
	public CiraFieldAccess.ProjectType resolvePreset(String typeId, String typeNm) {
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
			throw new IllegalArgumentException("작업 공간 프리셋이 없습니다. 기준정보에서 프리셋을 먼저 확인하세요: "
				+ (typeId != null ? typeId : typeNm));
		}
		return t;
	}

	// ── 사용자 정의 필드: 전역 · 타입 (기준정보, 쓰기는 컨트롤러에서 ADMIN) ─────

	/** copyToTypeIds 가 있으면 그 타입들로 바로 복사한다 (설계 5.2) */
	@Transactional
	public Field createGlobalField(SaveCustomField req) {
		Field f = createField(null, null, req, currentUserId());
		if (req.copyToTypeIds() != null) {
			req.copyToTypeIds().stream().distinct().forEach(typeId -> importToType(typeId, id(f)));
		}
		return f;
	}

	@Transactional
	public Field updateGlobalField(String id, SaveCustomField req) {
		return updateField(requireField(id, null, null), req);
	}

	/** 원본만 지운다. 복사본(다른 id)과 그 값은 남는다 */
	@Transactional
	public void deleteGlobalField(String id) {
		access.deleteCustomField(requireField(id, null, null).id());
	}

	/** 전역·타입 필드의 사용량: 복사본 수, 이 필드 자신을 쓰는 프로젝트·Task 수 */
	public Usage usage(String id) {
		CustomField cf = access.customField(tenant(), id);
		if (cf == null || cf.projectId() != null) {
			throw new jakarta.persistence.EntityNotFoundException("사용자 정의 필드를 찾을 수 없습니다: " + id);
		}
		int[] u = access.customFieldUsage(cf.id());
		return new Usage(access.copiesOf(cf.id()), u[0], u[1]);
	}

	@Transactional
	public Field createTypeField(String typeId, SaveCustomField req) {
		requireType(typeId);
		return createField(null, typeId, req, currentUserId());
	}

	@Transactional
	public Field updateTypeField(String typeId, String id, SaveCustomField req) {
		return updateField(requireField(id, null, typeId), req);
	}

	/** 타입 필드와 그 세트 참조·값을 지운다. 프로젝트가 가져간 복사본은 남는다 */
	@Transactional
	public void deleteTypeField(String typeId, String id) {
		access.deleteCustomField(requireField(id, null, typeId).id());
	}

	/** 전역 필드를 타입 필드로 복사 */
	@Transactional
	public Field importToType(String typeId, String sourceFieldId) {
		requireType(typeId);
		return copyField(requireField(sourceFieldId, null, null), null, typeId);
	}

	// ── 사용자 정의 필드: 프로젝트 (멤버) ─────────────────────

	/** 가져올 수 있는 것: 전역 필드 + 모든 프리셋의 필드 (공간은 어느 프리셋의 필드든 가져올 수 있다). 이미 가져온 건 importedAs */
	public List<LibraryItem> library(String projectId) {
		requireMember(projectId);
		Map<String, String> imported = new HashMap<>();
		access.projectCustomFields(tenant(), projectId).stream().filter(cf -> cf.sourceFieldId() != null)
			.forEach(cf -> imported.putIfAbsent(cf.sourceFieldId(), "cf:" + cf.id()));
		List<CustomField> items = new ArrayList<>(access.globalFields(tenant()));
		items.addAll(access.allTypeFields(tenant()));
		return items.stream().map(cf -> new LibraryItem(toField(cf, null, true, null), imported.get(cf.id()))).toList();
	}

	/** 전역 필드 또는 프리셋 필드를 프로젝트 필드로 복사하고 기본 공간 필드 세트 끝에 붙인다 */
	@Transactional
	public Field importToProject(String projectId, String sourceFieldId) {
		return importToProject(projectId, null, sourceFieldId);
	}

	/** 공간 필드 세트 끝에 붙인다 (spaceId null = 기본 공간) */
	@Transactional
	public Field importToProject(String projectId, String spaceId, String sourceFieldId) {
		requireMember(projectId);
		String sid = spaces.resolve(projectId, spaceId).getObjId();
		CustomField src = access.customField(tenant(), sourceFieldId);
		// 다른 프로젝트의 필드는 가져올 수 없다 (전역·프리셋 필드만)
		if (src == null || src.projectId() != null) {
			throw new jakarta.persistence.EntityNotFoundException("가져올 수 있는 필드가 아닙니다: " + sourceFieldId);
		}
		Field copy = copyField(src, projectId, null);
		appendSpaceField(projectId, sid, copy.fieldKey());
		return spaceField(projectId, sid, copy.fieldKey());
	}

	/** 프로젝트 전용 필드. 프로젝트 멤버 누구나 만들고, 만들면 바로 그 프로젝트의 추가 필드가 된다 */
	@Transactional
	public Field createProjectField(String projectId, SaveCustomField req) {
		return createProjectField(projectId, null, req);
	}

	/** 사용자 정의 필드는 프로젝트 소유, 만들면 그 공간 필드 세트 끝에 붙는다 */
	@Transactional
	public Field createProjectField(String projectId, String spaceId, SaveCustomField req) {
		requireMember(projectId);
		String sid = spaces.resolve(projectId, spaceId).getObjId();
		Field f = createField(projectId, null, req, currentUserId());
		appendSpaceField(projectId, sid, f.fieldKey());
		return spaceField(projectId, sid, f.fieldKey());
	}

	@Transactional
	public Field updateProjectField(String projectId, String id, SaveCustomField req) {
		CustomField cf = requireField(id, projectId, null);
		requireEditor(projectId, cf);
		updateField(cf, req);
		return toField(access.customField(tenant(), id), "project", true, viewer(projectId));
	}

	@Transactional
	public void deleteProjectField(String projectId, String id) {
		CustomField cf = requireField(id, projectId, null);
		requireEditor(projectId, cf);
		access.deleteCustomField(cf.id());
	}

	private void appendSpaceField(String projectId, String spaceId, String key) {
		List<CiraFieldAccess.TypeField> rows = new ArrayList<>(access.spaceFields(spaceId));
		rows.add(new CiraFieldAccess.TypeField(key, true));
		access.replaceSpaceFields(projectId, spaceId, rows);
	}

	/** 이 공간 유효 필드 중 하나 (canEdit 이 현재 사용자 기준으로 채워진 것) */
	private Field spaceField(String projectId, String spaceId, String key) {
		return effective(projectId, spaceId).stream().filter(f -> f.fieldKey().equals(key)).findFirst().orElseThrow();
	}

	/**
	 * 새 공간에 프리셋(구 프로젝트 타입)의 필드 세트를 복사한다. 타입 필드(cf)는 프로젝트 필드로 복사본을 만들어
	 * 공간이 프리셋과 완전히 끊기게 한다(이후 프리셋 수정·삭제와 무관). typeId null = sys 기본 필드 없이 고정 필드만.
	 */
	@Transactional
	public void copyPresetFields(String projectId, String spaceId, String typeId) {
		List<CiraFieldAccess.TypeField> rows = new ArrayList<>();
		if (typeId != null) {
			Map<String, CustomField> typeCfs = access.typeFields(tenant(), typeId).stream()
				.collect(Collectors.toMap(CustomField::id, Function.identity()));
			Map<String, String> copied = new HashMap<>(); // 원본 id → 이 프로젝트의 복사본 키
			access.projectCustomFields(tenant(), projectId).stream().filter(cf -> cf.sourceFieldId() != null)
				.forEach(cf -> copied.putIfAbsent(cf.sourceFieldId(), "cf:" + cf.id()));
			for (CiraFieldAccess.TypeField tf : access.typeSet(typeId)) {
				String key = tf.fieldKey();
				if (key.startsWith("cf:")) {
					CustomField src = typeCfs.get(key.substring(3));
					if (src == null) {
						continue;
					}
					key = copied.computeIfAbsent(src.id(), x -> copyField(src, projectId, null).fieldKey());
				}
				rows.add(new CiraFieldAccess.TypeField(key, tf.defaultVisible()));
			}
		}
		access.replaceSpaceFields(projectId, spaceId, rows);
	}

	private Field createField(String projectId, String typeId, SaveCustomField req, String createdBy) {
		String name = fieldName(req.fieldNm());
		if (req.fieldType() == null || !FIELD_TYPES.contains(req.fieldType())) {
			throw new IllegalArgumentException("지원하지 않는 필드 타입입니다: " + req.fieldType());
		}
		ensureUniqueName(projectId, typeId, name, null);
		String id = access.insertCustomField(tenant(), projectId, typeId, name, req.fieldType(), Boolean.TRUE.equals(req.required()),
			optionsJson(req.fieldType(), req.options()), null, createdBy);
		return toField(access.customField(tenant(), id), null, true, null);
	}

	/** 원본의 이름·필드 타입·선택지·필수 여부를 그대로 새 행으로 (설계 F3) */
	private Field copyField(CustomField src, String projectId, String typeId) {
		ensureUniqueName(projectId, typeId, src.fieldNm(), null);
		String id = access.insertCustomField(tenant(), projectId, typeId, src.fieldNm(), src.fieldType(), src.required(),
			src.optionsJson(), src.id(), currentUserId());
		return toField(access.customField(tenant(), id), null, true, null);
	}

	private Field updateField(CustomField cf, SaveCustomField req) {
		if (req.fieldType() != null && !req.fieldType().equals(cf.fieldType())) {
			throw new IllegalArgumentException("필드 타입은 만든 뒤 바꿀 수 없습니다");
		}
		String name = fieldName(req.fieldNm());
		ensureUniqueName(cf.projectId(), cf.projectTypeId(), name, cf.id());
		access.updateCustomField(cf.id(), name, Boolean.TRUE.equals(req.required()), optionsJson(cf.fieldType(), req.options()));
		return toField(access.customField(tenant(), cf.id()), null, true, null);
	}

	// ── 프로젝트 유효 필드 (설계 5.2) ────────────────────

	/** 고정 → 타입 세트 → 프로젝트 추가 필드. 중복은 앞쪽이 이긴다. 읽기는 프로젝트 멤버 */
	public List<Field> projectFields(String projectId) {
		return projectFields(projectId, null);
	}

	public List<Field> projectFields(String projectId, String spaceId) {
		requireMember(projectId);
		return effective(projectId, spaceId);
	}

	/** 프로젝트 추가 필드 전체 교체. 고정·타입 세트에 이미 있는 키는 그대로 받아 두고 계산에서 무시한다 */
	@Transactional
	public List<Field> replaceProjectFields(String projectId, List<String> keys) {
		return replaceProjectFields(projectId, null, keys);
	}

	/** 공간 필드 세트 전체 교체 (V650: 타입 세트 고정 없음 — 공간 관리자가 자유롭게 켜고 끈다). 기존 키의 기본 표시 여부는 유지 */
	@Transactional
	public List<Field> replaceProjectFields(String projectId, String spaceId, List<String> keys) {
		String sid = spaces.requireManager(spaces.resolve(projectId, spaceId)).getObjId();
		Map<String, CustomField> cfs = visibleCustomFields(projectId);
		List<String> clean = new ArrayList<>(new LinkedHashSet<>(keys));
		clean.removeIf(FIXED::contains);
		for (String key : clean) {
			if (resolve(key, cfs, null) == null) {
				throw new IllegalArgumentException("이 프로젝트에서 쓸 수 없는 필드입니다 (사용자 정의 필드는 라이브러리에서 가져오세요): " + key);
			}
		}
		Map<String, Boolean> visible = new HashMap<>();
		access.spaceFields(sid).forEach(tf -> visible.put(tf.fieldKey(), tf.defaultVisible()));
		access.replaceSpaceFields(projectId, sid,
			clean.stream().map(k -> new CiraFieldAccess.TypeField(k, visible.getOrDefault(k, true))).toList());
		return effective(projectId, sid);
	}

	List<Field> effective(String projectId) {
		return effective(projectId, null);
	}

	/** 고정 → 공간 필드 세트. 중복은 앞쪽이 이긴다 */
	List<Field> effective(String projectId, String spaceId) {
		String sid = spaces.resolve(projectId, spaceId).getObjId();
		Map<String, CustomField> cfs = visibleCustomFields(projectId);
		Viewer viewer = viewer(projectId);
		Map<String, Field> out = new LinkedHashMap<>();
		FIXED.forEach(k -> out.put(k, withSource(SYSTEM_BY_KEY.get(k), "fixed", true)));
		for (CiraFieldAccess.TypeField tf : access.spaceFields(sid)) {
			Field f = resolve(tf.fieldKey(), cfs, viewer);
			if (f != null) {
				out.putIfAbsent(f.fieldKey(), withSource(f, "project", tf.defaultVisible()));
			}
		}
		return new ArrayList<>(out.values());
	}

	// ── 사람별 목록 컬럼 ────────────────────────────

	/** 없으면 기본 표시 필드. 있으면 죽은 키를 거르고, 처음 보는 기본 표시 필드는 뒤에 붙인다 (설계 5.3) */
	public FieldKeys columnPref(String projectId) {
		return columnPref(projectId, null);
	}

	public FieldKeys columnPref(String projectId, String spaceId) {
		requireMember(projectId);
		String sid = spaces.resolve(projectId, spaceId).getObjId();
		List<Field> eff = effective(projectId, sid);
		Set<String> effKeys = eff.stream().map(Field::fieldKey).collect(Collectors.toCollection(LinkedHashSet::new));
		CiraFieldAccess.ColumnPref pref = access.columnPref(currentUserId(), sid);
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
		return saveColumnPref(projectId, null, fieldKeys);
	}

	@Transactional
	public FieldKeys saveColumnPref(String projectId, String spaceId, List<String> fieldKeys) {
		requireMember(projectId);
		String sid = spaces.resolve(projectId, spaceId).getObjId();
		List<String> effKeys = effective(projectId, sid).stream().map(Field::fieldKey).toList();
		List<String> keys = new ArrayList<>(new LinkedHashSet<>(fieldKeys == null ? List.of() : fieldKeys));
		for (String k : keys) {
			if (!effKeys.contains(k)) {
				throw new IllegalArgumentException("이 프로젝트의 필드가 아닙니다: " + k);
			}
		}
		keys = withAlwaysShown(keys);
		access.saveColumnPref(currentUserId(), projectId, sid, writeJson(keys), writeJson(effKeys));
		return new FieldKeys(keys);
	}

	@Transactional
	public FieldKeys resetColumnPref(String projectId) {
		return resetColumnPref(projectId, null);
	}

	@Transactional
	public FieldKeys resetColumnPref(String projectId, String spaceId) {
		requireMember(projectId);
		String sid = spaces.resolve(projectId, spaceId).getObjId();
		access.deleteColumnPref(currentUserId(), sid);
		return columnPref(projectId, sid);
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
		applyValues(projectId, null, issueId, values, creating);
	}

	/** spaceId = Task 의 공간 (null = 공통 레이어 → 기본 공간 필드 세트) */
	@Transactional
	public void applyValues(String projectId, String spaceId, String issueId, Map<String, Object> values, boolean creating) {
		Map<String, Field> cfFields = effective(projectId, spaceId).stream().filter(f -> "cf".equals(f.kind()))
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

	/** 소속이 정확히 (projectId, typeId) 인 필드. 둘 다 null = 전역 */
	private CustomField requireField(String id, String projectId, String typeId) {
		CustomField cf = access.customField(tenant(), id);
		if (cf == null || !java.util.Objects.equals(cf.projectId(), projectId)
			|| !java.util.Objects.equals(cf.projectTypeId(), typeId)) {
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

	/** 같은 소속(전역 / 그 타입 / 그 프로젝트) 안에서 이름 중복 금지. 전역·타입은 PROJECT_ID null 이라 DB UNIQUE 가 못 막는다 */
	private void ensureUniqueName(String projectId, String typeId, String name, String selfId) {
		List<CustomField> same = projectId != null ? access.projectCustomFields(tenant(), projectId)
			: typeId != null ? access.typeFields(tenant(), typeId) : access.globalFields(tenant());
		if (same.stream().anyMatch(cf -> cf.fieldNm().equals(name) && !cf.id().equals(selfId))) {
			throw new IllegalArgumentException(projectId != null ? "이 프로젝트에 같은 이름의 필드가 있습니다: " + name
				: typeId != null ? "이 타입에 같은 이름의 필드가 있습니다: " + name : "이미 있는 필드 이름입니다: " + name);
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

	/**
	 * 유효 필드 계산에 쓰는 사용자 정의 필드: 이 프로젝트 필드 + 프리셋 필드.
	 * ponytail: 프리셋 필드는 V650 이전 세트가 원본 키(cf:타입필드)를 그대로 가리키는 경우용. 새 공간은 복사본만 쓴다.
	 */
	private Map<String, CustomField> visibleCustomFields(String projectId) {
		List<CustomField> all = new ArrayList<>(access.projectCustomFields(tenant(), projectId));
		all.addAll(access.allTypeFields(tenant()));
		return all.stream().collect(Collectors.toMap(CustomField::id, Function.identity()));
	}

	private Field resolve(String key, Map<String, CustomField> cfs, Viewer viewer) {
		if (key.startsWith("sys:")) {
			return SYSTEM_BY_KEY.get(key);
		}
		if (key.startsWith("cf:")) {
			CustomField cf = cfs.get(key.substring(3));
			return cf == null ? null : toField(cf, null, true, viewer);
		}
		return null;
	}

	/** 현재 사용자와 그 사람이 이 프로젝트 ADMIN 인지 (canEdit 계산용). 멤버가 아니면 null */
	private record Viewer(String userId, boolean projectAdmin) {}

	private Viewer viewer(String projectId) {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			return null;
		}
		String userId = currentUserId();
		return memberAccess.findByProjectIdAndUserId(projectId, userId)
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.map(m -> new Viewer(userId, "ADMIN".equals(m.getRole())))
			.orElse(null);
	}

	private static boolean canEdit(CustomField cf, Viewer v) {
		return cf.projectId() != null && v != null && (v.projectAdmin() || (cf.createdBy() != null && cf.createdBy().equals(v.userId())));
	}

	/** 프로젝트 필드 수정·삭제: 만든 사람 또는 프로젝트 ADMIN (CREATED_BY 가 없는 옛 필드는 ADMIN 만) */
	private void requireEditor(String projectId, CustomField cf) {
		requireMember(projectId);
		if (!canEdit(cf, viewer(projectId))) {
			throw new AccessDeniedException("만든 사람이나 프로젝트 관리자만 바꿀 수 있습니다");
		}
	}

	private Field toField(CustomField cf, String source, boolean defaultVisible, Viewer viewer) {
		List<String> options = cf.optionsJson() == null ? null : readKeys(cf.optionsJson());
		String scope = cf.projectId() != null ? "project" : cf.projectTypeId() != null ? "type" : "global";
		CustomField src = cf.sourceFieldId() == null ? null : access.customField(tenant(), cf.sourceFieldId());
		return new Field("cf:" + cf.id(), cf.fieldNm(), "cf", cf.fieldType(), options, cf.required(), false, source,
			defaultVisible, cf.projectId(), scope, cf.sourceFieldId(), src == null ? null : src.fieldNm(), cf.createdBy(),
			canEdit(cf, viewer));
	}

	private static Field withSource(Field f, String source, boolean defaultVisible) {
		return new Field(f.fieldKey(), f.name(), f.kind(), f.fieldType(), f.options(), f.required(), f.sortable(), source,
			defaultVisible, f.projectId(), f.scope(), f.sourceFieldId(), f.sourceName(), f.createdBy(), f.canEdit());
	}

	private static String id(Field f) {
		return f.fieldKey().substring(3);
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

	private static String tenant() {
		return TenantContext.get();
	}
}
