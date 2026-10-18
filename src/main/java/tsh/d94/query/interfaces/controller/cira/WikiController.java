package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.WikiService;
import tsh.d94.app.server.apService.cira.dto.wiki.MovePageRequest;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageRequest;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageResponse;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageSummary;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageVersionResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WikiController {

	private final WikiService wikiService;

	@GetMapping("/projects/{projectId}/wiki")
	public ApiResponse<List<WikiPageSummary>> getWikiTree(@PathVariable String projectId) {
		return ApiResponse.ok(wikiService.getWikiTree(projectId));
	}

	@PostMapping("/projects/{projectId}/wiki")
	public ApiResponse<WikiPageResponse> createPage(
		@PathVariable String projectId,
		@Valid @RequestBody WikiPageRequest request
	) {
		return ApiResponse.ok(wikiService.createPage(projectId, request));
	}

	@GetMapping("/wiki/{pageId}")
	public ApiResponse<WikiPageResponse> getPage(@PathVariable String pageId) {
		return ApiResponse.ok(wikiService.getPage(pageId));
	}

	@PutMapping("/wiki/{pageId}")
	public ApiResponse<WikiPageResponse> updatePage(
		@PathVariable String pageId,
		@Valid @RequestBody WikiPageRequest request
	) {
		return ApiResponse.ok(wikiService.updatePage(pageId, request));
	}

	@DeleteMapping("/wiki/{pageId}")
	public ApiResponse<Void> deletePage(@PathVariable String pageId) {
		wikiService.deletePage(pageId);
		return ApiResponse.noContent();
	}

	@PutMapping("/wiki/{pageId}/move")
	public ApiResponse<Void> movePage(
		@PathVariable String pageId,
		@RequestBody MovePageRequest request
	) {
		wikiService.movePage(pageId, request.getNewParentId());
		return ApiResponse.noContent();
	}

	@GetMapping("/wiki/{pageId}/versions")
	public ApiResponse<List<WikiPageVersionResponse>> getVersions(@PathVariable String pageId) {
		return ApiResponse.ok(wikiService.getVersions(pageId));
	}
}
