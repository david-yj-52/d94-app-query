package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.BoardColumnResponse;
import tsh.d94.app.server.apService.cira.dto.BoardDetailResponse;
import tsh.d94.app.server.apService.cira.dto.BoardIssueResponse;
import tsh.d94.app.server.apService.cira.dto.BoardResponse;
import tsh.d94.app.server.apService.cira.dto.CreateBoardColumnRequest;
import tsh.d94.app.server.apService.cira.dto.CreateBoardRequest;
import tsh.d94.app.server.apService.cira.dto.MoveIssueRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateBoardColumnRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateBoardRequest;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardModel;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnModel;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssuePosition.SnCiraIssuePositionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssuePosition.SnCiraIssuePositionModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.lib.core.apService.auth.dto.UserResponse;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardService {

	private final SnCiraBoardAccess boardAccess;
	private final SnCiraBoardColumnAccess boardColumnAccess;
	private final SnCiraIssuePositionAccess issuePositionAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraIssueStatusAccess statusAccess;
	private final SnCiraCiraIssueTypeAccess issueTypeAccess;
	private final GsUserAccess userAccess;
	private final SnCiraProjectMemberAccess projectMemberAccess;

	@Transactional
	public BoardResponse createBoard(String projectId, CreateBoardRequest request) {
		requireProjectMember(projectId);

		SnCiraBoardModel board = SnCiraBoardModel.builder()
			.projectId(projectId)
			.boardNm(request.getBoardNm())
			.boardType(request.getBoardType() != null ? request.getBoardType() : "KANBAN")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("CREATE-BOARD")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateBoard")
			.prevEvntNm("None")
			.build();

		boardAccess.save(board);
		return mapToBoardResponse(board, 0);
	}

	public List<BoardResponse> listBoards(String projectId) {
		requireProjectMember(projectId);
		return boardAccess.findByProjectId(projectId).stream()
			.filter(b -> b.getUseStatCd() == UseStatCd.Usable)
			.map(b -> {
				int colCount = (int)boardColumnAccess.findByBoardIdOrderBySortOrd(b.getObjId()).stream()
					.filter(c -> c.getUseStatCd() == UseStatCd.Usable)
					.count();
				return mapToBoardResponse(b, colCount);
			})
			.collect(Collectors.toList());
	}

	public BoardDetailResponse getBoardDetail(String boardId) {
		SnCiraBoardModel board = boardAccess.findById(boardId);
		requireProjectMember(board.getProjectId());
		return assembleBoardDetail(board);
	}

	@Transactional
	public BoardResponse updateBoard(String boardId, UpdateBoardRequest request) {
		SnCiraBoardModel board = boardAccess.findById(boardId);
		requireProjectMember(board.getProjectId());

		if (request.getBoardNm() != null) {
			board.setBoardNm(request.getBoardNm());
		}
		boardAccess.save(board);

		int colCount = (int)boardColumnAccess.findByBoardIdOrderBySortOrd(boardId).stream()
			.filter(c -> c.getUseStatCd() == UseStatCd.Usable)
			.count();
		return mapToBoardResponse(board, colCount);
	}

	@Transactional
	public void deleteBoard(String boardId) {
		SnCiraBoardModel board = boardAccess.findById(boardId);
		requireProjectMember(board.getProjectId());
		boardAccess.delete(boardId);
	}

	@Transactional
	public BoardColumnResponse addColumn(String boardId, CreateBoardColumnRequest request) {
		SnCiraBoardModel board = boardAccess.findById(boardId);
		requireProjectMember(board.getProjectId());

		List<SnCiraBoardColumnModel> existing = boardColumnAccess.findByBoardIdOrderBySortOrd(boardId).stream()
			.filter(c -> c.getUseStatCd() == UseStatCd.Usable)
			.collect(Collectors.toList());
		short nextOrd = existing.isEmpty() ? 1 : (short)(existing.get(existing.size() - 1).getSortOrd() + 1);

		SnCiraBoardColumnModel column = SnCiraBoardColumnModel.builder()
			.boardId(boardId)
			.statusId(request.getStatusId())
			.columnNm(request.getColumnNm())
			.wipLimit(request.getWipLimit())
			.sortOrd(nextOrd)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("ADD-COLUMN")
			.useStatCd(UseStatCd.Usable)
			.evtNm("AddColumn")
			.prevEvntNm("None")
			.build();

		boardColumnAccess.save(column);

		SnCiraIssueStatusModel status = statusAccess.findByIdOptional(request.getStatusId()).orElse(null);
		return mapToColumnResponse(column, status, List.of());
	}

	@Transactional
	public BoardColumnResponse updateColumn(String boardId, String columnId, UpdateBoardColumnRequest request) {
		SnCiraBoardColumnModel column = boardColumnAccess.findById(columnId);
		if (!column.getBoardId().equals(boardId)) {
			throw new CiraException(ErrorCode.BOARD_COLUMN_NOT_FOUND);
		}
		SnCiraBoardModel board = boardAccess.findById(boardId);
		requireProjectMember(board.getProjectId());

		if (request.getColumnNm() != null)
			column.setColumnNm(request.getColumnNm());
		if (request.getWipLimit() != null)
			column.setWipLimit(request.getWipLimit());
		boardColumnAccess.save(column);

		SnCiraIssueStatusModel status = statusAccess.findByIdOptional(column.getStatusId()).orElse(null);
		List<BoardIssueResponse> issues = resolveIssuesForColumn(columnId);
		return mapToColumnResponse(column, status, issues);
	}

	@Transactional
	public void deleteColumn(String boardId, String columnId) {
		SnCiraBoardColumnModel column = boardColumnAccess.findById(columnId);
		if (!column.getBoardId().equals(boardId)) {
			throw new CiraException(ErrorCode.BOARD_COLUMN_NOT_FOUND);
		}
		SnCiraBoardModel board = boardAccess.findById(boardId);
		requireProjectMember(board.getProjectId());
		boardColumnAccess.delete(columnId);
	}

	@Transactional
	public BoardDetailResponse moveIssue(String boardId, String issueId, MoveIssueRequest request) {
		SnCiraBoardModel board = boardAccess.findById(boardId);
		requireProjectMember(board.getProjectId());

		SnCiraBoardColumnModel targetColumn = boardColumnAccess.findById(request.getTargetColumnId());
		if (!targetColumn.getBoardId().equals(boardId)) {
			throw new CiraException(ErrorCode.BOARD_COLUMN_NOT_FOUND, "컬럼이 해당 보드에 속하지 않습니다.");
		}

		// WIP 제한 검증
		Short wipLimit = targetColumn.getWipLimit();
		if (wipLimit != null && wipLimit > 0) {
			long currentCount = issuePositionAccess.findByColumnIdOrderByRankStr(request.getTargetColumnId()).stream()
				.filter(p -> p.getUseStatCd() == UseStatCd.Usable)
				.filter(p -> !p.getIssueId().equals(issueId))
				.count();
			if (currentCount >= wipLimit) {
				throw new CiraException(ErrorCode.BOARD_WIP_LIMIT_EXCEEDED,
					"WIP 제한(" + wipLimit + ")을 초과하였습니다. 현재 이슈 수: " + currentCount);
			}
		}

		Set<String> boardColumnIds = boardColumnAccess.findByBoardIdOrderBySortOrd(boardId).stream()
			.map(SnCiraBoardColumnModel::getObjId)
			.collect(Collectors.toSet());

		List<SnCiraIssuePositionModel> existingInBoard = issuePositionAccess.findByIssueId(issueId).stream()
			.filter(p -> boardColumnIds.contains(p.getColumnId()))
			.collect(Collectors.toList());

		String newRank = calculateNewRank(request.getTargetColumnId(), issueId,
			request.getAfterIssueId(), request.getBeforeIssueId());

		if (!existingInBoard.isEmpty()) {
			SnCiraIssuePositionModel pos = existingInBoard.get(0);
			pos.setColumnId(request.getTargetColumnId());
			pos.setRankStr(newRank);
			issuePositionAccess.save(pos);
			for (int i = 1; i < existingInBoard.size(); i++) {
				issuePositionAccess.delete(existingInBoard.get(i).getObjId());
			}
		} else {
			SnCiraIssuePositionModel pos = SnCiraIssuePositionModel.builder()
				.issueId(issueId)
				.columnId(request.getTargetColumnId())
				.rankStr(newRank)
				.srvId(ApplicationProperties.getApplicationServiceName())
				.tenant(ApplicationProperties.getApplicationTenant())
				.traceId("MOVE-ISSUE")
				.useStatCd(UseStatCd.Usable)
				.evtNm("MoveIssue")
				.prevEvntNm("None")
				.build();
			issuePositionAccess.save(pos);
		}

		// 이슈 상태를 컬럼의 상태와 동기화
		SnCiraIssueModel issue = issueAccess.findById(issueId);
		if (!targetColumn.getStatusId().equals(issue.getStatusId())) {
			issue.setStatusId(targetColumn.getStatusId());
			issueAccess.save(issue);
		}

		return assembleBoardDetail(board);
	}

	// ── Private helpers ──────────────────────────────────────────────────────

	private BoardDetailResponse assembleBoardDetail(SnCiraBoardModel board) {
		List<BoardColumnResponse> columnResponses = boardColumnAccess
			.findByBoardIdOrderBySortOrd(board.getObjId()).stream()
			.filter(c -> c.getUseStatCd() == UseStatCd.Usable)
			.map(col -> {
				SnCiraIssueStatusModel status = statusAccess.findByIdOptional(col.getStatusId()).orElse(null);
				List<BoardIssueResponse> issues = resolveIssuesForColumn(col.getObjId());
				return mapToColumnResponse(col, status, issues);
			})
			.collect(Collectors.toList());

		return BoardDetailResponse.builder()
			.id(board.getObjId())
			.projectId(board.getProjectId())
			.boardNm(board.getBoardNm())
			.boardType(board.getBoardType())
			.columns(columnResponses)
			.createdAt(board.getCreatedAt())
			.modifiedAt(board.getModifiedAt())
			.build();
	}

	private List<BoardIssueResponse> resolveIssuesForColumn(String columnId) {
		return issuePositionAccess.findByColumnIdOrderByRankStr(columnId).stream()
			.filter(p -> p.getUseStatCd() == UseStatCd.Usable)
			.map(pos -> {
				try {
					SnCiraIssueModel issue = issueAccess.findById(pos.getIssueId());
					if (issue.getDeletedAt() != null)
						return null;
					return mapToBoardIssueResponse(issue, pos.getRankStr());
				} catch (Exception e) {
					return null;
				}
			})
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}

	private String calculateNewRank(String targetColumnId, String movingIssueId,
		String afterIssueId, String beforeIssueId) {
		List<SnCiraIssuePositionModel> columnPositions = issuePositionAccess
			.findByColumnIdOrderByRankStr(targetColumnId).stream()
			.filter(p -> p.getUseStatCd() == UseStatCd.Usable)
			.filter(p -> !p.getIssueId().equals(movingIssueId))
			.collect(Collectors.toList());

		if (columnPositions.isEmpty()) {
			return LexoRankUtil.initial();
		}

		if (afterIssueId == null && beforeIssueId == null) {
			return LexoRankUtil.after(columnPositions.get(columnPositions.size() - 1).getRankStr());
		}

		if (afterIssueId != null && beforeIssueId == null) {
			int idx = findPositionIndex(columnPositions, afterIssueId);
			if (idx < 0 || idx == columnPositions.size() - 1) {
				String base = idx < 0 ? columnPositions.get(columnPositions.size() - 1).getRankStr()
					: columnPositions.get(idx).getRankStr();
				return LexoRankUtil.after(base);
			}
			String rank = LexoRankUtil.between(columnPositions.get(idx).getRankStr(),
				columnPositions.get(idx + 1).getRankStr());
			return rank != null ? rank : rebalanceAndGetRank(targetColumnId, columnPositions, idx + 1);
		}

		if (afterIssueId == null) {
			int idx = findPositionIndex(columnPositions, beforeIssueId);
			if (idx <= 0) {
				return LexoRankUtil.before(columnPositions.get(0).getRankStr());
			}
			String rank = LexoRankUtil.between(columnPositions.get(idx - 1).getRankStr(),
				columnPositions.get(idx).getRankStr());
			return rank != null ? rank : rebalanceAndGetRank(targetColumnId, columnPositions, idx);
		}

		// 양쪽 모두 지정된 경우
		int afterIdx = findPositionIndex(columnPositions, afterIssueId);
		int beforeIdx = findPositionIndex(columnPositions, beforeIssueId);
		if (afterIdx < 0 || beforeIdx < 0) {
			return LexoRankUtil.after(columnPositions.get(columnPositions.size() - 1).getRankStr());
		}
		String rank = LexoRankUtil.between(columnPositions.get(afterIdx).getRankStr(),
			columnPositions.get(beforeIdx).getRankStr());
		return rank != null ? rank : rebalanceAndGetRank(targetColumnId, columnPositions, afterIdx + 1);
	}

	private int findPositionIndex(List<SnCiraIssuePositionModel> positions, String issueId) {
		for (int i = 0; i < positions.size(); i++) {
			if (positions.get(i).getIssueId().equals(issueId))
				return i;
		}
		return -1;
	}

	private String rebalanceAndGetRank(String columnId,
		List<SnCiraIssuePositionModel> positions, int insertAt) {
		List<String> newRanks = LexoRankUtil.rebalance(positions.size() + 1);
		for (int i = 0; i < insertAt && i < positions.size(); i++) {
			positions.get(i).setRankStr(newRanks.get(i));
			issuePositionAccess.save(positions.get(i));
		}
		String insertRank = newRanks.get(insertAt);
		for (int i = insertAt; i < positions.size(); i++) {
			positions.get(i).setRankStr(newRanks.get(i + 1));
			issuePositionAccess.save(positions.get(i));
		}
		return insertRank;
	}

	private void requireProjectMember(String projectId) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
		projectMemberAccess.findAllByUserId(user.getObjId()).stream()
			.filter(m -> m.getProjectId().equals(projectId))
			.findFirst()
			.orElseThrow(() -> new CiraException(ErrorCode.PROJECT_NOT_MEMBER));
	}

	private BoardIssueResponse mapToBoardIssueResponse(SnCiraIssueModel issue, String rankStr) {
		GsUserModel assignee = issue.getAssigneeId() != null
			? userAccess.findByIdOptional(issue.getAssigneeId()).orElse(null) : null;
		SnCiraCiraIssueTypeModel type = issueTypeAccess.findByIdOptional(issue.getIssueTypeId()).orElse(null);
		SnCiraIssueStatusModel status = statusAccess.findByIdOptional(issue.getStatusId()).orElse(null);

		return BoardIssueResponse.builder()
			.id(issue.getObjId())
			.issueKey(issue.getIssueKey())
			.title(issue.getTitle())
			.priority(issue.getPriority())
			.issueTypeId(issue.getIssueTypeId())
			.issueTypeNm(type != null ? type.getTypeNm() : null)
			.statusId(issue.getStatusId())
			.statusNm(status != null ? status.getStatusNm() : null)
			.assignee(assignee != null
				? UserResponse.builder().email(assignee.getEmail()).name(assignee.getUserNm())
				.avatarUrl(assignee.getAvatarUrl()).build()
				: null)
			.rankStr(rankStr)
			.build();
	}

	private BoardResponse mapToBoardResponse(SnCiraBoardModel board, int colCount) {
		return BoardResponse.builder()
			.id(board.getObjId())
			.projectId(board.getProjectId())
			.boardNm(board.getBoardNm())
			.boardType(board.getBoardType())
			.columnCount(colCount)
			.createdAt(board.getCreatedAt())
			.modifiedAt(board.getModifiedAt())
			.build();
	}

	private BoardColumnResponse mapToColumnResponse(SnCiraBoardColumnModel col,
		SnCiraIssueStatusModel status, List<BoardIssueResponse> issues) {
		return BoardColumnResponse.builder()
			.id(col.getObjId())
			.boardId(col.getBoardId())
			.statusId(col.getStatusId())
			.statusNm(status != null ? status.getStatusNm() : null)
			.statusCategory(status != null ? status.getCategory() : null)
			.colorCd(status != null ? status.getColorCd() : null)
			.columnNm(col.getColumnNm())
			.wipLimit(col.getWipLimit())
			.sortOrd(col.getSortOrd())
			.issues(issues)
			.build();
	}
}
