package tsh.d94.app.server.data.orm.cira.ciraIssueLink;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraIssueLinkRepo extends BaseJpaRepository<SnCiraIssueLinkModel, String> {
}
