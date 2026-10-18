package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.AddReactionRequest;
import tsh.d94.app.server.apService.cira.dto.CommentReactionResponse;
import tsh.d94.app.server.apService.cira.dto.CommentResponse;
import tsh.d94.app.server.apService.cira.dto.CreateCommentRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateCommentRequest;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraComment.SnCiraCommentAccess;
import tsh.d94.app.server.data.orm.cira.ciraComment.SnCiraCommentModel;
import tsh.d94.app.server.data.orm.cira.ciraCommentReaction.SnCiraCommentReactionAccess;
import tsh.d94.app.server.data.orm.cira.ciraCommentReaction.SnCiraCommentReactionModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.lib.core.apService.auth.dto.UserResponse;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {

	private final SnCiraCommentAccess commentAccess;
	private final SnCiraIssueAccess issueAccess;
	private final GsUserAccess userAccess;
	private final SnCiraCommentReactionAccess commentReactionAccess;
	private final NotificationService notificationService;

	@Transactional
	public CommentResponse createComment(String issueId, CreateCommentRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel author = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraIssueModel issue = issueAccess.findById(issueId);
		if (issue.getDeletedAt() != null) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: " + issueId);
		}

		if (request.getParentId() != null) {
			commentAccess.findByIdOptional(request.getParentId())
				.filter(c -> UseStatCd.Usable.equals(c.getUseStatCd()))
				.orElseThrow(
					() -> new CiraException(ErrorCode.COMMENT_NOT_FOUND, "부모 댓글을 찾을 수 없습니다: " + request.getParentId()));
		}

		SnCiraCommentModel comment = SnCiraCommentModel.builder()
			.issueId(issueId)
			.authorId(author.getObjId())
			.parentId(request.getParentId())
			.content(request.getContent())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("CREATE-COMMENT")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateComment")
			.prevEvntNm("None")
			.build();

		commentAccess.save(comment);

		// 댓글 알림: 담당자/보고자에게 발송 (본인 제외), @멘션 파싱
		String commentTitle = "새 댓글이 달렸습니다";
		String commentMsg = "[" + issue.getIssueKey() + "] " + request.getContent();
		java.util.Set<String> notified = new java.util.HashSet<>();
		notified.add(author.getObjId());

		if (issue.getAssigneeId() != null && notified.add(issue.getAssigneeId())) {
			notificationService.send(issue.getAssigneeId(), "COMMENT_ADDED",
				commentTitle, commentMsg, "ISSUE", issueId);
		}
		if (notified.add(issue.getReporterId())) {
			notificationService.send(issue.getReporterId(), "COMMENT_ADDED",
				commentTitle, commentMsg, "ISSUE", issueId);
		}

		// @멘션 파싱: @userId 패턴
		java.util.regex.Pattern mentionPattern = java.util.regex.Pattern.compile("@([\\w.-]+)");
		java.util.regex.Matcher matcher = mentionPattern.matcher(request.getContent());
		while (matcher.find()) {
			String mentionEmail = matcher.group(1);
			userAccess.findByEmail(mentionEmail).ifPresent(mentioned -> {
				if (notified.add(mentioned.getObjId())) {
					notificationService.send(mentioned.getObjId(), "COMMENT_MENTIONED",
						"댓글에서 멘션되었습니다", commentMsg, "ISSUE", issueId);
				}
			});
		}

		return mapToResponse(comment, false, author.getObjId());
	}

	public List<CommentResponse> getComments(String issueId) {
		SnCiraIssueModel issue = issueAccess.findById(issueId);
		if (issue.getDeletedAt() != null) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: " + issueId);
		}

		String currentUserId = resolveCurrentUserId();
		List<SnCiraCommentModel> all = commentAccess.findByIssueId(issueId);

		return all.stream()
			.filter(c -> c.getParentId() == null && UseStatCd.Usable.equals(c.getUseStatCd()))
			.map(root -> {
				CommentResponse response = mapToResponse(root, false, currentUserId);
				List<CommentResponse> replies = all.stream()
					.filter(c -> root.getObjId().equals(c.getParentId()) && UseStatCd.Usable.equals(c.getUseStatCd()))
					.map(reply -> mapToResponse(reply, true, currentUserId))
					.collect(Collectors.toList());
				response.setReplies(replies);
				return response;
			})
			.collect(Collectors.toList());
	}

	@Transactional
	public CommentResponse updateComment(String commentId, UpdateCommentRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraCommentModel comment = commentAccess.findById(commentId);
		if (!UseStatCd.Usable.equals(comment.getUseStatCd())) {
			throw new CiraException(ErrorCode.COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다: " + commentId);
		}

		if (!comment.getAuthorId().equals(user.getObjId())) {
			throw new CiraException(ErrorCode.COMMENT_NOT_AUTHOR);
		}

		comment.setContent(request.getContent());
		comment.setEvtNm("UpdateComment");
		comment.setPrevEvntNm("CreateComment");
		commentAccess.save(comment);

		return mapToResponse(comment, comment.getParentId() != null, user.getObjId());
	}

	@Transactional
	public void deleteComment(String commentId) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraCommentModel comment = commentAccess.findById(commentId);
		if (!UseStatCd.Usable.equals(comment.getUseStatCd())) {
			throw new CiraException(ErrorCode.COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다: " + commentId);
		}

		if (!comment.getAuthorId().equals(user.getObjId())) {
			throw new CiraException(ErrorCode.COMMENT_NOT_AUTHOR);
		}

		comment.setUseStatCd(UseStatCd.Delete);
		comment.setEvtNm("DeleteComment");
		comment.setPrevEvntNm("UpdateComment");
		commentAccess.save(comment);
	}

	@Transactional
	public CommentResponse toggleReaction(String commentId, AddReactionRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraCommentModel comment = commentAccess.findById(commentId);
		if (!UseStatCd.Usable.equals(comment.getUseStatCd())) {
			throw new CiraException(ErrorCode.COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다: " + commentId);
		}

		Optional<SnCiraCommentReactionModel> existing = commentReactionAccess
			.findByCommentIdAndUserIdAndReactionType(commentId, user.getObjId(), request.getReactionType());

		if (existing.isPresent()) {
			SnCiraCommentReactionModel reaction = existing.get();
			if (UseStatCd.Usable.equals(reaction.getUseStatCd())) {
				reaction.setUseStatCd(UseStatCd.Delete);
				reaction.setEvtNm("RemoveReaction");
				reaction.setPrevEvntNm("AddReaction");
			} else {
				reaction.setUseStatCd(UseStatCd.Usable);
				reaction.setEvtNm("AddReaction");
				reaction.setPrevEvntNm("RemoveReaction");
			}
			commentReactionAccess.save(reaction);
		} else {
			SnCiraCommentReactionModel reaction = SnCiraCommentReactionModel.builder()
				.commentId(commentId)
				.userId(user.getObjId())
				.reactionType(request.getReactionType())
				.srvId(ApplicationProperties.getApplicationServiceName())
				.tenant(ApplicationProperties.getApplicationTenant())
				.traceId("ADD-REACTION")
				.useStatCd(UseStatCd.Usable)
				.evtNm("AddReaction")
				.prevEvntNm("None")
				.build();
			commentReactionAccess.save(reaction);
		}

		return mapToResponse(comment, comment.getParentId() != null, user.getObjId());
	}

	private String resolveCurrentUserId() {
		try {
			String email = SecurityContextHolder.getContext().getAuthentication().getName();
			return userAccess.findByEmail(email).map(GsUserModel::getObjId).orElse(null);
		} catch (Exception ignored) {
			return null;
		}
	}

	private CommentResponse mapToResponse(SnCiraCommentModel model, boolean suppressReplies, String currentUserId) {
		GsUserModel author = userAccess.findByIdOptional(model.getAuthorId()).orElse(null);

		List<SnCiraCommentReactionModel> rawReactions = commentReactionAccess.findByCommentId(model.getObjId());
		List<CommentReactionResponse> reactions = buildReactionSummary(rawReactions, currentUserId);

		return CommentResponse.builder()
			.id(model.getObjId())
			.issueId(model.getIssueId())
			.parentId(model.getParentId())
			.content(model.getContent())
			.author(author != null
				? UserResponse.builder()
				.email(author.getEmail())
				.name(author.getUserNm())
				.avatarUrl(author.getAvatarUrl())
				.build()
				: null)
			.replies(suppressReplies ? null : List.of())
			.reactions(reactions)
			.createdAt(model.getCreatedAt())
			.modifiedAt(model.getModifiedAt())
			.build();
	}

	private List<CommentReactionResponse> buildReactionSummary(
		List<SnCiraCommentReactionModel> reactions, String currentUserId) {
		Map<String, List<SnCiraCommentReactionModel>> grouped = reactions.stream()
			.filter(r -> UseStatCd.Usable.equals(r.getUseStatCd()))
			.collect(Collectors.groupingBy(SnCiraCommentReactionModel::getReactionType));

		return grouped.entrySet().stream()
			.map(e -> CommentReactionResponse.builder()
				.reactionType(e.getKey())
				.count(e.getValue().size())
				.reacted(currentUserId != null && e.getValue().stream()
					.anyMatch(r -> r.getUserId().equals(currentUserId)))
				.build())
			.collect(Collectors.toList());
	}
}
