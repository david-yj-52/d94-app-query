package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.CiraFieldService;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Catalog;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Field;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.FieldKeys;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.ImportField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.LibraryItem;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.ProjectType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveCustomField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.TypeField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Usage;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/** 기준정보 프로젝트 타입·필드(쓰기 ADMIN) + 프로젝트 필드·사람별 컬럼(설계 5.4) */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CiraFieldController {

	private final CiraFieldService service;

	@GetMapping("/cira/field-catalog")
	public ApiResponse<Catalog> catalog() {
		return ApiResponse.ok(service.catalog());
	}

	@GetMapping("/cira/project-types")
	public ApiResponse<List<ProjectType>> types() {
		return ApiResponse.ok(service.types());
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/cira/project-types")
	public ApiResponse<ProjectType> createType(@RequestBody SaveType req) {
		return ApiResponse.ok(service.createType(req));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/cira/project-types/{id}")
	public ApiResponse<ProjectType> updateType(@PathVariable String id, @RequestBody SaveType req) {
		return ApiResponse.ok(service.updateType(id, req));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@DeleteMapping("/cira/project-types/{id}")
	public ApiResponse<Void> deleteType(@PathVariable String id) {
		service.deleteType(id);
		return ApiResponse.noContent();
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/cira/project-types/{id}/fields")
	public ApiResponse<ProjectType> replaceTypeFields(@PathVariable String id, @RequestBody List<TypeField> fields) {
		return ApiResponse.ok(service.replaceTypeFields(id, fields));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/cira/project-types/{id}/custom-fields")
	public ApiResponse<Field> createTypeField(@PathVariable String id, @RequestBody SaveCustomField req) {
		return ApiResponse.ok(service.createTypeField(id, req));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/cira/project-types/{id}/custom-fields/{cfId}")
	public ApiResponse<Field> updateTypeField(@PathVariable String id, @PathVariable String cfId, @RequestBody SaveCustomField req) {
		return ApiResponse.ok(service.updateTypeField(id, cfId, req));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@DeleteMapping("/cira/project-types/{id}/custom-fields/{cfId}")
	public ApiResponse<Void> deleteTypeField(@PathVariable String id, @PathVariable String cfId) {
		service.deleteTypeField(id, cfId);
		return ApiResponse.noContent();
	}

	/** 전역 필드를 타입 필드로 복사 */
	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/cira/project-types/{id}/custom-fields/import")
	public ApiResponse<Field> importToType(@PathVariable String id, @RequestBody ImportField req) {
		return ApiResponse.ok(service.importToType(id, req.sourceFieldId()));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/cira/custom-fields")
	public ApiResponse<Field> createGlobalField(@RequestBody SaveCustomField req) {
		return ApiResponse.ok(service.createGlobalField(req));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/cira/custom-fields/{id}")
	public ApiResponse<Field> updateGlobalField(@PathVariable String id, @RequestBody SaveCustomField req) {
		return ApiResponse.ok(service.updateGlobalField(id, req));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@DeleteMapping("/cira/custom-fields/{id}")
	public ApiResponse<Void> deleteGlobalField(@PathVariable String id) {
		service.deleteGlobalField(id);
		return ApiResponse.noContent();
	}

	@GetMapping("/cira/custom-fields/{id}/usage")
	public ApiResponse<Usage> usage(@PathVariable String id) {
		return ApiResponse.ok(service.usage(id));
	}

	@GetMapping("/projects/{projectId}/fields")
	public ApiResponse<List<Field>> projectFields(@PathVariable String projectId) {
		return ApiResponse.ok(service.projectFields(projectId));
	}

	@PutMapping("/projects/{projectId}/fields")
	public ApiResponse<List<Field>> replaceProjectFields(@PathVariable String projectId, @RequestBody FieldKeys req) {
		return ApiResponse.ok(service.replaceProjectFields(projectId, req.fieldKeys() == null ? List.of() : req.fieldKeys()));
	}

	/** 가져올 수 있는 전역·타입 필드 (프로젝트 멤버) */
	@GetMapping("/projects/{projectId}/field-library")
	public ApiResponse<List<LibraryItem>> library(@PathVariable String projectId) {
		return ApiResponse.ok(service.library(projectId));
	}

	/** 라이브러리 필드를 프로젝트 필드로 복사 (프로젝트 멤버) */
	@PostMapping("/projects/{projectId}/custom-fields/import")
	public ApiResponse<Field> importToProject(@PathVariable String projectId, @RequestBody ImportField req) {
		return ApiResponse.ok(service.importToProject(projectId, req.sourceFieldId()));
	}

	@PostMapping("/projects/{projectId}/custom-fields")
	public ApiResponse<Field> createProjectField(@PathVariable String projectId, @RequestBody SaveCustomField req) {
		return ApiResponse.ok(service.createProjectField(projectId, req));
	}

	@PutMapping("/projects/{projectId}/custom-fields/{id}")
	public ApiResponse<Field> updateProjectField(@PathVariable String projectId, @PathVariable String id,
		@RequestBody SaveCustomField req) {
		return ApiResponse.ok(service.updateProjectField(projectId, id, req));
	}

	@DeleteMapping("/projects/{projectId}/custom-fields/{id}")
	public ApiResponse<Void> deleteProjectField(@PathVariable String projectId, @PathVariable String id) {
		service.deleteProjectField(projectId, id);
		return ApiResponse.noContent();
	}

	@GetMapping("/projects/{projectId}/column-pref")
	public ApiResponse<FieldKeys> columnPref(@PathVariable String projectId) {
		return ApiResponse.ok(service.columnPref(projectId));
	}

	@PutMapping("/projects/{projectId}/column-pref")
	public ApiResponse<FieldKeys> saveColumnPref(@PathVariable String projectId, @RequestBody FieldKeys req) {
		return ApiResponse.ok(service.saveColumnPref(projectId, req.fieldKeys()));
	}

	/** [기본값으로] — 타입 기본 표시로 되돌린다 */
	@DeleteMapping("/projects/{projectId}/column-pref")
	public ApiResponse<FieldKeys> resetColumnPref(@PathVariable String projectId) {
		return ApiResponse.ok(service.resetColumnPref(projectId));
	}
}
