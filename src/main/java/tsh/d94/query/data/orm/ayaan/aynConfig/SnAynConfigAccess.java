package tsh.d94.app.server.data.orm.ayaan.aynConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 자연키 테이블이라 AbstractCrudService(BaseModel 전용)를 쓰지 않는다. 메서드는 사용처(내부 API)가 생길 때 추가. */
@Service
public class SnAynConfigAccess {

	@Autowired
	SnAynConfigRepo repo;

}
