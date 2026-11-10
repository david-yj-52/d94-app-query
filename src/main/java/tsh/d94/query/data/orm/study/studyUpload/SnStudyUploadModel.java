package tsh.d94.app.server.data.orm.study.studyUpload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** ponytail: @Audited 미사용 — 블록 수만 행의 _HIST 기록이 필요 없다. 삭제는 물리 삭제. */
@Entity
@Table(name = ApTableName.SN_STUDY_UPLOAD)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnStudyUploadModel extends BaseModel {

	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	@Column(name = "FILE_NM", length = 500, nullable = false)
	private String fileNm;

	@Column(name = "FILE_SIZE", nullable = false)
	private Long fileSize;

	@Column(name = "PART_COUNT", nullable = false)
	private Integer partCount;

}
