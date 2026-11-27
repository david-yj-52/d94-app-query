package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

/** 프로젝트 타입·필드·사람별 컬럼 API 의 요청/응답 (설계 5.4) */
public final class CiraFieldDto {

	private CiraFieldDto() {}

	/**
	 * source: fixed | type | project (유효 필드에서만, 카탈로그는 null). scope: global | type | project (사용자 정의 필드의 소속, sys 는 null).
	 * sourceFieldId·sourceName: 복사해 온 원본(원본이 지워졌으면 sourceName null). canEdit: 현재 사용자가 이 프로젝트 필드를 고칠 수 있는지.
	 */
	public record Field(String fieldKey, String name, String kind, String fieldType, List<String> options, boolean required,
		boolean sortable, String source, boolean defaultVisible, String projectId, String scope, String sourceFieldId,
		String sourceName, String createdBy, boolean canEdit) {}

	/** typeFields: 타입 id → 그 타입의 타입 필드. copies: 전역 필드 id → 복사본 수(타입·프로젝트 합계) */
	public record Catalog(List<Field> system, List<Field> custom, java.util.Map<String, List<Field>> typeFields,
		java.util.Map<String, Integer> copies) {}

	public record TypeField(String fieldKey, boolean defaultVisible) {}

	public record ProjectType(String id, String typeNm, String descr, int sortOrd, int spaceCount, List<TypeField> fields) {}

	public record SaveType(String typeNm, String descr) {}

	/** copyToTypeIds: 전역 필드를 만들 때만 — 그 타입들로 바로 복사 */
	public record SaveCustomField(String fieldNm, String fieldType, Boolean required, List<String> options,
		List<String> copyToTypeIds) {}

	public record ImportField(String sourceFieldId) {}

	/** importedAs: 이 프로젝트에 이미 가져온 복사본 fieldKey, 없으면 null */
	public record LibraryItem(Field field, String importedAs) {}

	public record Usage(int copies, int projects, int issues) {}

	public record FieldKeys(List<String> fieldKeys) {}
}
