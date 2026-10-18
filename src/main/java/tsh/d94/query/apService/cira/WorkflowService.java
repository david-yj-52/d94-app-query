package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import tsh.d94.app.server.apService.cira.dto.IssueStatusResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

	private final SnCiraIssueTransitionAccess transitionAccess;
	private final SnCiraIssueStatusAccess statusAccess;

	/**
	 * 상태 전이 검증.
	 * issue_transitions 테이블에 allowYn='Y' 행이 없으면 전이 불가로 판단한다.
	 */
	public void validateTransition(String projectId, String fromStatusId, String toStatusId) {
		if (fromStatusId.equals(toStatusId)) {
			return;
		}

		statusAccess.findByIdOptional(toStatusId)
			.orElseThrow(() -> new CiraException(ErrorCode.ISSUE_STATUS_NOT_FOUND,
				"존재하지 않는 상태 ID: " + toStatusId));

		List<SnCiraIssueTransitionModel> transitions = getProjectTransitions(projectId);

		boolean allowed = transitions.stream()
			.anyMatch(t ->
				fromStatusId.equals(t.getFromStatusId()) &&
					toStatusId.equals(t.getToStatusId()) &&
					"Y".equals(t.getAllowYn())
			);

		if (!allowed) {
			log.warn("허용되지 않은 상태 전이 - projectId={}, from={}, to={}", projectId, fromStatusId, toStatusId);
			throw new CiraException(ErrorCode.ISSUE_INVALID_TRANSITION);
		}
	}

	/**
	 * 현재 상태에서 전이 가능한 상태 목록 반환.
	 * UI 드롭다운에서 선택 가능한 상태만 노출하기 위해 사용한다.
	 */
	public List<IssueStatusResponse> getAvailableTransitions(String projectId, String fromStatusId) {
		List<SnCiraIssueTransitionModel> transitions = getProjectTransitions(projectId);

		List<String> toStatusIds = transitions.stream()
			.filter(t -> fromStatusId.equals(t.getFromStatusId()) && "Y".equals(t.getAllowYn()))
			.map(SnCiraIssueTransitionModel::getToStatusId)
			.collect(Collectors.toList());

		return toStatusIds.stream()
			.map(statusId -> statusAccess.findByIdOptional(statusId).orElse(null))
			.filter(s -> s != null)
			.map(this::mapToStatusResponse)
			.collect(Collectors.toList());
	}

	@Cacheable(value = "workflowTransitions", key = "#projectId")
	public List<SnCiraIssueTransitionModel> getProjectTransitions(String projectId) {
		return transitionAccess.findByProjectId(projectId);
	}

	public IssueStatusResponse mapToStatusResponse(SnCiraIssueStatusModel model) {
		return IssueStatusResponse.builder()
			.id(model.getObjId())
			.statusNm(model.getStatusNm())
			.category(model.getCategory())
			.colorCd(model.getColorCd())
			.sortOrd(model.getSortOrd())
			.build();
	}
}
