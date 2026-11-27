package tsh.d94.app.server.data.orm.cira.ciraField;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 타입·필드 세트·프로젝트 추가 필드·사람별 컬럼(V590)과 사용자 정의 필드·값(V160) 저장소.
 * ponytail: 테이블 6개를 JdbcTemplate 하나로 다룬다(StudyReadingAidsAccess 와 같은 방식). 엔티티가 필요해지면 그때 나눈다.
 */
@Service
@RequiredArgsConstructor
public class CiraFieldAccess {

	public record ProjectType(String id, String typeNm, String descr, int sortOrd) {}

	public record TypeField(String fieldKey, boolean defaultVisible) {}

	/** 소속: projectId·projectTypeId 둘 다 null = 전역, projectTypeId = 타입, projectId = 프로젝트 (V600) */
	public record CustomField(String id, String projectId, String projectTypeId, String fieldNm, String fieldType,
		boolean required, String optionsJson, int sortOrd, String sourceFieldId, String createdBy) {}

	public record CfValue(String issueId, String customFieldId, String valText, BigDecimal valNumber,
		java.time.LocalDate valDt, String valJson) {}

	public record ColumnPref(String fieldKeysJson, String knownKeysJson) {}

	private static final String CF_COLS =
		"OBJ_ID, PROJECT_ID, PROJECT_TYPE_ID, FIELD_NM, FIELD_TYPE, REQUIRED_YN, OPTIONS::text, SORT_ORD, SOURCE_FIELD_ID, CREATED_BY";
	private static final String CF_ORDER = " order by SORT_ORD, FIELD_NM";

	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate named;

	// ── 프로젝트 타입 ─────────────────────────────

	public List<ProjectType> types(String tenant) {
		return jdbc.query("select OBJ_ID, TYPE_NM, DESCR, SORT_ORD from SN_CIRA_PROJECT_TYPE where TENANT=? order by SORT_ORD, TYPE_NM",
			(r, n) -> new ProjectType(r.getString(1), r.getString(2), r.getString(3), r.getInt(4)), tenant);
	}

	public ProjectType type(String tenant, String id) {
		return types(tenant).stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
	}

	/** 프리셋 기본 보드 유형 (V630). 없으면 KANBAN */
	public String boardType(String typeId) {
		return jdbc.queryForList("select BOARD_TYPE from SN_CIRA_PROJECT_TYPE where OBJ_ID=?", String.class, typeId).stream()
			.findFirst().orElse("KANBAN");
	}

	public ProjectType typeByName(String tenant, String typeNm) {
		return types(tenant).stream().filter(t -> t.typeNm().equals(typeNm)).findFirst().orElse(null);
	}

	public String insertType(String tenant, String typeNm, String descr) {
		String id = UUID.randomUUID().toString();
		jdbc.update("""
			insert into SN_CIRA_PROJECT_TYPE(OBJ_ID, TENANT, TYPE_NM, DESCR, SORT_ORD)
			values(?,?,?,?,(select coalesce(max(SORT_ORD),0)+1 from SN_CIRA_PROJECT_TYPE where TENANT=?))""",
			id, tenant, typeNm, descr, tenant);
		return id;
	}

	public void updateType(String id, String typeNm, String descr) {
		jdbc.update("update SN_CIRA_PROJECT_TYPE set TYPE_NM=?, DESCR=?, MODIFIED_AT=now() where OBJ_ID=?", typeNm, descr, id);
		// 이름 사본(PROJECT_TYPE 칼럼, NOT NULL)도 맞춘다
		jdbc.update("update SN_CIRA_PROJECT set PROJECT_TYPE=? where PROJECT_TYPE_ID=?", typeNm, id);
	}

	public int projectsUsingType(String id) {
		return jdbc.queryForObject("select count(*) from SN_CIRA_PROJECT where PROJECT_TYPE_ID=? and DELETED_AT is null",
			Integer.class, id);
	}

	/** 삭제된(soft) 프로젝트의 FK 는 끊고 지운다 */
	public void deleteType(String id) {
		jdbc.update("update SN_CIRA_PROJECT set PROJECT_TYPE_ID=null where PROJECT_TYPE_ID=?", id);
		jdbc.update("delete from SN_CIRA_PROJECT_TYPE where OBJ_ID=?", id);
	}

	/** 타입 기본 세트 (SN_CIRA_PROJECT_TYPE_FIELD) */
	public List<TypeField> typeSet(String typeId) {
		return jdbc.query("select FIELD_KEY, DEFAULT_VISIBLE_YN from SN_CIRA_PROJECT_TYPE_FIELD where PROJECT_TYPE_ID=? order by SORT_ORD",
			(r, n) -> new TypeField(r.getString(1), "Y".equals(r.getString(2))), typeId);
	}

	public void replaceTypeFields(String typeId, List<TypeField> fields) {
		jdbc.update("delete from SN_CIRA_PROJECT_TYPE_FIELD where PROJECT_TYPE_ID=?", typeId);
		for (int i = 0; i < fields.size(); i++) {
			jdbc.update("insert into SN_CIRA_PROJECT_TYPE_FIELD(PROJECT_TYPE_ID, FIELD_KEY, SORT_ORD, DEFAULT_VISIBLE_YN) values(?,?,?,?)",
				typeId, fields.get(i).fieldKey(), i + 1, fields.get(i).defaultVisible() ? "Y" : "N");
		}
	}

	// ── 공간 필드 세트 (V630: SN_CIRA_PROJECT_FIELD 는 공간 소유) ─────────────────────────

	public List<TypeField> spaceFields(String spaceId) {
		return jdbc.query("select FIELD_KEY, DEFAULT_VISIBLE_YN from SN_CIRA_PROJECT_FIELD where SPACE_ID=? order by SORT_ORD",
			(r, n) -> new TypeField(r.getString(1), !"N".equals(r.getString(2))), spaceId);
	}

	public void replaceSpaceFields(String projectId, String spaceId, List<TypeField> fields) {
		jdbc.update("delete from SN_CIRA_PROJECT_FIELD where SPACE_ID=?", spaceId);
		for (int i = 0; i < fields.size(); i++) {
			jdbc.update("insert into SN_CIRA_PROJECT_FIELD(PROJECT_ID, SPACE_ID, FIELD_KEY, SORT_ORD, DEFAULT_VISIBLE_YN) values(?,?,?,?,?)",
				projectId, spaceId, fields.get(i).fieldKey(), i + 1, fields.get(i).defaultVisible() ? "Y" : "N");
		}
	}

	// ── 사용자 정의 필드 ─────────────────────────

	public List<CustomField> globalFields(String tenant) {
		return jdbc.query("select " + CF_COLS + " from SN_CIRA_CUSTOM_FIELD where TENANT=? and PROJECT_ID is null and PROJECT_TYPE_ID is null"
			+ CF_ORDER, this::cf, tenant);
	}

	public List<CustomField> typeFields(String tenant, String typeId) {
		return jdbc.query("select " + CF_COLS + " from SN_CIRA_CUSTOM_FIELD where TENANT=? and PROJECT_TYPE_ID=?" + CF_ORDER,
			this::cf, tenant, typeId);
	}

	public List<CustomField> projectCustomFields(String tenant, String projectId) {
		return jdbc.query("select " + CF_COLS + " from SN_CIRA_CUSTOM_FIELD where TENANT=? and PROJECT_ID=?" + CF_ORDER,
			this::cf, tenant, projectId);
	}

	/** 이 원본에서 복사해 간 필드 수 (타입·프로젝트 합계) */
	public int copiesOf(String sourceFieldId) {
		return jdbc.queryForObject("select count(*) from SN_CIRA_CUSTOM_FIELD where SOURCE_FIELD_ID=?", Integer.class, sourceFieldId);
	}

	public CustomField customField(String tenant, String id) {
		List<CustomField> rows = jdbc.query("select " + CF_COLS + " from SN_CIRA_CUSTOM_FIELD where OBJ_ID=? and TENANT=?", this::cf,
			id, tenant);
		return rows.isEmpty() ? null : rows.get(0);
	}

	public String insertCustomField(String tenant, String projectId, String projectTypeId, String fieldNm, String fieldType,
		boolean required, String optionsJson, String sourceFieldId, String createdBy) {
		String id = UUID.randomUUID().toString();
		jdbc.update("""
			insert into SN_CIRA_CUSTOM_FIELD(OBJ_ID, PROJECT_ID, PROJECT_TYPE_ID, FIELD_NM, FIELD_TYPE, REQUIRED_YN, OPTIONS, SORT_ORD,
			  SOURCE_FIELD_ID, CREATED_BY, TENANT, USE_STAT_CD, EVNT_NM, PREV_EVNT_NM, CREATED_AT, MODIFIED_AT)
			values(?,?,?,?,?,?,?::jsonb,0,?,?,?,'Usable','CreateCustomField','None',now(),now())""",
			id, projectId, projectTypeId, fieldNm, fieldType, required ? "Y" : "N", optionsJson, sourceFieldId, createdBy, tenant);
		return id;
	}

	public void updateCustomField(String id, String fieldNm, boolean required, String optionsJson) {
		jdbc.update("update SN_CIRA_CUSTOM_FIELD set FIELD_NM=?, REQUIRED_YN=?, OPTIONS=?::jsonb, MODIFIED_AT=now() where OBJ_ID=?",
			fieldNm, required ? "Y" : "N", optionsJson, id);
	}

	/** [프로젝트 수, Task 수] — 삭제 확인 문구용 */
	public int[] customFieldUsage(String id) {
		String key = "cf:" + id;
		Integer projects = jdbc.queryForObject("""
			select count(distinct p.OBJ_ID) from SN_CIRA_PROJECT p
			where p.DELETED_AT is null and (
			  exists (select 1 from SN_CIRA_PROJECT_TYPE_FIELD tf where tf.PROJECT_TYPE_ID=p.PROJECT_TYPE_ID and tf.FIELD_KEY=?)
			  or exists (select 1 from SN_CIRA_PROJECT_FIELD pf where pf.PROJECT_ID=p.OBJ_ID and pf.FIELD_KEY=?))""",
			Integer.class, key, key);
		Integer issues = jdbc.queryForObject("select count(*) from SN_CIRA_ISSUE_CF_VALUE where CUSTOM_FIELD_ID=?", Integer.class, id);
		return new int[] {projects, issues};
	}

	/** cf: 키는 FK 가 없으므로 참조·값을 같은 트랜잭션에서 지운다(호출자가 @Transactional) */
	public void deleteCustomField(String id) {
		String key = "cf:" + id;
		jdbc.update("delete from SN_CIRA_PROJECT_TYPE_FIELD where FIELD_KEY=?", key);
		jdbc.update("delete from SN_CIRA_PROJECT_FIELD where FIELD_KEY=?", key);
		jdbc.update("delete from SN_CIRA_ISSUE_CF_VALUE where CUSTOM_FIELD_ID=?", id);
		jdbc.update("delete from SN_CIRA_CUSTOM_FIELD where OBJ_ID=?", id);
	}

	// ── 값 ─────────────────────────────────────

	/** 페이지의 issue id 들을 한 번에 조회 (N+1 금지) */
	public Map<String, List<CfValue>> values(Collection<String> issueIds) {
		Map<String, List<CfValue>> out = new HashMap<>();
		if (issueIds.isEmpty()) {
			return out;
		}
		named.query("select ISSUE_ID, CUSTOM_FIELD_ID, VAL_TEXT, VAL_NUMBER, VAL_DT, VAL_JSON::text from SN_CIRA_ISSUE_CF_VALUE where ISSUE_ID in (:ids)",
			new MapSqlParameterSource("ids", issueIds), r -> {
				Date dt = r.getDate(5);
				CfValue v = new CfValue(r.getString(1), r.getString(2), r.getString(3), r.getBigDecimal(4),
					dt == null ? null : dt.toLocalDate(), r.getString(6));
				out.computeIfAbsent(v.issueId(), k -> new ArrayList<>()).add(v);
			});
		return out;
	}

	public void upsertValue(String tenant, CfValue v) {
		jdbc.update("""
			insert into SN_CIRA_ISSUE_CF_VALUE(OBJ_ID, ISSUE_ID, CUSTOM_FIELD_ID, VAL_TEXT, VAL_NUMBER, VAL_DT, VAL_JSON,
			  TENANT, USE_STAT_CD, EVNT_NM, PREV_EVNT_NM, CREATED_AT, MODIFIED_AT)
			values(gen_random_uuid()::text,?,?,?,?,?,?::jsonb,?,'Usable','SetCustomFieldValue','None',now(),now())
			on conflict (ISSUE_ID, CUSTOM_FIELD_ID) do update set VAL_TEXT=excluded.VAL_TEXT, VAL_NUMBER=excluded.VAL_NUMBER,
			  VAL_DT=excluded.VAL_DT, VAL_JSON=excluded.VAL_JSON, MODIFIED_AT=now()""",
			v.issueId(), v.customFieldId(), v.valText(), v.valNumber(), v.valDt() == null ? null : Date.valueOf(v.valDt()),
			v.valJson(), tenant);
	}

	public void deleteValue(String issueId, String customFieldId) {
		jdbc.update("delete from SN_CIRA_ISSUE_CF_VALUE where ISSUE_ID=? and CUSTOM_FIELD_ID=?", issueId, customFieldId);
	}

	// ── 사람별 컬럼 ─────────────────────────────

	public ColumnPref columnPref(String userId, String spaceId) {
		List<ColumnPref> rows = jdbc.query("select FIELD_KEYS::text, KNOWN_KEYS::text from SN_CIRA_USER_COLUMN_PREF where USER_ID=? and SPACE_ID=?",
			(r, n) -> new ColumnPref(r.getString(1), r.getString(2)), userId, spaceId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	public void saveColumnPref(String userId, String projectId, String spaceId, String fieldKeysJson, String knownKeysJson) {
		jdbc.update("""
			insert into SN_CIRA_USER_COLUMN_PREF(USER_ID, PROJECT_ID, SPACE_ID, FIELD_KEYS, KNOWN_KEYS) values(?,?,?,?::jsonb,?::jsonb)
			on conflict (USER_ID, SPACE_ID) do update set FIELD_KEYS=excluded.FIELD_KEYS, KNOWN_KEYS=excluded.KNOWN_KEYS, MODIFIED_AT=now()""",
			userId, projectId, spaceId, fieldKeysJson, knownKeysJson);
	}

	public void deleteColumnPref(String userId, String spaceId) {
		jdbc.update("delete from SN_CIRA_USER_COLUMN_PREF where USER_ID=? and SPACE_ID=?", userId, spaceId);
	}

	/** 공간 삭제: 그 공간 소유 필드 세트·컬럼 설정 */
	public void deleteSpaceRows(String spaceId) {
		jdbc.update("delete from SN_CIRA_PROJECT_FIELD where SPACE_ID=?", spaceId);
		jdbc.update("delete from SN_CIRA_USER_COLUMN_PREF where SPACE_ID=?", spaceId);
	}

	private CustomField cf(java.sql.ResultSet r, int n) throws java.sql.SQLException {
		return new CustomField(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5),
			"Y".equals(r.getString(6)), r.getString(7), r.getInt(8), r.getString(9), r.getString(10));
	}
}
