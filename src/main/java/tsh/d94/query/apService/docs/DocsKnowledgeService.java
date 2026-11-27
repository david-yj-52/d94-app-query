package tsh.d94.app.server.apService.docs;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.config.DocsRagProperties;
import tsh.d94.app.server.data.orm.docs.docsNote.SnDocsNoteModel;

/** 사용자 요청으로 노트를 Elasticsearch 지식 청크로 색인하고, 접근 가능한 vault만 검색한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocsKnowledgeService {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final int VECTOR_DIMS = 768;
	private static final int MAX_RESULTS = 20;

	private final DocsRagProperties props;
	private final DocsNoteService notes;
	private final DocsVaultService vaults;

	public KnowledgeResult index(String noteId) {
		SnDocsNoteModel note = notes.requireOwned(noteId);
		List<Chunk> chunks = chunk(note.getTitle(), note.getContent());
		if (chunks.isEmpty()) throw new IllegalArgumentException("내용이 있는 노트만 지식화할 수 있습니다.");
		List<List<Double>> vectors = embed(chunks.stream().map(Chunk::embeddingText).toList());
		ensureIndex();
		StringBuilder bulk = new StringBuilder();
		for (int i = 0; i < chunks.size(); i++) {
			Chunk chunk = chunks.get(i);
			String id = Base64.getUrlEncoder().withoutPadding().encodeToString((noteId + ":" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			bulk.append(json(Map.of("index", Map.of("_index", props.getIndex(), "_id", id)))).append('\n');
			bulk.append(json(Map.of(
				"note_id", noteId, "vault_id", note.getVaultId(), "title", note.getTitle(),
				"kind", note.getKind(), "stream", note.getStream() == null ? "" : note.getStream(),
				"version", note.getVersion(), "chunk_index", i, "content", chunk.content(),
				"embedding_text", chunk.embeddingText(), "content_vector", vectors.get(i)))).append('\n');
		}
		RestClient es = elasticsearch();
		String bulkResponse = es.post().uri("/_bulk?refresh=wait_for").contentType(MediaType.parseMediaType("application/x-ndjson"))
			.body(bulk.toString()).retrieve().body(String.class);
		try {
			if (JSON.readTree(bulkResponse).path("errors").asBoolean()) throw new IllegalStateException("Elasticsearch 청크 색인에 실패했습니다.");
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Elasticsearch 응답을 읽을 수 없습니다.", e);
		}
		es.post().uri("/{index}/_delete_by_query", props.getIndex())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("query", Map.of("bool", Map.of("filter", List.of(
				Map.of("term", Map.of("note_id", noteId)),
				Map.of("range", Map.of("version", Map.of("lt", note.getVersion()))))))))
			.retrieve().toBodilessEntity();
		es.post().uri("/{index}/_delete_by_query", props.getIndex())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("query", Map.of("bool", Map.of("filter", List.of(
				Map.of("term", Map.of("note_id", noteId)),
				Map.of("term", Map.of("version", note.getVersion())),
				Map.of("range", Map.of("chunk_index", Map.of("gte", chunks.size()))))))))
			.retrieve().toBodilessEntity();
		return new KnowledgeResult(noteId, note.getVersion(), chunks.size());
	}

	/** 노트 삭제 시 RAG 복사본도 best-effort 로 제거한다. ES 장애가 일반 Docs 삭제를 막지는 않는다. */
	public void forget(String noteId) {
		notes.requireOwned(noteId);
		purge(noteId);
	}

	private void purge(String noteId) {
		try {
			elasticsearch().post().uri("/{index}/_delete_by_query", props.getIndex())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("query", Map.of("term", Map.of("note_id", noteId))))
				.retrieve().toBodilessEntity();
		} catch (HttpClientErrorException.NotFound ignored) {
			// The index has not been created yet.
		} catch (Exception e) {
			log.warn("Could not remove deleted Docs note from the RAG index: {}", noteId);
		}
	}

	public List<KnowledgeHit> search(String query, String vaultId) {
		if (query == null || query.isBlank()) throw new IllegalArgumentException("검색어를 입력해 주세요.");
		if (query.length() > 2_000) throw new IllegalArgumentException("검색어는 2,000자 이하여야 합니다.");
		List<String> vaultIds = vaults.scope(vaultId);
		if (vaultIds.isEmpty()) return List.of();
		List<Double> vector = embed(List.of(query)).getFirst();
		ensureIndex();
		Map<String, Object> request = Map.of(
			"size", MAX_RESULTS,
			"query", Map.of("bool", Map.of(
				"filter", List.of(Map.of("terms", Map.of("vault_id", vaultIds))),
				"should", List.of(
					Map.of("match", Map.of("content", Map.of("query", query, "boost", 1.5))),
					Map.of("knn", Map.of("field", "content_vector", "query_vector", vector,
						"k", MAX_RESULTS, "num_candidates", 100))),
				"minimum_should_match", 1)));
		JsonNode response = elasticsearch().post().uri("/{index}/_search", props.getIndex())
			.contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
		List<KnowledgeHit> hits = new ArrayList<>();
		for (JsonNode hit : response.path("hits").path("hits")) {
			JsonNode source = hit.path("_source");
			String noteId = source.path("note_id").asText();
			try {
				SnDocsNoteModel current = notes.requireOwned(noteId);
				if (current.getVersion() != source.path("version").asInt()) {
					deleteHit(hit.path("_id").asText());
					continue;
				}
				hits.add(new KnowledgeHit(noteId, source.path("vault_id").asText(), source.path("title").asText(),
					source.path("content").asText(), source.path("chunk_index").asInt(),
					hit.path("_score").asDouble()));
			} catch (DocsException ignored) {
				purge(noteId);
				// stale/deleted or no longer authorized notes never leave the API
			}
		}
		return hits;
	}

	private void deleteHit(String id) {
		if (id.isBlank()) return;
		try {
			elasticsearch().delete().uri("/{index}/_doc/{id}", props.getIndex(), id).retrieve().toBodilessEntity();
		} catch (Exception ignored) {
			// A best-effort cleanup; authorization and version checks above still protect returned data.
		}
	}

	private List<List<Double>> embed(List<String> input) {
		JsonNode response = RestClient.create(props.getOllamaUrl()).post().uri("/api/embed")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("model", props.getEmbeddingModel(), "input", input))
			.retrieve().body(JsonNode.class);
		List<List<Double>> result = new ArrayList<>();
		for (JsonNode embedding : response.path("embeddings")) {
			List<Double> vector = new ArrayList<>();
			embedding.forEach(v -> vector.add(v.asDouble()));
			if (vector.size() != VECTOR_DIMS) throw new IllegalStateException("Embedding 차원이 docs_chunks 매핑과 다릅니다.");
			result.add(vector);
		}
		if (result.size() != input.size()) throw new IllegalStateException("Embedding 응답 개수가 요청과 다릅니다.");
		return result;
	}

	private void ensureIndex() {
		RestClient es = elasticsearch();
		Map<String, Object> mapping = Map.of("mappings", Map.of("properties", Map.of(
			"note_id", Map.of("type", "keyword"), "vault_id", Map.of("type", "keyword"),
			"title", Map.of("type", "text"), "kind", Map.of("type", "keyword"),
			"stream", Map.of("type", "keyword"), "version", Map.of("type", "integer"),
			"chunk_index", Map.of("type", "integer"), "content", Map.of("type", "text"),
			"embedding_text", Map.of("type", "text"),
			"content_vector", Map.of("type", "dense_vector", "dims", VECTOR_DIMS, "index", true, "similarity", "cosine"))));
		try {
			es.put().uri("/{index}", props.getIndex()).contentType(MediaType.APPLICATION_JSON)
				.body(mapping).retrieve().toBodilessEntity();
		} catch (HttpClientErrorException.BadRequest e) {
			if (!e.getResponseBodyAsString().contains("resource_already_exists_exception")) throw e;
		}
	}

	private String json(Object value) {
		try {
			return JSON.writeValueAsString(value);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("JSON을 직렬화할 수 없습니다.", e);
		}
	}

	private RestClient elasticsearch() {
		return RestClient.builder().baseUrl(props.getElasticsearchUrl())
			.defaultHeaders(h -> h.setBasicAuth(props.getElasticsearchUsername(), props.getElasticsearchPassword()))
			.build();
	}

	private List<Chunk> chunk(String title, String markdown) {
		List<Chunk> result = new ArrayList<>();
		String[] paragraphs = markdown.split("\\n\\s*\\n");
		String heading = "";
		StringBuilder current = new StringBuilder();
		for (String paragraph : paragraphs) {
			String text = paragraph.strip();
			if (text.isEmpty()) continue;
			if (text.matches("(?s)^#{1,6}\\s+.*")) heading = text.replaceFirst("^#{1,6}\\s+", "").strip();
			if (current.length() > 0 && current.length() + text.length() > 1_200) {
				result.add(new Chunk(current.toString(), title + " > " + heading + "\\n" + current));
				current.setLength(0);
			}
			if (text.length() > 1_200) {
				for (int start = 0; start < text.length(); start += 1_200) {
					String part = text.substring(start, Math.min(start + 1_200, text.length()));
					result.add(new Chunk(part, title + " > " + heading + "\\n" + part));
				}
			} else {
				if (current.length() > 0) current.append("\\n\\n");
				current.append(text);
			}
		}
		if (current.length() > 0) result.add(new Chunk(current.toString(), title + " > " + heading + "\\n" + current));
		return result;
	}

	public record KnowledgeResult(String noteId, int version, int chunks) {}
	public record KnowledgeHit(String noteId, String vaultId, String title, String content, int chunkIndex, double score) {}
	private record Chunk(String content, String embeddingText) {}
}
