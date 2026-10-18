package tsh.d94.app.server.apService.cira.dto.cql;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CqlAutocompleteResponse {

	private List<SuggestionItem> suggestions;

	@Data
	@Builder
	public static class SuggestionItem {
		/** 제안 텍스트 (삽입할 문자열) */
		private String text;
		/** 제안 종류: FIELD / OPERATOR / VALUE / KEYWORD */
		private String kind;
		/** UI 표시용 설명 */
		private String description;
	}
}
