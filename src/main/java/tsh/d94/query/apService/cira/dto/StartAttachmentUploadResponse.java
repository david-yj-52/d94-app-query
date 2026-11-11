package tsh.d94.app.server.apService.cira.dto;

/** 조각 업로드 시작 응답: 이 크기(partSize)로 잘라 1..partCount 번 조각을 보낸다 */
public record StartAttachmentUploadResponse(String uploadId, int partSize, int partCount) {
}
