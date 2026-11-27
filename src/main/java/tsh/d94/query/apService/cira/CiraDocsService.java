package tsh.d94.app.server.apService.cira;

import static tsh.d94.app.server.apService.docs.DocsException.Code.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.automation.events.IssueAssignedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueContentChangedEvent;
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
	private final SpaceGuard spaceGuard;

	/**
	 * promote 중 같은 스레드의 IssueCreatedEvent 에 "이 노트를 MAIN 으로" 를 넘긴다.
	 * ponytail: 이벤트가 동기·같은 스레드라 ThreadLocal 로 충분. 이벤트를 비동기로 바꾸면 이벤트 필드로 옮길 것.
	 */
	private static final ThreadLocal<String> PROMOTED_NOTE = new ThreadLocal<>();

	/** scope: GLOBAL(전사 기준정보) | PROJECT(프로젝트 공통) | SPACE. locked = 기준정보에서 가져온 규칙(삭제·수정 불가) */
	public record DocRuleDto(String id, String issueTypeId, String folderName, Integer sortOrder, String defaultKind,
		String defaultStream, String templateContent, Boolean autoCreate, String scope, String spaceId,
		String sourceRuleId, Boolean locked) {
		static DocRuleDto of(DocRule r, String scope) {
			return new DocRuleDto(r.id(), r.issueTypeId(), r.folderName(), r.sortOrder(), r.defaultKind(),
				r.defaultStream(), r.templateContent(), r.autoCreate(), scope, r.spaceId(), r.sourceRuleId(), r.locked());
		}

		static DocRuleDto of(DocRule r) {
			return of(r, r.spaceId() != null ? "SPACE" : "PROJECT");
		}

		/** 저장 요청용 (스코프·잠금은 서버가 정한다) */
		public DocRuleDto(String id, String issueTypeId, String folderName, Integer sortOrder, String defaultKind,
			String defaultStream, String templateContent, Boolean autoCreate) {
			this(id, issueTypeId, folderName, sortOrder, defaultKind, defaultStream, templateContent, autoCreate, null, null,
				null, null);
		}
	}

	/** ruleIds 가 비면 전사 규칙 전부 */
	public record ImportRulesRequest(List<String> ruleIds) {
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

	public record PromoteRequest(String issueTypeId, String title, String spaceId, String featureId) {
		public PromoteRequest(String issueTypeId, String title) {
			this(issueTypeId, title, null, null);
		}
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
			// frontmatter status 도 같은 저장(버전 1건)에서 갱신 — RAG·목록이 옛 상태를 보지 않게
			appendProgress(e.getIssueId(), "상태 변경: " + statusNm(e.getFromStatus()) + " → " + statusNm(e.getToStatus())
				+ " (by " + userNm(e.getChangedBy()) + ")",
				c -> DocsMarkdownIndexer.setFrontmatter(c, "status", q(statusNm(e.getToStatus()))));
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

	/** Task 설명 수정 → 메인 노트 본문(frontmatter·진행 경과 사이)만 바꾼다. 메인 노트가 없으면 만든다 */
	@EventListener
	public void onContentChanged(IssueContentChangedEvent e) {
		if (!(e.getSource() instanceof IssueService)) {
			return;
		}
		SnCiraIssueModel issue = issueAccess.findById(e.getIssueId());
		String mainId = access.mainNoteId(issue.getObjId()).orElseGet(() -> createMain(issue).getObjId());
		SnDocsNoteModel n = noteAccess.findById(mainId);
		String next = withDescription(n.getContent(), e.getContent());
		if (!next.equals(n.getContent())) {
			notes.writeInternal(n, n.getTitle(), next);
		}
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
		List<DocRuleDto> rules = access.effectiveRules(projectId, issue.getSpaceId(), issue.getIssueTypeId()).stream()
			.map(DocRuleDto::of).toList();
		return new IssueDocsResponse(main, derived, lineage, rules);
	}

	/**
	 * GET /issues/{id}: 설명 = 메인 노트 본문(frontmatter·진행 경과 제외), mainNoteId 를 채운다.
	 * 메인 노트 없이 옛 CONTENT 만 있는 Task 는 이 시점에 노트로 옮긴다(온디맨드 이관). 멤버가 아니면 응답 그대로.
	 */
	@Transactional
	public IssueResponse attachMain(IssueResponse r) {
		if (!isMember(r.getProjectId())) {
			return r;
		}
		SnCiraIssueModel issue = issueAccess.findById(r.getId());
		String mainId = access.mainNoteId(issue.getObjId())
			.orElseGet(() -> issue.getContent() == null || issue.getContent().isBlank() ? null : createMain(issue).getObjId());
		if (mainId != null) {
			noteAccess.findByIdOptional(mainId).ifPresent(n -> {
				r.setMainNoteId(n.getObjId());
				r.setContent(description(n.getContent()));
			});
		}
		return r;
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
			DocRule rule = access.rule(projectId, ruleId).filter(r -> r.issueTypeId().equals(issue.getIssueTypeId())
					&& (r.spaceId() == null || r.spaceId().equals(issue.getSpaceId())))
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
		// 프로젝트 Team vault 노트면 그 노트 자체가 Task 의 MAIN(설명)이 된다 (V650 단일 원본)
		boolean sameVault = src.getVaultId().equals(vaults.teamVault(projectAccess.findById(projectId)).getObjId());
		IssueResponse issue;
		if (sameVault) {
			PROMOTED_NOTE.set(src.getObjId());
		}
		try {
			issue = issueService.createIssue(projectId, CreateIssueRequest.builder()
				.title(title).issueTypeId(blankToNull(req.issueTypeId())).spaceId(blankToNull(req.spaceId()))
				.featureId(blankToNull(req.featureId())).build());
		} finally {
			PROMOTED_NOTE.remove();
		}
		if (sameVault) {
			return issue;
		}
		access.mainNoteId(issue.getId()).ifPresent(mainId -> {
			SnDocsNoteModel main = noteAccess.findById(mainId);
			notes.writeInternal(main, main.getTitle(),
				DocsMarkdownIndexer.setFrontmatter(main.getContent(), "origin", "\"[[" + src.getTitle() + "]]\""));
		});
		return issue;
	}

	// ── 기준정보 (V650: 전사 · 프로젝트 공통 · 공간 3단계) ──

	/** 프로젝트 공통 규칙 (기준정보에서 가져온 잠금 규칙 포함) */
	public List<DocRuleDto> rules(String projectId) {
		requireMember(projectId);
		return access.rules(projectId).stream().map(DocRuleDto::of).toList();
	}

	/** 그 Task 타입의 프로젝트 공통 슬롯(잠기지 않은 것)을 통째로 저장. 프로젝트 ADMIN */
	@Transactional
	public List<DocRuleDto> saveRules(String projectId, String issueTypeId, List<DocRuleDto> body) {
		if (!"ADMIN".equals(requireMember(projectId).getRole())) {
			throw new DocsException(FORBIDDEN, "문서 관리 기준정보는 프로젝트 ADMIN 만 수정할 수 있습니다.");
		}
		saveScope(projectId, null, issueTypeId, body, access.rules(projectId, issueTypeId));
		return access.rules(projectId, issueTypeId).stream().map(DocRuleDto::of).toList();
	}

	/** 기준정보 → 프로젝트 공통 잠금 복사 (프로젝트 ADMIN). 이미 가져온 것·같은 폴더 이름은 건너뛴다 */
	@Transactional
	public List<DocRuleDto> importRules(String projectId, ImportRulesRequest req) {
		if (!"ADMIN".equals(requireMember(projectId).getRole())) {
			throw new DocsException(FORBIDDEN, "기준정보 산출물 규칙은 프로젝트 ADMIN 만 가져올 수 있습니다.");
		}
		List<String> ids = req == null || req.ruleIds() == null || req.ruleIds().isEmpty()
			? access.globalRules().stream().map(DocRule::id).toList() : req.ruleIds();
		access.importGlobal(projectId, ids);
		return rules(projectId);
	}

	/** 공간 규칙 + 그 공간에 적용되는 프로젝트 공통 규칙(잠금 포함, 읽기 전용 표시용) */
	public List<DocRuleDto> spaceRules(String spaceId) {
		var space = spaceGuard.get(spaceId);
		requireMember(space.getProjectId());
		List<DocRuleDto> out = new ArrayList<>(access.rules(space.getProjectId()).stream().map(DocRuleDto::of).toList());
		access.spaceRules(spaceId).stream().map(DocRuleDto::of).forEach(out::add);
		return out;
	}

	/** 그 Task 타입의 공간 슬롯을 통째로 저장. 프로젝트 ADMIN 또는 공간 관리자. 공통 규칙과 같은 폴더 이름은 거절 */
	@Transactional
	public List<DocRuleDto> saveSpaceRules(String spaceId, String issueTypeId, List<DocRuleDto> body) {
		var space = spaceGuard.get(spaceId);
		requireMember(space.getProjectId());
		if (!spaceGuard.canManage(space)) {
			throw new DocsException(FORBIDDEN, "공간 산출물 규칙은 프로젝트 ADMIN 이나 공간 관리자만 수정할 수 있습니다.");
		}
		List<DocRule> current = access.effectiveRules(space.getProjectId(), spaceId, issueTypeId);
		saveScope(space.getProjectId(), spaceId, issueTypeId, body, current);
		return spaceRules(spaceId);
	}

	public List<DocRuleDto> globalRules() {
		return access.globalRules().stream().map(r -> DocRuleDto.of(r, "GLOBAL")).toList();
	}

	/** 전사 기준정보 슬롯 저장 (총괄 ADMIN — 컨트롤러 @PreAuthorize). 이미 프로젝트로 가져간 복사본은 그대로 */
	@Transactional
	public List<DocRuleDto> saveGlobalRules(String issueTypeId, List<DocRuleDto> body) {
		saveScope(null, null, issueTypeId, body,
			access.globalRules().stream().filter(r -> r.issueTypeId().equals(issueTypeId)).toList());
		return globalRules();
	}

	/**
	 * 한 스코프 저장 공통. current = 그 스코프에서 보이는 규칙(잠금·상위 스코프 포함) — id 유지와 이름 충돌 검사에 쓴다.
	 * 잠긴 규칙·상위 스코프 규칙은 body 에 와도 무시(무력화 불가), 같은 폴더 이름으로 덮을 수도 없다.
	 */
	private void saveScope(String projectId, String spaceId, String issueTypeId, List<DocRuleDto> body, List<DocRule> current) {
		issueTypeAccess.findByIdOptional(issueTypeId).orElseThrow(() -> new DocsException(NOT_FOUND));
		Set<String> mine = current.stream().filter(r -> !r.locked() && java.util.Objects.equals(r.spaceId(), spaceId))
			.map(DocRule::id).collect(Collectors.toSet());
		Set<String> reserved = current.stream().filter(r -> !mine.contains(r.id())).map(DocRule::folderName)
			.collect(Collectors.toSet());
		Set<String> fixedIds = current.stream().filter(r -> !mine.contains(r.id())).map(DocRule::id).collect(Collectors.toSet());
		Set<String> names = new java.util.HashSet<>();
		List<DocRule> rules = new ArrayList<>();
		int i = 0;
		for (DocRuleDto d : body == null ? List.<DocRuleDto>of() : body) {
			if (d.id() != null && fixedIds.contains(d.id())) {
				continue; // 잠금·상위 규칙은 여기서 못 바꾼다
			}
			String name = Docs.name(d.folderName(), 200, "/\\", "폴더 이름");
			if (!names.add(name)) {
				throw new DocsException(INVALID_REQUEST, "같은 폴더 이름이 두 번 있습니다: " + name);
			}
			if (reserved.contains(name)) {
				throw new DocsException(INVALID_REQUEST, "공통 산출물 규칙과 같은 폴더 이름입니다: " + name);
			}
			String kind = blankToNull(d.defaultKind()) == null ? null : DocsEvolveService.kind(d.defaultKind());
			rules.add(new DocRule(mine.contains(d.id()) ? d.id() : null, issueTypeId, name,
				d.sortOrder() == null ? i : d.sortOrder(), kind, DocsEvolveService.stream(d.defaultStream()),
				blankToNull(d.templateContent()), Boolean.TRUE.equals(d.autoCreate())));
			i++;
		}
		access.replaceRules(projectId, spaceId, issueTypeId, rules);
	}

	// ── 내부 ──

	/** Team vault 의 task/{projectKey}/{issueKey} 에 메인 노트 + 자동 생성 슬롯 폴더, MAIN 연결 */
	private SnDocsNoteModel createMain(SnCiraIssueModel issue) {
		SnCiraProjectModel p = projectAccess.findById(issue.getProjectId());
		String vaultId = vaults.teamVault(p).getObjId();
		String[] path = taskPath(issue, p);
		String folderId = folders.ensurePath(vaultId, path);
		for (DocRule r : access.effectiveRules(p.getObjId(), issue.getSpaceId(), issue.getIssueTypeId())) {
			if (r.autoCreate()) {
				folders.ensurePath(vaultId, append(path, r.folderName()));
			}
		}
		String promoted = PROMOTED_NOTE.get();
		if (promoted != null) { // Docs 승격: 원본 노트가 그대로 MAIN (frontmatter 만 Task 표시로)
			SnDocsNoteModel n = noteAccess.findById(promoted);
			String c = n.getContent();
			c = DocsMarkdownIndexer.setFrontmatter(c, "kind", "task");
			c = DocsMarkdownIndexer.setFrontmatter(c, "project", q(p.getProjectKey()));
			c = DocsMarkdownIndexer.setFrontmatter(c, "issueKey", q(issue.getIssueKey()));
			c = DocsMarkdownIndexer.setFrontmatter(c, "status", q(statusNm(issue.getStatusId())));
			notes.writeInternal(n, n.getTitle(), withProgress(c, "- " + now() + " | CIRA Task 로 등록 (by "
				+ userNm(issue.getReporterId()) + ")"));
			access.link(issue.getObjId(), n.getObjId(), CiraDocsAccess.MAIN, null);
			return n;
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
		if (issue.getContent() != null) { // 설명은 이제 노트에만 (레거시 CONTENT 이관 포함)
			issue.setContent(null);
			issueAccess.save(issue);
		}
		return n;
	}

	private void appendProgress(String issueId, String text) {
		appendProgress(issueId, text, UnaryOperator.identity());
	}

	private void appendProgress(String issueId, String text, UnaryOperator<String> edit) {
		access.mainNoteId(issueId).flatMap(noteAccess::findByIdOptional).ifPresent(n ->
			notes.writeInternal(n, n.getTitle(), withProgress(edit.apply(n.getContent()), "- " + now() + " | " + text)));
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

	/** frontmatter 를 뺀 본문 */
	public static String stripFrontmatter(String content) {
		String c = content == null ? "" : content;
		if (c.startsWith("---\n")) {
			int end = c.indexOf("\n---", 4);
			if (end >= 0) {
				int nl = c.indexOf('\n', end + 4);
				return nl < 0 ? "" : c.substring(nl + 1);
			}
		}
		return c;
	}

	/** Task 설명 = 메인 노트에서 frontmatter 와 "## 진행 경과" 섹션(과 그 뒤)을 뺀 부분. withDescription 의 짝 */
	public static String description(String content) {
		String body = stripFrontmatter(content);
		int at = body.indexOf(PROGRESS);
		return (at < 0 ? body : body.substring(0, at)).strip();
	}

	/**
	 * 설명 교체: frontmatter 는 그대로, "## 진행 경과" 섹션(과 그 뒤)은 그대로, 그 사이만 description 으로.
	 * description 에 진행 경과 섹션이 들어 있으면 그 앞까지만 쓴다(기록이 둘로 갈라지지 않게).
	 */
	static String withDescription(String content, String description) {
		String c = content == null ? "" : content;
		String fm = c.substring(0, c.length() - stripFrontmatter(c).length());
		String body = c.substring(fm.length());
		int at = body.indexOf(PROGRESS);
		String tail = at < 0 ? "" : body.substring(at);
		String d = description == null ? "" : description;
		int dAt = d.indexOf(PROGRESS);
		d = (dAt < 0 ? d : d.substring(0, dAt)).strip();
		return fm + (d.isEmpty() ? "" : d + "\n") + (tail.isEmpty() ? "" : "\n" + tail);
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

	private boolean isMember(String projectId) {
		return userAccess.findByEmail(Docs.owner())
			.flatMap(me -> memberAccess.findByProjectIdAndUserId(projectId, me.getObjId()))
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.isPresent();
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
