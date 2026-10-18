package tsh.d94.app.server.interfaces.controller;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tsh.d94.app.server.apService.cira.cql.CqlParseException;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CiraException.class)
	public ResponseEntity<ApiResponse<Void>> handleCiraException(CiraException e) {
		log.warn("Cira domain error [{}]: {}", e.getErrorCode().getCode(), e.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
	}

	@ExceptionHandler(CqlParseException.class)
	public ResponseEntity<ApiResponse<Void>> handleCqlParseException(CqlParseException e) {
		log.warn("CQL parse error at position {}: {}", e.getPosition(), e.getMessage());
		return ResponseEntity.badRequest()
			.body(ApiResponse.error("CQL_PARSE_ERROR", e.getMessage()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
		log.warn("Authentication failed: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ApiResponse.error("AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."));
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
		log.warn("Not found: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiResponse.error("NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
		log.warn("Bad request: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error("BAD_REQUEST", e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Internal server error", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.error("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		String errorMessage = e.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.collect(Collectors.joining(", "));

		log.warn("Validation failed: {}", errorMessage);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error("VALIDATION_ERROR", errorMessage));
	}
}
