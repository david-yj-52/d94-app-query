package tsh.d94.app.server.apService.cira.automation.events;

import org.springframework.context.ApplicationEvent;

/** Task 설명 수정 요청. 설명은 Docs 메인 노트에만 있으므로 CiraDocsService 가 노트 본문을 바꾼다 (V650 단일 원본) */
public class IssueContentChangedEvent extends ApplicationEvent {

	private final String issueId;
	private final String content;

	public IssueContentChangedEvent(Object source, String issueId, String content) {
		super(source);
		this.issueId = issueId;
		this.content = content;
	}

	public String getIssueId() {
		return issueId;
	}

	public String getContent() {
		return content;
	}
}
