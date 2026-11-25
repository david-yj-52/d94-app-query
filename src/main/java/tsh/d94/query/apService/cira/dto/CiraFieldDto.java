package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

/** 프로젝트 타입·필드·사람별 컬럼 API 의 요청/응답 (설계 5.4) */
public final class CiraFieldDto {

	private CiraFieldDto() {}

	/** source: fixed | type | project (카탈로그 응답에서는 null) */
	public record Field(String fieldKey, String name, String kind, String fieldType, List<String> options, boolean required,
		boolean sortable, String source, boolean defaultVisible, String projectId) {}

	public record Catalog(List<Field> system, List<Field> custom) {}

	public record TypeField(String fieldKey, boolean defaultVisible) {}

	public record ProjectType(String id, String typeNm, String descr, int sortOrd, int projectCount, List<TypeField> fields) {}

	public record SaveType(String typeNm, String descr) {}

	public record SaveCustomField(String fieldNm, String fieldType, Boolean required, List<String> options) {}

	public record Usage(int projects, int issues) {}

	public record FieldKeys(List<String> fieldKeys) {}
}
