package tsh.d94.app.server.apService.study.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyErrorCode {

	NOT_FOUND("STUDY_001", HttpStatus.NOT_FOUND, "존재하지 않습니다."),
	INVALID_FILE("STUDY_002", HttpStatus.BAD_REQUEST, "PDF 파일만 올릴 수 있습니다."),
	FILE_TOO_LARGE("STUDY_003", HttpStatus.BAD_REQUEST, "50MB 이하 파일만 올릴 수 있습니다."),
	UPLOAD_INCOMPLETE("STUDY_UPLOAD_INCOMPLETE", HttpStatus.BAD_REQUEST, "업로드되지 않은 조각이 있습니다."),
	PART_INVALID("STUDY_005", HttpStatus.BAD_REQUEST, "잘못된 업로드 조각입니다."),
	NOT_READY("STUDY_006", HttpStatus.CONFLICT, "문서를 아직 분석 중이거나 분석에 실패했습니다."),
	INVALID_RANGE("STUDY_007", HttpStatus.BAD_REQUEST, "잘못된 선택 범위입니다."),
	TERM_DUPLICATE("STUDY_008", HttpStatus.CONFLICT, "이미 등록된 단어입니다."),
	INVALID_REQUEST("STUDY_009", HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");

	private final String code;
	private final HttpStatus status;
	private final String defaultMessage;
}
