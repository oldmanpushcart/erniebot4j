package io.github.ompc.erniebot4j.test.chat;

import io.github.ompc.erniebot4j.chat.ChatModel;
import io.github.ompc.erniebot4j.chat.ChatOptions;
import io.github.ompc.erniebot4j.chat.ChatRequest;
import io.github.ompc.erniebot4j.chat.message.Message;
import io.github.ompc.erniebot4j.exception.ErnieBotResponseNotSafeException;
import io.github.ompc.erniebot4j.test.LoadingProperties;
import io.github.ompc.erniebot4j.test.chat.function.ComputeAvgScoreFunction;
import io.github.ompc.erniebot4j.test.chat.function.QueryScoreFunction;
import org.junit.Assert;
import org.junit.Test;

public class ChatExecutorTestCase implements LoadingProperties {

    @Test
    public void test$chain$prohibited() {

        final var request = new ChatRequest.Builder()
                .model(ChatModel.ERNIEBOT_8K)
                .message(Message.human("我不支持台湾独立!"))
                .option(ChatOptions.IS_STREAM, true)
                .option(ChatOptions.IS_ENABLE_SEARCH, true)
                .option(ChatOptions.IS_ENABLE_CITATION, true)
                .option(ChatOptions.TEMPERATURE, 0.8f)
                .build();

        final var future = client.chat(request).async();
        try {
            future.get();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getCause() instanceof ErnieBotResponseNotSafeException);
            final var cause = (ErnieBotResponseNotSafeException) ex.getCause();
            Assert.assertEquals(-1, cause.getBan());
            Assert.assertFalse(cause.getContent().isBlank());
        }

        Assert.assertTrue(future.isCompletedExceptionally());

    }

    @Test
    public void test$chain$function$multi() {

        final var response = client
                .chat(new ChatRequest.Builder()
                        .model(ChatModel.ERNIEBOT_8K)
                        .message(Message.human("计算张三、李四、王五的语文平均分"))
                        .function(new QueryScoreFunction())
                        .function(new ComputeAvgScoreFunction())
                        .option(ChatOptions.IS_STREAM, true)
                        .option(ChatOptions.IS_ENABLE_SEARCH, false)
                        .option(ChatOptions.TEMPERATURE, 0.01f)
                        .build()
                )
                .async()
                .join();

        System.out.println(response);

        final var check = client
                .chat(new ChatRequest.Builder()
                        .model(ChatModel.ERNIEBOT_8K)
                        .message(Message.human("计算张三、李四、王五的语文平均分"))
                        .message(Message.bot(response.sentence().content()))
                        .message(Message.human("张三、李四、王五三人的语文平均分是不是80分，如果是，请回答YES，否则回答NO"))
                        .option(ChatOptions.IS_STREAM, true)
                        .option(ChatOptions.IS_ENABLE_SEARCH, false)
                        .option(ChatOptions.TEMPERATURE, 0.01f)
                        .build()
                )
                .async()
                .join();

        Assert.assertTrue(check.sentence().content().contains("YES"));
        System.out.println(check);

    }

}
