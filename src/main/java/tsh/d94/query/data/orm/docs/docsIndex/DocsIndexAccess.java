package tsh.d94.app.server.data.orm.docs.docsIndex;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 버전·색인(링크·태그·Task)·목록 조회. SQL 은 apService 밖, 여기에만 둔다 (StudyReadingAidsAccess 와 같음).
 * 링크 대상은 제목으로 저장하고, 같은 vault 의 노트와 제목으로 맞춘다. 목록·Task 는 접근 가능한 vault 들(통합 뷰).
 */
@Service
@RequiredArgsConstructor
public class DocsIndexAccess {

	private final JdbcTemplate jdbc;

	public record Link(String toTitle, String rel) {
	}

	public record Task(int lineNo, String text, String status, LocalDate dueDt) {
	}

	public record NoteRow(String id, String vaultId, String title, String folderId, String kind, String stream,
		LocalDateTime modifiedAt) {
	}

	public record VersionRow(int version, String title, String content, String editedBy, LocalDateTime editedAt) {
	}

	public record TaskRow(String noteId, String vaultId, String noteTitle, int lineNo, String text, String status, LocalDate dueDt) {
	}

	public record LineageRow(String id, String title, String kind, String stream, String originTitle) {
	}

	public record GraphEdge(String fromId, String toId, String rel) {
	}

	// ── 색인 ──

	/** 노트 하나의 색인을 통째로 바꾼다 (저장할 때마다) */
	public void replaceIndex(String noteId, Collection<Link> links, Collection<String> tags, Collection<Task> tasks) {
		jdbc.update("delete from SN_DOCS_LINK where FROM_ID=?", noteId);
		jdbc.update("delete from SN_DOCS_TAG where NOTE_ID=?", noteId);
		jdbc.update("delete from SN_DOCS_TASK where NOTE_ID=?", noteId);
		jdbc.batchUpdate("insert into SN_DOCS_LINK(FROM_ID,TO_TITLE,REL) values(?,?,?)",
			links.stream().map(l -> new Object[] {noteId, l.toTitle(), l.rel()}).toList());
		jdbc.batchUpdate("insert into SN_DOCS_TAG(NOTE_ID,TAG) values(?,?)",
			tags.stream().map(t -> new Object[] {noteId, t}).toList());
		jdbc.batchUpdate("insert into SN_DOCS_TASK(NOTE_ID,LINE_NO,TEXT,STATUS,DUE_DT) values(?,?,?,?,?)",
			tasks.stream().map(t -> new Object[] {noteId, t.lineNo(), t.text(), t.status(), t.dueDt()}).toList());
	}

	public List<String> tags(String noteId) {
		return jdbc.queryForList("select TAG from SN_DOCS_TAG where NOTE_ID=? order by TAG", String.class, noteId);
	}

	// ── 버전 ──

	/**
	 * 버전 이력 1건. 자동 저장으로 이력이 폭증하지 않게, 같은 사람이 5분 안에 같은 제목으로 다시 저장하면
	 * 직전 이력을 지우고 새 것으로 바꾼다(제목 변경은 의미 있는 사건이라 남김).
	 */
	public void addVersion(String noteId, int version, String title, String content, String editedBy) {
		jdbc.update("delete from SN_DOCS_NOTE_VER v where v.NOTE_ID=? and v.EDITED_BY=? and v.TITLE=? "
				+ "and v.EDITED_AT > CURRENT_TIMESTAMP - interval '5 minutes' "
				+ "and v.VERSION=(select max(VERSION) from SN_DOCS_NOTE_VER where NOTE_ID=?)",
			noteId, editedBy, title, noteId);
		jdbc.update("insert into SN_DOCS_NOTE_VER(NOTE_ID,VERSION,TITLE,CONTENT,EDITED_BY) values(?,?,?,?,?)",
			noteId, version, title, content, editedBy);
	}

	public List<VersionRow> versions(String noteId) {
		return jdbc.query("select VERSION,TITLE,CONTENT,EDITED_BY,EDITED_AT from SN_DOCS_NOTE_VER where NOTE_ID=? "
				+ "order by VERSION desc",
			(r, n) -> new VersionRow(r.getInt(1), r.getString(2), r.getString(3), r.getString(4), ts(r.getTimestamp(5))),
			noteId);
	}

	// ── 조회 ──

	/** 목록·검색. null 인자는 조건에서 뺀다. folderId 는 "" 이면 최상위만. */
	public List<NoteRow> listNotes(Collection<String> vaultIds, String folderId, String q, String kind, String stream, String tag,
		String origin) {
		StringBuilder sql = new StringBuilder(
			"select " + NOTE_COLS + " from SN_DOCS_NOTE n where n.VAULT_ID = any(?)");
		List<Object> args = new ArrayList<>();
		args.add(arr(vaultIds)); // List.of(배열) 은 varargs 로 펼쳐진다
		if (folderId != null) {
			if (folderId.isEmpty()) {
				sql.append(" and n.FOLDER_ID is null");
			} else {
				sql.append(" and n.FOLDER_ID=?");
				args.add(folderId);
			}
		}
		if (q != null && !q.isBlank()) {
			sql.append(" and (n.TITLE ilike ? or n.CONTENT ilike ?)");
			String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			args.add(like);
			args.add(like);
		}
		if (kind != null) {
			sql.append(" and n.KIND=?");
			args.add(kind);
		}
		if (stream != null) {
			sql.append(" and n.STREAM=?");
			args.add(stream);
		}
		if (tag != null) {
			sql.append(" and exists(select 1 from SN_DOCS_TAG t where t.NOTE_ID=n.OBJ_ID and t.TAG=?)");
			args.add(tag);
		}
		if (origin != null) {
			sql.append(" and exists(select 1 from SN_DOCS_LINK l where l.FROM_ID=n.OBJ_ID and l.REL='origin' "
				+ "and l.TO_TITLE=?)");
			args.add(origin);
		}
		sql.append(" order by n.MODIFIED_AT desc nulls last limit 500"); // ponytail: 페이지 없이 500건, 넘으면 페이지네이션
		return jdbc.query(sql.toString(), DocsIndexAccess::noteRow, args.toArray());
	}

	public List<String> titles(Collection<String> vaultIds, String prefix) {
		return jdbc.queryForList("select distinct TITLE from SN_DOCS_NOTE where VAULT_ID = any(?) and TITLE ilike ? order by TITLE limit 20",
			String.class, arr(vaultIds), (prefix == null ? "" : prefix.replace("%", "\\%").replace("_", "\\_")) + "%");
	}

	/** title 을 링크(본문 [[ ]] 또는 origin)한 같은 vault 노트들 */
	public List<NoteRow> backlinks(String vaultId, String title) {
		return jdbc.query("select distinct " + NOTE_COLS + " from SN_DOCS_NOTE n "
				+ "join SN_DOCS_LINK l on l.FROM_ID=n.OBJ_ID where n.VAULT_ID=? and l.TO_TITLE=? order by n.TITLE",
			DocsIndexAccess::noteRow, vaultId, title);
	}

	/** 노트 제목들 중 실제로 있는 것 (없는 노트 링크 표시용) */
	public List<String> existingTitles(String vaultId, Collection<String> titles) {
		if (titles.isEmpty()) {
			return List.of();
		}
		return jdbc.queryForList("select TITLE from SN_DOCS_NOTE where VAULT_ID=? and TITLE = any(?)", String.class,
			vaultId, arr(titles));
	}

	// ── Task ──

	public List<TaskRow> tasks(Collection<String> vaultIds, String status, String tag, String stream, LocalDate dueBefore) {
		StringBuilder sql = new StringBuilder("select n.OBJ_ID,n.VAULT_ID,n.TITLE,t.LINE_NO,t.TEXT,t.STATUS,t.DUE_DT "
			+ "from SN_DOCS_TASK t join SN_DOCS_NOTE n on n.OBJ_ID=t.NOTE_ID where n.VAULT_ID = any(?)");
		List<Object> args = new ArrayList<>();
		args.add(arr(vaultIds)); // List.of(배열) 은 varargs 로 펼쳐진다
		if (status != null) {
			sql.append(" and t.STATUS=?");
			args.add(status);
		}
		if (tag != null) {
			sql.append(" and exists(select 1 from SN_DOCS_TAG g where g.NOTE_ID=n.OBJ_ID and g.TAG=?)");
			args.add(tag);
		}
		if (stream != null) {
			sql.append(" and n.STREAM=?");
			args.add(stream);
		}
		if (dueBefore != null) {
			sql.append(" and t.DUE_DT<=?");
			args.add(dueBefore);
		}
		sql.append(" order by t.DUE_DT nulls last, n.TITLE, t.LINE_NO limit 1000");
		return jdbc.query(sql.toString(), (r, n) -> new TaskRow(r.getString(1), r.getString(2), r.getString(3),
			r.getInt(4), r.getString(5), r.getString(6), r.getObject(7, LocalDate.class)), args.toArray());
	}

	// ── 계보 ──

	/**
	 * 노트가 속한 origin 트리 전체: 루트(origin 이 없는 조상)까지 올라간 뒤 하위를 모두 내려간다.
	 * ponytail: 순환(A origin B, B origin A)은 UNION 으로 중복 제거되어 멈춘다. 깊이 제한 50.
	 */
	public List<LineageRow> lineage(String vaultId, String noteId) {
		return jdbc.query("""
			with recursive o as (
			  select n.OBJ_ID, n.TITLE, (select l.TO_TITLE from SN_DOCS_LINK l where l.FROM_ID=n.OBJ_ID and l.REL='origin' limit 1) as ORIGIN, 0 as D
			  from SN_DOCS_NOTE n where n.OBJ_ID=? and n.VAULT_ID=?
			  union
			  select p.OBJ_ID, p.TITLE, (select l.TO_TITLE from SN_DOCS_LINK l where l.FROM_ID=p.OBJ_ID and l.REL='origin' limit 1), o.D+1
			  from o join SN_DOCS_NOTE p on p.VAULT_ID=? and p.TITLE=o.ORIGIN where o.D < 50
			), root as (select OBJ_ID, TITLE from o order by D desc limit 1),
			t as (
			  select n.OBJ_ID, n.TITLE, n.KIND, n.STREAM, cast(null as varchar) as ORIGIN, 0 as D from SN_DOCS_NOTE n join root r on r.OBJ_ID=n.OBJ_ID
			  union
			  select c.OBJ_ID, c.TITLE, c.KIND, c.STREAM, t.TITLE, t.D+1
			  from t join SN_DOCS_LINK l on l.REL='origin' and l.TO_TITLE=t.TITLE
			  join SN_DOCS_NOTE c on c.OBJ_ID=l.FROM_ID and c.VAULT_ID=? where t.D < 50
			)
			select distinct on (OBJ_ID) OBJ_ID, TITLE, KIND, STREAM, ORIGIN from t order by OBJ_ID, D""",
			(r, n) -> new LineageRow(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)),
			noteId, vaultId, vaultId, vaultId);
	}

	// ── 그래프 ──

	/** ponytail: vault 하나당 노트 5000개 상한. 넘으면 페이지·서버 측 로컬 그래프로 */
	public List<NoteRow> graphNotes(Collection<String> vaultIds) {
		return jdbc.query("select " + NOTE_COLS + " from SN_DOCS_NOTE n where n.VAULT_ID = any(?) order by n.TITLE limit 5000",
			DocsIndexAccess::noteRow, (Object) arr(vaultIds));
	}

	/** 같은 vault 안 노트끼리 이어진 링크(link·origin). 없는 노트·자기 자신 링크는 뺀다 */
	public List<GraphEdge> graphEdges(Collection<String> vaultIds) {
		return jdbc.query("select distinct l.FROM_ID, t.OBJ_ID, l.REL from SN_DOCS_LINK l "
				+ "join SN_DOCS_NOTE f on f.OBJ_ID=l.FROM_ID and f.VAULT_ID = any(?) "
				+ "join SN_DOCS_NOTE t on t.VAULT_ID=f.VAULT_ID and t.TITLE=l.TO_TITLE where t.OBJ_ID<>l.FROM_ID",
			(r, n) -> new GraphEdge(r.getString(1), r.getString(2), r.getString(3)), (Object) arr(vaultIds));
	}

	private static final String NOTE_COLS = "n.OBJ_ID,n.VAULT_ID,n.TITLE,n.FOLDER_ID,n.KIND,n.STREAM,n.MODIFIED_AT";

	private static NoteRow noteRow(java.sql.ResultSet r, int i) throws java.sql.SQLException {
		return new NoteRow(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5),
			r.getString(6), ts(r.getTimestamp(7)));
	}

	private static String[] arr(Collection<String> c) {
		return c.toArray(String[]::new);
	}

	private static LocalDateTime ts(Timestamp t) {
		return t == null ? null : t.toLocalDateTime();
	}
}
