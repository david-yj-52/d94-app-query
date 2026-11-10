package tsh.d94.app.server.apService.study.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;

/** study API 요청·응답. 필드명은 웹 `src/types/study.ts`와 짝이다. ponytail: 파일 하나에 모음. */
public final class StudyDtos {

	private StudyDtos() {
	}

	public record StartUploadRequest(String fileName, long fileSize) {
	}

	public record StartUploadResponse(String uploadId, int partSize, int partCount) {
	}

	public record DocumentResponse(String id, String title, String fileName, long fileSize, Integer pageCount,
		String status, String failReason, LocalDateTime createdAt) {

		public static DocumentResponse of(SnStudyDocumentModel d) {
			return new DocumentResponse(d.getObjId(), d.getTitle(), d.getFileNm(), d.getFileSize(), d.getPageCount(),
				d.getStatus(), d.getFailReason(), d.getCreatedAt());
		}
	}

	public record TocNode(String id, String title, int level, String pageSectionId, String anchorBlockKey,
		List<TocNode> children) {
	}

	public record SectionRef(String id, String title) {
	}

	public record OutlineItem(String blockKey, String title, int level) {
	}

	public record PageCell(String key, int row, int col, String text) {
	}

	public record PageBlock(String key, String type, Integer level, String text, Integer page, List<PageCell> cells) {
	}

	public record TermHitDto(String blockKey, int start, int end, String termId) {
	}

	public record TermDefDto(String term, String definition, String scope) {
	}

	public record PageAnnotation(String id, String startBlockKey, int startOffset, String endBlockKey, int endOffset,
		String color, boolean hasNote) {
	}

	public record PageResponse(SectionRef section, SectionRef prev, SectionRef next, List<OutlineItem> outline,
		List<PageBlock> blocks, List<TermHitDto> termHits, Map<String, TermDefDto> terms,
		List<PageAnnotation> annotations) {
	}

	public record TermRequest(String term, String definition, String documentId) {
	}

	public record TermResponse(String id, String term, String definition, String documentId, String scope) {
	}

	public record CreateAnnotationRequest(String documentId, String startBlockKey, int startOffset,
		String endBlockKey, int endOffset, String color, String note) {
	}

	public record UpdateAnnotationRequest(String color, String note) {
	}

	public record NoteLocation(String documentId, String pageSectionId, String blockKey) {
	}

	public record AnnotationListItem(String id, String documentId, String documentTitle, String sectionTitle,
		String quote, String note, String color, LocalDateTime createdAt, NoteLocation location) {
	}

	public record StudyDocumentUploadedEvent(String documentId) {
	}
}
