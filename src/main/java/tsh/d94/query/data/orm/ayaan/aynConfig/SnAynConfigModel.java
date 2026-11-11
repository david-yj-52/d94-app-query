package tsh.d94.app.server.data.orm.ayaan.aynConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;

@Entity
@Table(name = ApTableName.SN_AYN_CONFIG)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynConfigModel extends AynRefBase {

	@Id
	@Column(name = "CONFIG_KEY", length = 100)
	private String configKey;

	@Column(name = "CONFIG_VALUE", length = 500)
	private String configValue;

}
