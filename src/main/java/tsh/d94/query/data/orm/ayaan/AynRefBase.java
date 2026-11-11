package tsh.d94.app.server.data.orm.ayaan;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** 자연키 PK 테이블(CONFIG/TOOL/WORKSPACE/TOOL_QUOTA)용 공통 컬럼. BaseModel 은 objId 를 PK 로 고정해 재사용할 수 없다. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class AynRefBase {

	@Column(name = "SRV_ID", length = 100) private String srvId;
	@Column(name = "TENANT", length = 100) private String tenant;
	@Column(name = "TRACE_ID", length = 100) private String traceId;
	@Column(name = "EVNT_NM", length = 100) private String evntNm;
	@Column(name = "PREV_EVNT_NM", length = 100) private String prevEvntNm;
	@Column(name = "ACT_CD", length = 100) private String actCd;
	@Column(name = "ACT_CM", length = 255) private String actCm;
	@Column(name = "USE_STAT_CD", length = 20) private String useStatCd;
	@CreatedDate @Column(name = "CREATED_AT", updatable = false) private LocalDateTime createdAt;
	@LastModifiedDate @Column(name = "MODIFIED_AT") private LocalDateTime modifiedAt;
	@CreatedBy @Column(name = "CREATED_BY", length = 100, updatable = false) private String createdBy;
	@LastModifiedBy @Column(name = "MODIFIED_BY", length = 100) private String modifiedBy;

}
