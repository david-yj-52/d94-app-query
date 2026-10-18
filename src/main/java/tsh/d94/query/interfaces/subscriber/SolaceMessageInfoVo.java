package tsh.d94.app.server.interfaces.subscriber;

import com.solacesystems.jcsmp.BytesXMLMessage;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class SolaceMessageInfoVo implements MessageInfoVo {

	BytesXMLMessage msgObject;
	String responseTopic;
	String selectorKey;

}
