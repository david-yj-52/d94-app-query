package tsh.d94.app.server.apService.study;

import static tsh.d94.app.server.apService.study.exception.StudyErrorCode.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.dto.StudyDtos.TermRequest;
import tsh.d94.app.server.apService.study.dto.StudyDtos.TermResponse;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.studyTerm.SnStudyTermAccess;
import tsh.d94.app.server.data.orm.study.studyTerm.SnStudyTermModel;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

@Service
@RequiredArgsConstructor
public class StudyTermService {

	private final SnStudyTermAccess termAccess;
	private final StudyDocumentService documents;

	/** documentId 가 있으면 전역 + 그 문서용, 없으면 전역만 */
	public List<TermResponse> list(String documentId) {
		String owner = Study.owner();
		List<SnStudyTermModel> terms = new ArrayList<>(termAccess.findByOwnerIdAndDocumentIdIsNull(owner));
		if (documentId != null && !documentId.isBlank()) {
			documents.requireOwned(documentId);
			terms.addAll(termAccess.findByOwnerIdAndDocumentId(owner, documentId));
		}
		return terms.stream().map(StudyTermService::toResponse).toList();
	}

	/** 같은 normTerm 이면 문서용이 전역을 덮어쓴다 */
	public List<SnStudyTermModel> effectiveTerms(String documentId) {
		String owner = Study.owner();
		Map<String, SnStudyTermModel> byNorm = new LinkedHashMap<>();
		termAccess.findByOwnerIdAndDocumentIdIsNull(owner).forEach(t -> byNorm.put(t.getNormTerm(), t));
		termAccess.findByOwnerIdAndDocumentId(owner, documentId).forEach(t -> byNorm.put(t.getNormTerm(), t));
		return new ArrayList<>(byNorm.values());
	}

	public TermResponse create(TermRequest req) {
		validate(req);
		String owner = Study.owner();
		String documentId = req.documentId() == null || req.documentId().isBlank() ? null : req.documentId();
		if (documentId != null) {
			documents.requireOwned(documentId);
		}
		String norm = TermMatcher.normalize(req.term());
		boolean exists = documentId == null
			? termAccess.existsByOwnerIdAndDocumentIdIsNullAndNormTerm(owner, norm)
			: termAccess.existsByOwnerIdAndDocumentIdAndNormTerm(owner, documentId, norm);
		if (exists) {
			throw new StudyException(TERM_DUPLICATE);
		}
		return toResponse(termAccess.save(Study.base(SnStudyTermModel.builder(), "CreateStudyTerm", TenantContext.get())
			.ownerId(owner)
			.documentId(documentId)
			.term(req.term().trim())
			.normTerm(norm)
			.definition(req.definition().trim())
			.build()));
	}

	public TermResponse update(String id, TermRequest req) {
		validate(req);
		SnStudyTermModel term = owned(id);
		String norm = TermMatcher.normalize(req.term());
		if (!norm.equals(term.getNormTerm())) {
			boolean exists = term.getDocumentId() == null
				? termAccess.existsByOwnerIdAndDocumentIdIsNullAndNormTerm(term.getOwnerId(), norm)
				: termAccess.existsByOwnerIdAndDocumentIdAndNormTerm(term.getOwnerId(), term.getDocumentId(), norm);
			if (exists) {
				throw new StudyException(TERM_DUPLICATE);
			}
		}
		term.setTerm(req.term().trim());
		term.setNormTerm(norm);
		term.setDefinition(req.definition().trim());
		return toResponse(termAccess.save(term));
	}

	public void delete(String id) {
		termAccess.remove(owned(id));
	}

	private SnStudyTermModel owned(String id) {
		return termAccess.findByObjIdAndOwnerId(id, Study.owner()).orElseThrow(() -> new StudyException(NOT_FOUND));
	}

	private static void validate(TermRequest req) {
		if (req.term() == null || req.term().isBlank() || req.term().length() > 300
			|| req.definition() == null || req.definition().isBlank()) {
			throw new StudyException(INVALID_REQUEST, "단어(300자 이하)와 뜻을 입력하세요.");
		}
	}

	static TermResponse toResponse(SnStudyTermModel t) {
		return new TermResponse(t.getObjId(), t.getTerm(), t.getDefinition(), t.getDocumentId(),
			t.getDocumentId() == null ? "GLOBAL" : "DOCUMENT");
	}
}
