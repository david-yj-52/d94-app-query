package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.SearchService;
import tsh.d94.app.server.apService.cira.dto.AutocompleteResponse;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.SavedFilterRequest;
import tsh.d94.app.server.apService.cira.dto.SavedFilterResponse;
import tsh.d94.app.server.apService.cira.dto.SearchIssueRequest;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;

	@GetMapping("/search/issues")
	public ApiResponse<Page<IssueResponse>> searchIssues(
		SearchIssueRequest request,
		Pageable pageable
	) {
		return ApiResponse.ok(searchService.searchIssues(request, pageable));
	}

	@PostMapping("/saved-filters")
	public ApiResponse<SavedFilterResponse> saveFilter(
		@Valid @RequestBody SavedFilterRequest request
	) {
		return ApiResponse.ok(searchService.saveFilter(request));
	}

	@GetMapping("/saved-filters")
	public ApiResponse<List<SavedFilterResponse>> getSavedFilters() {
		return ApiResponse.ok(searchService.getSavedFilters());
	}

	@DeleteMapping("/saved-filters/{id}")
	public ApiResponse<Void> deleteFilter(@PathVariable String id) {
		searchService.deleteFilter(id);
		return ApiResponse.noContent();
	}

	@GetMapping("/autocomplete")
	public ApiResponse<List<AutocompleteResponse>> autocomplete(
		@RequestParam String type,
		@RequestParam(required = false, defaultValue = "") String keyword
	) {
		return ApiResponse.ok(searchService.autocomplete(type, keyword));
	}

}
