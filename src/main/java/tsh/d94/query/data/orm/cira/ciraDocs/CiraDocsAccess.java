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
		String defaultStream, String templateContent, boolean autoCreate) {
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

	// ── 문서 기준정보 ──

	public List<DocRule> rules(String projectId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID=? "
			+ "order by ISSUE_TYPE_ID, SORT_ORDER, FOLDER_NAME", CiraDocsAccess::rule, projectId);
	}

	public List<DocRule> rules(String projectId, String issueTypeId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID=? and ISSUE_TYPE_ID=? "
			+ "order by SORT_ORDER, FOLDER_NAME", CiraDocsAccess::rule, projectId, issueTypeId);
	}

	public Optional<DocRule> rule(String projectId, String ruleId) {
		return jdbc.query("select " + RULE_COLS + " from SN_CIRA_DOC_RULE where PROJECT_ID=? and OBJ_ID=?",
			CiraDocsAccess::rule, projectId, ruleId).stream().findFirst();
	}

	/** 그 Task 타입의 슬롯을 통째로 바꾼다. id 가 있으면 유지(연결된 RULE_ID 가 계속 맞도록), 없으면 새로 */
	public void replaceRules(String projectId, String issueTypeId, List<DocRule> rules) {
		jdbc.update("delete from SN_CIRA_DOC_RULE where PROJECT_ID=? and ISSUE_TYPE_ID=?", projectId, issueTypeId);
		jdbc.batchUpdate("insert into SN_CIRA_DOC_RULE(OBJ_ID,PROJECT_ID,ISSUE_TYPE_ID,FOLDER_NAME,SORT_ORDER,"
				+ "DEFAULT_KIND,DEFAULT_STREAM,TEMPLATE_CONTENT,AUTO_CREATE_YN) values(?,?,?,?,?,?,?,?,?)",
			rules.stream().map(r -> new Object[] {r.id() == null || r.id().isBlank() ? UUID.randomUUID().toString() : r.id(),
				projectId, issueTypeId, r.folderName(), r.sortOrder(), r.defaultKind(), r.defaultStream(),
				r.templateContent(), r.autoCreate() ? "Y" : "N"}).toList());
	}

	private static final String RULE_COLS = "OBJ_ID,ISSUE_TYPE_ID,FOLDER_NAME,SORT_ORDER,DEFAULT_KIND,DEFAULT_STREAM,"
		+ "TEMPLATE_CONTENT,AUTO_CREATE_YN";

	private static DocRule rule(java.sql.ResultSet r, int i) throws java.sql.SQLException {
		return new DocRule(r.getString(1), r.getString(2), r.getString(3), r.getInt(4), r.getString(5), r.getString(6),
			r.getString(7), "Y".equals(r.getString(8)));
	}
}
