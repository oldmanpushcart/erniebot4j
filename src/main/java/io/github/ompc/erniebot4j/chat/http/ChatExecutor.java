package io.github.ompc.erniebot4j.chat.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.ompc.erniebot4j.TokenRefresher;
import io.github.ompc.erniebot4j.chat.ChatRequest;
import io.github.ompc.erniebot4j.chat.ChatResponse;
import io.github.ompc.erniebot4j.executor.Sentence;
import io.github.ompc.erniebot4j.executor.http.HttpExecutor;
import io.github.ompc.erniebot4j.executor.http.ResponseBodyHandler;
import io.github.ompc.erniebot4j.util.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static java.util.Objects.isNull;

public class ChatExecutor implements HttpExecutor<ChatRequest, ChatResponse> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final ObjectMapper mapper = JacksonUtils.mapper()
            .registerModule(new SimpleModule() {{
                addSerializer(ChatRequest.class, new ChatRequestJsonSerializer());
                addDeserializer(ChatResponse.class, new ChatResponseJsonDeserializer());
            }});
    private final TokenRefresher refresher;
    private final Executor executor;

    public ChatExecutor(TokenRefresher refresher, Executor executor) {
        this.refresher = refresher;
        this.executor = executor;
    }

    @Override
    public String toString() {
        return "erniebot://chat";
    }

    @Override
    public CompletableFuture<ChatResponse> execute(HttpClient http, ChatRequest request, Consumer<ChatResponse> consumer) {
        final var merged = new Merged();
        return execute(merged, http, request, consumer)
                .thenApply(response -> new ChatResponse(
                        response.id(),
                        response.type(),
                        response.timestamp(),
                        merged.usage(), response.sentence(),
                        response.call(),
                        merged.search()
                ));
    }

    CompletableFuture<ChatResponse> execute(Merged merged, HttpClient http, ChatRequest request, Consumer<ChatResponse> consumer) {
        return refresher.refresh(http).thenCompose(token -> {

            // 构建HTTP请求体
            final var httpRequestBodyJson = JacksonUtils.toJson(mapper, request);
            logger.debug("{}/{}/http => {}", this, request.model().name(), httpRequestBodyJson);

            // 构建HTTP请求
            final var builder = HttpRequest.newBuilder()
                    .header("Content-Type", "application/json")
                    .uri(URI.create("%s?access_token=%s".formatted(request.model().remote(), token)))
                    .POST(HttpRequest.BodyPublishers.ofString(httpRequestBodyJson));

            Optional.ofNullable(request.timeout()).ifPresent(builder::timeout);
            final var httpRequest = builder.build();

            // 构建请求处理器
            final var responseBodyHandler = new ResponseBodyHandler.Builder<ChatResponse>()

                    // 将json转为Response
                    .convertor(json -> {

                        logger.debug("{}/{}/http <= {}", this, request.model().name(), json);

                        // 转为Node处理
                        final var node = JacksonUtils.toResponseNode(mapper, json);

                        // 检查是否安全
                        if (node.has("need_clear_history") && node.get("need_clear_history").asBoolean()) {
                            throw new RuntimeException("response is not safe! ban=%s".formatted(
                                    node.get("ban_round").asInt()
                            ));
                        }

                        // 返回应答对象
                        return JacksonUtils.toObject(mapper, ChatResponse.class, node);
                    })

                    // 消费Response
                    .consumer(consumer)

                    // 合并Response
                    .accumulator((left, right) -> {
                        if (left == right || isNull(right)) {
                            return left;
                        } else if (isNull(left)) {
                            return right;
                        } else {
                            return new ChatResponse(
                                    left.id(),
                                    left.type(),
                                    left.timestamp(),
                                    right.usage(),
                                    new Sentence(
                                            left.sentence().index(),
                                            left.sentence().isLast() || right.sentence().isLast(),
                                            left.sentence().content() + right.sentence().content()
                                    ),
                                    left.call(),
                                    right.search()
                            );
                        }
                    })
                    .build();

            // 执行HTTP
            return http.sendAsync(httpRequest, responseBodyHandler)
                    .thenApplyAsync(HttpResponse::body, executor)
                    .thenCompose(new ChatResponseHandler(merged, this, http, request, consumer));
        });

    }

}
