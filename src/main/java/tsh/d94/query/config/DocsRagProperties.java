package tsh.d94.app.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "docs.rag")
public class DocsRagProperties {
	private String elasticsearchUrl = "http://localhost:9200";
	private String elasticsearchUsername = "docs_rag_app";
	private String elasticsearchPassword = "";
	private String ollamaUrl = "http://localhost:11434";
	private String embeddingModel = "nomic-embed-text";
	private String index = "docs_chunks";
}
