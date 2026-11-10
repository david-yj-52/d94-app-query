package tsh.d94.app.server.apService.study.parser;

import java.util.List;

/** d94-app-pdf `POST /pdf/api/v1/parse` 응답. 파서 `to_response()`와 짝이다. */
public record ParserResponse(String parserVersion, String title, int pageCount, List<Section> sections,
	List<Block> blocks, List<Figure> figures) {

	public record Section(String key, String parentKey, int level, String title, int page, String anchorBlockKey) {
	}

	public record Block(String key, String sectionKey, String parentKey, String type, Integer level, String text,
		int page, Integer row, Integer col) {
	}

	public record Figure(String blockKey, String pngBase64) {
	}
}
