package io.github.oldmanpushcart.test.qianfan4j.chat.function;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.oldmanpushcart.qianfan4j.chat.function.ChatFn;
import io.github.oldmanpushcart.qianfan4j.chat.function.ChatFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ChatFn(name = "query_score", description = "query student's scores", examples = {
        @ChatFn.Example(
                question = "查询张三、李四的数学成绩",
                thoughts = "用户需要查询张三、李四、王五的数学成绩，但函数一次只能查询一个学生，所以我们先查询张三的成绩，然后再分别查询李四和王五的数学成绩",
                arguments = """
                        {
                             "name": "张三",
                             "subjects": [
                                 "MATH"
                             ]
                         }
                        """
        ),
        @ChatFn.Example(
                question = "查询李四的数学和语文成绩",
                thoughts = "用户需要查询李四的数学和语文成绩，函数一次可以查询一个学生的多个成绩",
                arguments = """
                        {
                             "name": "李四",
                             "subjects": [
                                 "MATH",
                                 "CHINESE"
                             ]
                         }
                        """
        )
})
public class QueryScoreFunction implements ChatFunction<QueryScoreFunction.Request, Result<List<QueryScoreFunction.Score>>> {

    private final Map<String, List<Score>> studentScoreMap = new HashMap<>() {{
        put("张三", List.of(
                new Score("张三", Subject.CHINESE, 90),
                new Score("张三", Subject.MATH, 80),
                new Score("张三", Subject.ENGLISH, 70)
        ));
        put("李四", List.of(
                new Score("李四", Subject.CHINESE, 80),
                new Score("李四", Subject.MATH, 70),
                new Score("李四", Subject.ENGLISH, 60)
        ));
        put("王五", List.of(
                new Score("王五", Subject.CHINESE, 70),
                new Score("王五", Subject.MATH, 60),
                new Score("王五", Subject.ENGLISH, 50)
        ));
    }};

    @Override
    public CompletableFuture<Result<List<Score>>> call(Request request) {

        if (!studentScoreMap.containsKey(request.name())) {
            return CompletableFuture.completedFuture(new Result<>(
                    false,
                    "学生不存在",
                    null
            ));
        }

        return CompletableFuture.completedFuture(new Result<>(
                true,
                "查询成功",
                studentScoreMap.get(request.name()).stream().filter(score -> {
                    if (null == request.subjects()) {
                        return true;
                    }
                    for (final var subject : request.subjects()) {
                        if (subject == score.subject()) {
                            return true;
                        }
                    }
                    return false;
                }).collect(Collectors.toList())
        ));
    }

    @JsonClassDescription("request to query student scores")
    public record Request(
            @JsonProperty(required = true)
            @JsonPropertyDescription("the student name to query, example: \"张三\"")
            String name,

            @JsonProperty(required = true)
            @JsonPropertyDescription("the subjects to query, example: [\"MATH\", \"CHINESE\"]")
            Subject... subjects
    ) {

    }

    @JsonClassDescription("student scores")
    public record Score(

            @JsonPropertyDescription("student name")
            String name,

            @JsonPropertyDescription("subject items")
            Subject subject,

            @JsonPropertyDescription("score value")
            float value
    ) {

    }

    @JsonClassDescription("subject items")
    public enum Subject {
        CHINESE,
        MATH,
        ENGLISH
    }

}