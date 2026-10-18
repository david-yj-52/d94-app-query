package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CfdResponse {

	private List<LocalDate> dates;
	private List<String> statuses;
	private Map<String, List<Integer>> data;

}
