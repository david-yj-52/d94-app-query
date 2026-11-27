package tsh.d94.app.server.data.orm.cira.ciraDocs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * CIRA Task ↔ Docs 연결(SN_CIRA_ISSUE_DOC)과 프로젝트·Task 타입별 문서 폴더 슬롯(SN_CIRA_DOC_RULE). V620.
 * ponytail: 엔티티 없이 JdbcTemplate (CiraFieldAccess 와 같음).
 */
@Service
@RequiredArgsConstructor
public class CiraDocsAccess {

	public static final String MAIN = "MAIN";
	public static final String DERIVED = "DERIVED";

	private final JdbcTemplate jdbc;

	public record IssueDoc(String noteId, String docRole, String ruleId) {
	}

	public record DocRule(String id, String issueTypeId, String folderName, int sortOrder, String defaultKind,
		String defaultStream, String templateContent, boolean autoCreate, String spaceId, String sourceRuleId,
		boolean locked) {

		/** 저장 요청용 (스코프·잠금은 replaceRules 가 정한다) */
		public DocRule(String id, String issueTypeId, String folderName, int sortOrder, String defaultKind,
			String defaultStream, String templateContent, boolean autoCreate) {
			this(id, issueTypeId, folderName, sortOrder, defaultKind, defaultStream, templateContent, autoCreate, null, null,
				false);
		}
	}

	// ── Task ↔ 노트 ──

	public Optional<String> mainNoteId(String issueId) {
		return jdbc.queryForList("select NOTE_ID from SN_CIRA_ISSUE_DOC where ISSUE_ID=? and DOC_ROLE='MAIN'",
			String.class, issueId).stream().findFirst();
	}

	/** MAIN 먼저, 그다음 연결한 순서 */
	public List<IssueDoc> issueDocs(String issueId) {
		return jdbc.query("select NOTE_ID,DOC_ROLE,RULE_ID from SN_CIRA_ISSUE_DOC where ISSUE_ID=? "
				+ "order by DOC_ROLE desc, CREATED_AT",
			(r, n) -> new IssueDoc(r.getString(1), r.getString(2), r.getString(3)), issueId);
	}

	public void link(String issueId, String noteId, String docRole, String ruleId) {
		jdbc.update("insert into SN_CIRA_ISSUE_DOC(ISSUE_ID,NOTE_ID,DOC_ROLE,RULE_ID) values(?,?,?,?) "
			+ "on conflict do nothing", issueId, noteId, docRole, ruleId);
	}

	// ── 문서 기준정보 (V630: 전사 PROJECT_ID null · 프로젝트 공통 SPACE_ID null · 공간 SPACE_ID) ──

	/** 프로젝트 공통 규칙 (가져온 잠금 규칙 포함) */
	public List<DocRule> rules(String projectId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID=? and SPACE_ID is null "
			+ "order by ISSUE_TYPE_ID, SORT_ORDER, FOLDER_NAME", CiraDocsAccess::rule, projectId);
	}

	/** 프로젝트 공통 규칙 중 그 Task 타입 */
	public List<DocRule> rules(String projectId, String issueTypeId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID=? and SPACE_ID is null "
			+ "and ISSUE_TYPE_ID=? order by SORT_ORDER, FOLDER_NAME", CiraDocsAccess::rule, projectId, issueTypeId);
	}

	/** Task 에 적용되는 규칙: 프로젝트 공통(잠금 먼저) + 그 공간 규칙 */
	public List<DocRule> effectiveRules(String projectId, String spaceId, String issueTypeId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID=? and ISSUE_TYPE_ID=? "
				+ "and (SPACE_ID is null or SPACE_ID=?) order by SPACE_ID nulls first, LOCKED_YN desc, SORT_ORDER, FOLDER_NAME",
			CiraDocsAccess::rule, projectId, issueTypeId, spaceId);
	}

	public List<DocRule> spaceRules(String spaceId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where SPACE_ID=? "
			+ "order by ISSUE_TYPE_ID, SORT_ORDER, FOLDER_NAME", CiraDocsAccess::rule, spaceId);
	}

	public List<DocRule> globalRules() {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID is null "
			+ "order by ISSUE_TYPE_ID, SORT_ORDER, FOLDER_NAME", CiraDocsAccess::rule);
	}

	/** 프로젝트·공간 규칙 하나 (전사 규칙은 여기서 안 찾는다) */
	public Optional<DocRule> rule(String projectId, String ruleId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID=? and OBJ_ID=?",
			CiraDocsAccess::rule, projectId, ruleId).stream().findFirst();
	}

	/**
	 * 한 스코프(전사 / 프로젝트 공통 / 공간) · Task 타입의 잠기지 않은 슬롯을 통째로 바꾼다. 잠금 규칙(기준정보에서 가져온 것)은 그대로.
	 * id 가 있으면 유지(연결된 RULE_ID 가 계속 맞도록), 없으면 새로.
	 */
	public void replaceRules(String projectId, String spaceId, String issueTypeId, List<DocRule> rules) {
		jdbc.update("delete from SN_CIRA_DOC_RULE where PROJECT_ID is not distinct from ? and SPACE_ID is not distinct from ? "
			+ "and ISSUE_TYPE_ID=? and LOCKED_YN='N'", projectId, spaceId, issueTypeId);
		jdbc.batchUpdate("insert into SN_CIRA_DOC_RULE(OBJ_ID,PROJECT_ID,SPACE_ID,ISSUE_TYPE_ID,FOLDER_NAME,SORT_ORDER,"
				+ "DEFAULT_KIND,DEFAULT_STREAM,TEMPLATE_CONTENT,AUTO_CREATE_YN,LOCKED_YN) values(?,?,?,?,?,?,?,?,?,?,'N')",
			rules.stream().map(r -> new Object[] {r.id() == null || r.id().isBlank() ? UUID.randomUUID().toString() : r.id(),
				projectId, spaceId, issueTypeId, r.folderName(), r.sortOrder(), r.defaultKind(), r.defaultStream(),
				r.templateContent(), r.autoCreate() ? "Y" : "N"}).toList());
	}

	/** 전사 규칙 → 프로젝트 공통 잠금 복사. 이미 가져왔거나 같은 폴더 이름이 있으면 건너뛴다. 가져온 수 */
	public int importGlobal(String projectId, List<String> globalRuleIds) {
		int n = 0;
		for (String id : globalRuleIds) {
			n += jdbc.update("""
				insert into SN_CIRA_DOC_RULE(OBJ_ID,PROJECT_ID,SPACE_ID,ISSUE_TYPE_ID,FOLDER_NAME,SORT_ORDER,DEFAULT_KIND,
				  DEFAULT_STREAM,TEMPLATE_CONTENT,AUTO_CREATE_YN,SOURCE_RULE_ID,LOCKED_YN)
				select gen_random_uuid()::text, ?, null, g.ISSUE_TYPE_ID, g.FOLDER_NAME, g.SORT_ORDER, g.DEFAULT_KIND,
				  g.DEFAULT_STREAM, g.TEMPLATE_CONTENT, g.AUTO_CREATE_YN, g.OBJ_ID, 'Y'
				from SN_CIRA_DOC_RULE g
				where g.OBJ_ID=? and g.PROJECT_ID is null
				  and not exists (select 1 from SN_CIRA_DOC_RULE x where x.PROJECT_ID=? and x.SPACE_ID is null
				    and (x.SOURCE_RULE_ID=g.OBJ_ID or (x.ISSUE_TYPE_ID=g.ISSUE_TYPE_ID and x.FOLDER_NAME=g.FOLDER_NAME)))""",
				projectId, id, projectId);
		}
		return n;
	}

	public void deleteSpaceRules(String spaceId) {
		jdbc.update("delete from SN_CIRA_DOC_RULE where SPACE_ID=?", spaceId);
	}

	private static final String RULE_COLS = "OBJ_ID,ISSUE_TYPE_ID,FOLDER_NAME,SORT_ORDER,DEFAULT_KIND,DEFAULT_STREAM,"
		+ "TEMPLATE_CONTENT,AUTO_CREATE_YN,SPACE_ID,SOURCE_RULE_ID,LOCKED_YN";

	private static DocRule rule(java.sql.ResultSet r, int i) throws java.sql.SQLException {
		return new DocRule(r.getString(1), r.getString(2), r.getString(3), r.getInt(4), r.getString(5), r.getString(6),
			r.getString(7), "Y".equals(r.getString(8)), r.getString(9), r.getString(10), "Y".equals(r.getString(11)));
	}
}
