package tsh.d94.app.server.apService.cira;

import static tsh.d94.app.server.apService.docs.DocsException.Code.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.automation.events.IssueAssignedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueCreatedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueStatusChangedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueTitleChangedEvent;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.docs.Docs;
import tsh.d94.app.server.apService.docs.DocsDtos.LineageNode;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteResponse;
import tsh.d94.app.server.apService.docs.DocsEvolveService;
import tsh.d94.app.server.apService.docs.DocsException;
import tsh.d94.app.server.apService.docs.DocsFolderService;
import tsh.d94.app.server.apService.docs.DocsMarkdownIndexer;
import tsh.d94.app.server.apService.docs.DocsNoteService;
import tsh.d94.app.server.apService.docs.DocsVaultService;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraDocs.CiraDocsAccess;
import tsh.d94.app.server.data.orm.cira.ciraDocs.CiraDocsAccess.DocRule;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.app.server.data.orm.docs.docsNote.SnDocsNoteAccess;
import tsh.d94.app.server.data.orm.docs.docsNote.SnDocsNoteModel;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/**
 * CIRA Task ↔ Docs. Task 가 생기면 프로젝트 Team vault 의 task/{projectKey}/{issueKey} 에 메인 노트를 만들고,
 * 상태·담당자 변경을 메인 노트의 "## 진행 경과" 끝에 한 줄씩 남긴다. 파생 문서는 Task 타입별 폴더 슬롯(기준정보)
 * 또는 자유 폴더로 만든다. 설계: DOCS 노트 "CIRA – Docs 통합 및 다중 Vault 설계".
 * 권한: 프로젝트 Usable 멤버(아니면 404), 기준정보 수정은 프로젝트 ADMIN.
 */
@Service
@RequiredArgsConstructor
public class CiraDocsService {

	static final String PROGRESS = "## 진행 경과";
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	/** ponytail: 진행 경과 시각은 로그와 같은 LOG_TZ (없으면 JVM 기본) */
	private static final ZoneId ZONE = ZoneId.of(System.getenv().getOrDefault("LOG_TZ", ZoneId.systemDefault().getId()));

	private final CiraDocsAccess access;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraProjectAccess projectAccess;
	private final SnCiraProjectMemberAccess memberAccess;
	private final SnCiraIssueStatusAccess statusAccess;
	private final SnCiraCiraIssueTypeAccess issueTypeAccess;
	private final GsUserAccess userAccess;
	private final SnDocsNoteAccess noteAccess;
	private final DocsVaultService vaults;
	private final DocsFolderService folders;
	private final DocsNoteService notes;
	private final DocsEvolveService evolve;
	private final IssueService issueService;

	public record DocRuleDto(String id, String issueTypeId, String folderName, Integer sortOrder, String defaultKind,
		String defaultStream, String templateContent, Boolean autoCreate) {
		static DocRuleDto of(DocRule r) {
			return new DocRuleDto(r.id(), r.issueTypeId(), r.folderName(), r.sortOrder(), r.defaultKind(),
				r.defaultStream(), r.templateContent(), r.autoCreate());
		}
	}

	/** role: MAIN | DERIVED */
	public record LinkedDoc(String noteId, String vaultId, String title, String folderId, String kind, String stream,
		String role, String ruleId) {
	}

	/** main 이 null 이면 아직 연결된 메인 노트가 없다(배포 전 이슈 → POST 로 온디맨드 생성) */
	public record IssueDocsResponse(LinkedDoc main, List<LinkedDoc> derived, List<LineageNode> lineage,
		List<DocRuleDto> rules) {
	}

	/** ruleId 가 있으면 그 슬롯, 없으면 folderName(선택, 메인 노트 폴더 아래)으로 자유 파생 */
	public record DeriveFromIssueRequest(String title, String ruleId, String folderName, String kind, String stream) {
	}

	public record PromoteRequest(String issueTypeId, String title) {
	}

	// ── 이벤트 (IssueService 트랜잭션 안에서 동기 실행) ──

	/** source 가 IssueService 일 때만: 로그인 사용자·트랜잭션이 있는 경로 (자동화가 직접 쏜 이벤트 제외) */
	@EventListener
	public void onIssueCreated(IssueCreatedEvent e) {
		if (e.getSource() instanceof IssueService) {
			createMain(issueAccess.findById(e.getIssueId()));
		}
	}

	@EventListener
	public void onStatusChanged(IssueStatusChangedEvent e) {
		if (e.getSource() instanceof IssueService) {
			appendProgress(e.getIssueId(), "상태 변경: " + statusNm(e.getFromStatus()) + " → " + statusNm(e.getToStatus())
				+ " (by " + userNm(e.getChangedBy()) + ")");
		}
	}

	/** 이슈 제목 변경 → 메인 노트 제목 `{issueKey} {새 제목}` (다른 노트의 [[링크]] 도 치환). 그 제목이 이미 있으면 objId 앞 8자를 붙인다 */
	@EventListener
	public void onTitleChanged(IssueTitleChangedEvent e) {
		if (!(e.getSource() instanceof IssueService)) {
			return;
		}
		SnCiraIssueModel issue = issueAccess.findById(e.getIssueId());
		access.mainNoteId(issue.getObjId()).flatMap(noteAccess::findByIdOptional).ifPresent(n -> {
			String title = mainTitle(issue.getIssueKey(), issue.getTitle());
			if (title.equals(n.getTitle())) {
				return;
			}
			if (notes.titleTaken(n.getVaultId(), title)) {
				title = mainTitle(issue.getIssueKey(), issue.getTitle() + " " + issue.getObjId().substring(0, 8));
			}
			notes.renameInternal(n, title);
		});
	}

	@EventListener
	public void onAssigned(IssueAssignedEvent e) {
		if (e.getSource() instanceof IssueService) {
			appendProgress(e.getIssueId(), "담당자 변경: " + userNm(e.getAssigneeId()) + " (by " + userNm(e.getAssignedBy()) + ")");
		}
	}

	// ── 조회·온디맨드 ──

	public IssueDocsResponse docs(String projectId, String issueId) {
		SnCiraIssueModel issue = requireIssue(projectId, issueId);
		LinkedDoc main = null;
		List<LinkedDoc> derived = new ArrayList<>();
		for (var d : access.issueDocs(issueId)) {
			SnDocsNoteModel n = noteAccess.findByIdOptional(d.noteId()).orElse(null);
			if (n == null) {
				continue;
			}
			LinkedDoc ld = new LinkedDoc(n.getObjId(), n.getVaultId(), n.getTitle(), n.getFolderId(), n.getKind(),
				n.getStream(), d.docRole(), d.ruleId());
			if (CiraDocsAccess.MAIN.equals(d.docRole())) {
				main = ld;
			} else {
				derived.add(ld);
			}
		}
		List<LineageNode> lineage = main == null ? List.of() : evolve.lineage(main.noteId());
		List<DocRuleDto> rules = access.rules(projectId, issue.getIssueTypeId()).stream().map(DocRuleDto::of).toList();
		return new IssueDocsResponse(main, derived, lineage, rules);
	}

	/** 메인 노트가 없는 (배포 전) Task 에 메인 노트를 만들어 연결. 이미 있으면 그대로 */
	@Transactional
	public IssueDocsResponse ensureMain(String projectId, String issueId) {
		SnCiraIssueModel issue = requireIssue(projectId, issueId);
		if (access.mainNoteId(issueId).isEmpty()) {
			createMain(issue);
		}
		return docs(projectId, issueId);
	}

	@Transactional
	public NoteResponse derive(String projectId, String issueId, DeriveFromIssueRequest req) {
		SnCiraIssueModel issue = requireIssue(projectId, issueId);
		String mainId = access.mainNoteId(issueId).orElseGet(() -> createMain(issue).getObjId());
		SnDocsNoteModel main = noteAccess.findById(mainId);
		String[] taskPath = taskPath(issue);
		String folderId;
		String kind;
		String stream;
		String template = "";
		String ruleId = blankToNull(req.ruleId());
		if (ruleId != null) {
			DocRule rule = access.rule(projectId, ruleId).filter(r -> r.issueTypeId().equals(issue.getIssueTypeId()))
				.orElseThrow(() -> new DocsException(NOT_FOUND));
			folderId = folders.ensurePath(main.getVaultId(), append(taskPath, rule.folderName()));
			kind = rule.defaultKind() == null ? "doc" : rule.defaultKind();
			stream = rule.defaultStream();
			template = rule.templateContent() == null ? "" : rule.templateContent();
		} else {
			String folderName = blankToNull(req.folderName());
			folderId = folderName == null ? main.getFolderId()
				: folders.ensurePath(main.getVaultId(), append(taskPath, folderName));
			kind = DocsEvolveService.kind(req.kind() == null ? "doc" : req.kind());
			stream = DocsEvolveService.stream(req.stream());
		}
		SnDocsNoteModel n = notes.createIn(main.getVaultId(), folderId, req.title(),
			DocsEvolveService.derivedContent(main.getTitle(), kind, stream) + template);
		access.link(issueId, n.getObjId(), CiraDocsAccess.DERIVED, ruleId);
		return notes.get(n.getObjId());
	}

	/**
	 * Docs 노트 → CIRA Task. 이슈 생성 이벤트로 메인 노트가 생기고, 그 frontmatter 에 origin 으로 원본을 건다.
	 * ponytail: 원본이 다른 vault(예: Private)면 origin 링크는 표시만 되고 계보 트리에는 이어지지 않는다(크로스 vault 링크 미구현).
	 */
	@Transactional
	public IssueResponse promote(String projectId, String noteId, PromoteRequest req) {
		SnDocsNoteModel src = notes.requireOwned(noteId);
		String title = blankToNull(req.title()) == null ? src.getTitle() : req.title().trim();
		IssueResponse issue = issueService.createIssue(projectId, CreateIssueRequest.builder()
			.title(title).issueTypeId(blankToNull(req.issueTypeId())).build());
		access.mainNoteId(issue.getId()).ifPresent(mainId -> {
			SnDocsNoteModel main = noteAccess.findById(mainId);
			notes.writeInternal(main, main.getTitle(),
				DocsMarkdownIndexer.setFrontmatter(main.getContent(), "origin", "\"[[" + src.getTitle() + "]]\""));
		});
		return issue;
	}

	// ── 기준정보 ──

	public List<DocRuleDto> rules(String projectId) {
		requireMember(projectId);
		return access.rules(projectId).stream().map(DocRuleDto::of).toList();
	}

	/** 그 Task 타입의 슬롯 목록을 통째로 저장 (빈 목록 = 모두 삭제). 프로젝트 ADMIN */
	@Transactional
	public List<DocRuleDto> saveRules(String projectId, String issueTypeId, List<DocRuleDto> body) {
		if (!"ADMIN".equals(requireMember(projectId).getRole())) {
			throw new DocsException(FORBIDDEN, "문서 관리 기준정보는 프로젝트 ADMIN 만 수정할 수 있습니다.");
		}
		issueTypeAccess.findByIdOptional(issueTypeId).orElseThrow(() -> new DocsException(NOT_FOUND));
		Set<String> existing = access.rules(projectId, issueTypeId).stream().map(DocRule::id).collect(Collectors.toSet());
		Set<String> names = new java.util.HashSet<>();
		List<DocRule> rules = new ArrayList<>();
		int i = 0;
		for (DocRuleDto d : body == null ? List.<DocRuleDto>of() : body) {
			String name = Docs.name(d.folderName(), 200, "/\\", "폴더 이름");
			if (!names.add(name)) {
				throw new DocsException(INVALID_REQUEST, "같은 폴더 이름이 두 번 있습니다: " + name);
			}
			String kind = blankToNull(d.defaultKind()) == null ? null : DocsEvolveService.kind(d.defaultKind());
			rules.add(new DocRule(existing.contains(d.id()) ? d.id() : null, issueTypeId, name,
				d.sortOrder() == null ? i : d.sortOrder(), kind, DocsEvolveService.stream(d.defaultStream()),
				blankToNull(d.templateContent()), Boolean.TRUE.equals(d.autoCreate())));
			i++;
		}
		access.replaceRules(projectId, issueTypeId, rules);
		return access.rules(projectId, issueTypeId).stream().map(DocRuleDto::of).toList();
	}

	// ── 내부 ──

	/** Team vault 의 task/{projectKey}/{issueKey} 에 메인 노트 + 자동 생성 슬롯 폴더, MAIN 연결 */
	private SnDocsNoteModel createMain(SnCiraIssueModel issue) {
		SnCiraProjectModel p = projectAccess.findById(issue.getProjectId());
		String vaultId = vaults.teamVault(p).getObjId();
		String[] path = taskPath(issue, p);
		String folderId = folders.ensurePath(vaultId, path);
		for (DocRule r : access.rules(p.getObjId(), issue.getIssueTypeId())) {
			if (r.autoCreate()) {
				folders.ensurePath(vaultId, append(path, r.folderName()));
			}
		}
		String title = mainTitle(issue.getIssueKey(), issue.getTitle());
		if (notes.titleTaken(vaultId, title)) { // 사람이 먼저 같은 제목을 만든 경우
			title = mainTitle(issue.getIssueKey(), issue.getTitle() + " " + issue.getObjId().substring(0, 8));
		}
		String fm = "---\nkind: task\nproject: " + q(p.getProjectKey()) + "\nissueKey: " + q(issue.getIssueKey())
			+ "\nissueType: " + q(issueTypeNm(issue.getIssueTypeId())) + "\nstatus: " + q(statusNm(issue.getStatusId()))
			+ "\n---\n";
		String body = (issue.getContent() == null || issue.getContent().isBlank() ? "" : issue.getContent().strip() + "\n")
			+ "\n" + PROGRESS + "\n- " + now() + " | 생성 (by " + userNm(issue.getReporterId()) + ")\n";
		SnDocsNoteModel n = notes.createIn(vaultId, folderId, title, fm + body);
		access.link(issue.getObjId(), n.getObjId(), CiraDocsAccess.MAIN, null);
		return n;
	}

	private void appendProgress(String issueId, String text) {
		access.mainNoteId(issueId).flatMap(noteAccess::findByIdOptional).ifPresent(n ->
			notes.writeInternal(n, n.getTitle(), withProgress(n.getContent(), "- " + now() + " | " + text)));
	}

	/** "## 진행 경과" 섹션 끝(다음 # 제목 앞, 빈 줄 제외)에 한 줄. 섹션이 없으면 본문 끝에 섹션을 만든다. 나머지 본문은 그대로 */
	static String withProgress(String content, String line) {
		String text = content == null ? "" : content;
		List<String> lines = new ArrayList<>(List.of(text.split("\\r?\\n", -1)));
		int start = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).strip().equals(PROGRESS)) {
				start = i;
				break;
			}
		}
		if (start < 0) {
			String base = text.stripTrailing();
			return (base.isEmpty() ? "" : base + "\n\n") + PROGRESS + "\n" + line + "\n";
		}
		int end = lines.size();
		for (int i = start + 1; i < lines.size(); i++) {
			if (lines.get(i).matches("^#{1,2} .*")) {
				end = i;
				break;
			}
		}
		int at = end;
		while (at > start + 1 && lines.get(at - 1).isBlank()) {
			at--;
		}
		lines.add(at, line);
		return String.join("\n", lines);
	}

	static String mainTitle(String issueKey, String issueTitle) {
		String t = (issueKey + " " + (issueTitle == null ? "" : issueTitle)).replaceAll("[\\[\\]|#\\r\\n]+", " ")
			.replaceAll(" {2,}", " ").strip();
		return t.length() > 500 ? t.substring(0, 500).strip() : t;
	}

	private String[] taskPath(SnCiraIssueModel issue) {
		return taskPath(issue, projectAccess.findById(issue.getProjectId()));
	}

	private static String[] taskPath(SnCiraIssueModel issue, SnCiraProjectModel p) {
		return new String[] {"task", p.getProjectKey(), issue.getIssueKey()};
	}

	private static String[] append(String[] path, String name) {
		String[] r = java.util.Arrays.copyOf(path, path.length + 1);
		r[path.length] = name;
		return r;
	}

	private SnCiraProjectMemberModel requireMember(String projectId) {
		GsUserModel me = userAccess.findByEmail(Docs.owner()).orElseThrow(() -> new DocsException(NOT_FOUND));
		return memberAccess.findByProjectIdAndUserId(projectId, me.getObjId())
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new DocsException(NOT_FOUND));
	}

	private SnCiraIssueModel requireIssue(String projectId, String issueId) {
		requireMember(projectId);
		return issueAccess.findByIdOptional(issueId)
			.filter(i -> projectId.equals(i.getProjectId()) && i.getDeletedAt() == null)
			.orElseThrow(() -> new DocsException(NOT_FOUND));
	}

	private String statusNm(String id) {
		return id == null ? "-" : statusAccess.findByIdOptional(id).map(s -> s.getStatusNm()).orElse(id);
	}

	private String issueTypeNm(String id) {
		return id == null ? "-" : issueTypeAccess.findByIdOptional(id).map(t -> t.getTypeNm()).orElse(id);
	}

	private String userNm(String userId) {
		return userId == null ? "-" : userAccess.findByIdOptional(userId).map(GsUserModel::getUserNm).orElse(userId);
	}

	private static String now() {
		return LocalDateTime.now(ZONE).format(TS);
	}

	/** YAML 큰따옴표 문자열 (이름에 : 등이 있어도 frontmatter 가 깨지지 않게) */
	private static String q(String v) {
		return "\"" + (v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
