package tsh.d94.app.server.apService.ifTest;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.ifTest.dto.InterfaceTestItemRequest;
import tsh.d94.app.server.apService.ifTest.dto.InterfaceTestItemResponse;
import tsh.d94.app.server.data.orm.ifTest.ifTestItem.SnIfTestItemAccess;
import tsh.d94.app.server.data.orm.ifTest.ifTestItem.SnIfTestItemModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * 인터페이스 테스트: UI → (UMD) → 백엔드 → DB 왕복을 메서드별(GET/POST/PUT/PATCH/DELETE)로 확인하는 단순 CRUD.
 */
@Service
@RequiredArgsConstructor
public class InterfaceTestService {

	private static final String STAT_NEW = "NEW";

	private final SnIfTestItemAccess itemAccess;

	public List<InterfaceTestItemResponse> getItems() {
		return itemAccess.findRecentUsable(tenant()).stream()
			.map(this::mapToResponse)
			.toList();
	}

	public InterfaceTestItemResponse getItem(String itemId) {
		return mapToResponse(findActiveItem(itemId));
	}

	@Transactional
	public InterfaceTestItemResponse createItem(InterfaceTestItemRequest request) {
		requireName(request);

		SnIfTestItemModel item = SnIfTestItemModel.builder()
			.itemNm(request.getName())
			.content(request.getContent())
			.status(request.getStatus() != null ? request.getStatus() : STAT_NEW)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(tenant())
			.traceId("CREATE-IF-TEST-ITEM")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateIfTestItem")
			.prevEvntNm("None")
			.build();

		itemAccess.saveAndFlush(item);
		return mapToResponse(item);
	}

	/** PUT: 전체 교체. 보내지 않은 content 는 비우고 status 는 기본값으로 돌린다. */
	@Transactional
	public InterfaceTestItemResponse replaceItem(String itemId, InterfaceTestItemRequest request) {
		requireName(request);
		SnIfTestItemModel item = findActiveItem(itemId);

		item.setItemNm(request.getName());
		item.setContent(request.getContent());
		item.setStatus(request.getStatus() != null ? request.getStatus() : STAT_NEW);

		item.setPrevEvntNm(item.getEvtNm());
		item.setEvtNm("ReplaceIfTestItem");
		itemAccess.saveAndFlush(item);
		return mapToResponse(item);
	}

	/** PATCH: 보낸 필드만 변경 */
	@Transactional
	public InterfaceTestItemResponse patchItem(String itemId, InterfaceTestItemRequest request) {
		SnIfTestItemModel item = findActiveItem(itemId);

		if (request.getName() != null) {
			if (request.getName().isBlank()) {
				throw new IllegalArgumentException("name 은 비울 수 없습니다.");
			}
			item.setItemNm(request.getName());
		}
		if (request.getContent() != null)
			item.setContent(request.getContent());
		if (request.getStatus() != null)
			item.setStatus(request.getStatus());

		item.setPrevEvntNm(item.getEvtNm());
		item.setEvtNm("PatchIfTestItem");
		itemAccess.saveAndFlush(item);
		return mapToResponse(item);
	}

	/** Soft delete. 이미 삭제된 항목은 404 */
	@Transactional
	public void deleteItem(String itemId) {
		SnIfTestItemModel item = findActiveItem(itemId);
		item.setUseStatCd(UseStatCd.Delete);
		item.setPrevEvntNm(item.getEvtNm());
		item.setEvtNm("DeleteIfTestItem");
		itemAccess.saveAndFlush(item);
	}

	private SnIfTestItemModel findActiveItem(String itemId) {
		return itemAccess.findUsable(itemId, tenant())
			.orElseThrow(() -> new EntityNotFoundException("인터페이스 테스트 항목을 찾을 수 없습니다: " + itemId));
	}

	private static void requireName(InterfaceTestItemRequest request) {
		if (request.getName() == null || request.getName().isBlank()) {
			throw new IllegalArgumentException("name 은 필수입니다.");
		}
	}

	private static String tenant() {
		return ApplicationProperties.getApplicationTenant();
	}

	private InterfaceTestItemResponse mapToResponse(SnIfTestItemModel model) {
		return InterfaceTestItemResponse.builder()
			.id(model.getObjId())
			.name(model.getItemNm())
			.content(model.getContent())
			.status(model.getStatus())
			.lastEvent(model.getEvtNm())
			.createdAt(model.getCreatedAt())
			.modifiedAt(model.getModifiedAt())
			.build();
	}
}
