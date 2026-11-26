package tsh.d94.app.server.apService.cira.automation.events;

import org.springframework.context.ApplicationEvent;

/** 이슈 제목 변경. 자동화 트리거는 아니고 Docs 메인 노트 제목 동기화용 (CiraDocsService) */
public class IssueTitleChangedEvent extends ApplicationEvent {

	private final String issueId;

	public IssueTitleChangedEvent(Object source, String issueId) {
		super(source);
		this.issueId = issueId;
	}

	public String getIssueId() {
		return issueId;
	}
}
