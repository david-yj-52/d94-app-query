package tsh.d94.app.server.apService.study;

import static tsh.d94.app.server.apService.study.exception.StudyErrorCode.*;

import java.util.List;
import java.util.Objects;

import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockModel;

/** 마킹 범위 검증과 quote 생성. 오프셋은 블록 텍스트의 UTF-16 단위(브라우저 Range 오프셋과 같다), end 미포함. */
final class AnnotationRange {

	static final int MAX_QUOTE = 1000;

	private AnnotationRange() {
	}

	/** between: ord 가 start 와 end 사이인 블록(TABLE 컨테이너는 호출자가 뺀다) */
	static String validateAndQuote(SnStudyBlockModel start, int startOffset, SnStudyBlockModel end, int endOffset,
		List<SnStudyBlockModel> between, String startPageSectionId, String endPageSectionId) {
		String st = StudyPageService.text(start);
		String et = StudyPageService.text(end);
		boolean samePage = Objects.equals(startPageSectionId, endPageSectionId);
		if (!samePage || start.getOrd() > end.getOrd() || startOffset < 0 || endOffset < 0
			|| startOffset > st.length() || endOffset > et.length()) {
			throw new StudyException(INVALID_RANGE);
		}
		String quote;
		if (start.getOrd().equals(end.getOrd())) {
			if (startOffset >= endOffset) {
				throw new StudyException(INVALID_RANGE);
			}
			quote = st.substring(startOffset, endOffset);
		} else {
			StringBuilder sb = new StringBuilder(st.substring(startOffset));
			between.forEach(b -> sb.append('\n').append(StudyPageService.text(b)));
			quote = sb.append('\n').append(et, 0, endOffset).toString();
		}
		if (quote.isBlank()) {
			throw new StudyException(INVALID_RANGE);
		}
		return quote.length() > MAX_QUOTE ? quote.substring(0, MAX_QUOTE - 1) + "…" : quote;
	}
}
