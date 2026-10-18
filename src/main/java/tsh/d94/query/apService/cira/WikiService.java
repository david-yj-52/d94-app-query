package tsh.d94.app.server.apService.cira;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageRequest;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageResponse;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageSummary;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageVersionResponse;
import tsh.d94.app.server.data.orm.cira.ciraWikiPage.SnCiraWikiPageAccess;
import tsh.d94.app.server.data.orm.cira.ciraWikiPage.SnCiraWikiPageModel;
import tsh.d94.app.server.data.orm.cira.ciraWikiPageVer.SnCiraWikiPageVerAccess;
import tsh.d94.app.server.data.orm.cira.ciraWikiPageVer.SnCiraWikiPageVerModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiService {

	private static final Pattern ISSUE_LINK_PATTERN = Pattern.compile("\\[\\[(CIRA-\\d+)\\]\\]");

	private final SnCiraWikiPageAccess wikiPageAccess;
	private final SnCiraWikiPageVerAccess wikiPageVerAccess;
	private final GsUserAccess userAccess;

	/**
	 * 프로젝트의 전체 위키 페이지 목록을 조회하여 트리 구성용 요약 리스트로 반환합니다.
	 * 각 요소에 parentId가 포함되어 있어 클라이언트에서 트리 조립이 가능합니다.
	 */
	public List<WikiPageSummary> getWikiTree(String projectId) {
		return wikiPageAccess.findByProjectId(projectId).stream()
			.map(this::mapToSummary)
			.collect(Collectors.toList());
	}

	/**
	 * 단일 위키 페이지를 조회합니다.
	 */
	public WikiPageResponse getPage(String pageId) {
		SnCiraWikiPageModel page = wikiPageAccess.findById(pageId);
		if (UseStatCd.Delete.equals(page.getUseStatCd())) {
			throw new EntityNotFoundException("Wiki page not found: " + pageId);
		}
		return mapToResponse(page);
	}

	/**
	 * 위키 페이지를 생성합니다.
	 * content 내 [[CIRA-\d+]] 패턴을 HTML 링크로 치환하여 contentHtml을 생성합니다.
	 */
	@Transactional
	public WikiPageResponse createPage(String projectId, WikiPageRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel author = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		String contentHtml = renderContentHtml(request.getContent());

		SnCiraWikiPageModel page = SnCiraWikiPageModel.builder()
			.projectId(projectId)
			.parentId(request.getParentId())
			.title(request.getTitle())
			.content(request.getContent())
			.contentHtml(contentHtml)
			.authorId(author.getObjId())
			.sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
			.version(1)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("CREATE-WIKI-PAGE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateWikiPage")
			.prevEvntNm("None")
			.build();

		wikiPageAccess.save(page);
		return mapToResponse(page);
	}

	/**
	 * 위키 페이지를 수정합니다.
	 * 수정 전 현재 버전을 SnCiraWikiPageVerModel에 저장한 뒤 페이지를 갱신합니다.
	 */
	@Transactional
	public WikiPageResponse updatePage(String pageId, WikiPageRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel editor = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraWikiPageModel page = wikiPageAccess.findById(pageId);
		if (UseStatCd.Delete.equals(page.getUseStatCd())) {
			throw new EntityNotFoundException("Wiki page not found: " + pageId);
		}

		// 현재 버전을 이력으로 저장
		SnCiraWikiPageVerModel versionSnapshot = SnCiraWikiPageVerModel.builder()
			.pageId(pageId)
			.version(page.getVersion())
			.content(page.getContent())
			.editedBy(editor.getObjId())
			.editedAt(OffsetDateTime.now())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("UPDATE-WIKI-PAGE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("SaveWikiPageVersion")
			.prevEvntNm("None")
			.build();
		wikiPageVerAccess.save(versionSnapshot);

		// 페이지 내용 갱신
		String contentHtml = renderContentHtml(request.getContent());
		page.setTitle(request.getTitle());
		page.setContent(request.getContent());
		page.setContentHtml(contentHtml);
		if (request.getSortOrder() != null) {
			page.setSortOrder(request.getSortOrder());
		}
		page.setVersion(page.getVersion() + 1);
		wikiPageAccess.save(page);

		return mapToResponse(page);
	}

	/**
	 * 위키 페이지를 soft delete 처리합니다.
	 * 하위 페이지도 재귀적으로 삭제됩니다.
	 */
	@Transactional
	public void deletePage(String pageId) {
		SnCiraWikiPageModel page = wikiPageAccess.findById(pageId);
		if (UseStatCd.Delete.equals(page.getUseStatCd())) {
			throw new EntityNotFoundException("Wiki page not found: " + pageId);
		}
		deletePageRecursive(page);
	}

	private void deletePageRecursive(SnCiraWikiPageModel page) {
		List<SnCiraWikiPageModel> children = wikiPageAccess.findByParentId(page.getObjId());
		for (SnCiraWikiPageModel child : children) {
			deletePageRecursive(child);
		}
		page.setUseStatCd(UseStatCd.Delete);
		wikiPageAccess.save(page);
	}

	/**
	 * 위키 페이지의 부모를 변경합니다 (트리 이동).
	 * newParentId가 null이면 루트 페이지로 이동됩니다.
	 */
	@Transactional
	public void movePage(String pageId, String newParentId) {
		SnCiraWikiPageModel page = wikiPageAccess.findById(pageId);
		if (UseStatCd.Delete.equals(page.getUseStatCd())) {
			throw new EntityNotFoundException("Wiki page not found: " + pageId);
		}
		page.setParentId(newParentId);
		wikiPageAccess.save(page);
	}

	/**
	 * 위키 페이지의 버전 이력을 조회합니다.
	 */
	public List<WikiPageVersionResponse> getVersions(String pageId) {
		SnCiraWikiPageModel page = wikiPageAccess.findById(pageId);
		if (UseStatCd.Delete.equals(page.getUseStatCd())) {
			throw new EntityNotFoundException("Wiki page not found: " + pageId);
		}
		return wikiPageVerAccess.findByPageId(pageId).stream()
			.map(this::mapToVersionResponse)
			.collect(Collectors.toList());
	}

	/**
	 * content 내 [[CIRA-숫자]] 패턴을 HTML 앵커 태그로 치환합니다.
	 * 예) [[CIRA-42]] → <a href="/issues/CIRA-42">CIRA-42</a>
	 */
	private String renderContentHtml(String content) {
		if (content == null) {
			return null;
		}
		Matcher matcher = ISSUE_LINK_PATTERN.matcher(content);
		return matcher.replaceAll(m -> {
			String issueKey = m.group(1);
			return "<a href=\"/issues/" + issueKey + "\">" + issueKey + "</a>";
		});
	}

	private WikiPageSummary mapToSummary(SnCiraWikiPageModel page) {
		return WikiPageSummary.builder()
			.id(page.getObjId())
			.title(page.getTitle())
			.parentId(page.getParentId())
			.sortOrder(page.getSortOrder())
			.version(page.getVersion())
			.build();
	}

	private WikiPageResponse mapToResponse(SnCiraWikiPageModel page) {
		return WikiPageResponse.builder()
			.id(page.getObjId())
			.title(page.getTitle())
			.content(page.getContent())
			.contentHtml(page.getContentHtml())
			.parentId(page.getParentId())
			.authorId(page.getAuthorId())
			.sortOrder(page.getSortOrder())
			.version(page.getVersion())
			.build();
	}

	private WikiPageVersionResponse mapToVersionResponse(SnCiraWikiPageVerModel ver) {
		return WikiPageVersionResponse.builder()
			.id(ver.getObjId())
			.version(ver.getVersion())
			.editedBy(ver.getEditedBy())
			.editedAt(ver.getEditedAt())
			.build();
	}
}
