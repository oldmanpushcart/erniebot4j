package io.github.ompc.erniebot4j.embedding;

import io.github.ompc.erniebot4j.executor.Response;
import io.github.ompc.erniebot4j.executor.Usage;

public record EmbeddingResponse(
        String id,
        String type,
        long timestamp,
        Usage usage,
        Embedding[] embeddings
) implements Response {

}
