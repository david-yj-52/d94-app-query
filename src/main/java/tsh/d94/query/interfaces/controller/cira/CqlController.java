package tsh.d94.app.server.interfaces.controller.cira;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.CqlSearchService;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.cql.CqlAutocompleteResponse;
import tsh.d94.app.server.apService.cira.dto.cql.CqlSearchRequest;
import tsh.d94.app.server.apService.cira.dto.cql.CqlValidateRequest;
import tsh.d94.app.server.apService.cira.dto.cql.CqlValidateResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/search/cql")
@RequiredArgsConstructor
public class CqlController {

	private final CqlSearchService cqlSearchService;

	/**
	 * CQL 검색
	 * POST /api/v1/search/cql
	 */
	@PostMapping
	public ApiResponse<Page<IssueResponse>> searchByCql(
		@Valid @RequestBody CqlSearchRequest request
	) {
		Page<IssueResponse> result = cqlSearchService.search(
			request.getCql(),
			PageRequest.of(request.getPage(), request.getSize())
		);
		return ApiResponse.ok(result);
	}

	/**
	 * CQL 유효성 검증
	 * POST /api/v1/search/cql/validate
	 */
	@PostMapping("/validate")
	public ApiResponse<CqlValidateResponse> validate(
		@Valid @RequestBody CqlValidateRequest request
	) {
		return ApiResponse.ok(cqlSearchService.validate(request.getCql()));
	}

	/**
	 * CQL 자동완성
	 * GET /api/v1/search/cql/autocomplete?cql=&cursor=
	 */
	@GetMapping("/autocomplete")
	public ApiResponse<CqlAutocompleteResponse> autocomplete(
		@RequestParam(required = false, defaultValue = "") String cql,
		@RequestParam(required = false, defaultValue = "-1") int cursor
	) {
		return ApiResponse.ok(cqlSearchService.autocomplete(cql, cursor));
	}
}
