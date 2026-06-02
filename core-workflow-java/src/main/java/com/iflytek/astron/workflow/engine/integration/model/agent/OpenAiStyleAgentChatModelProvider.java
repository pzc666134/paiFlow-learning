package com.iflytek.astron.workflow.engine.integration.model.agent;

import com.iflytek.astron.workflow.engine.constants.ModelTypeEnum;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI-compatible chat model provider for agent nodes.
 */
@Slf4j
@Component
public class OpenAiStyleAgentChatModelProvider implements AgentChatModelProvider {

    private static final Pattern API_URL_PATTERN = Pattern.compile("^(https?://[^/]+)(/.*)?$");

    @Override
    public ModelTypeEnum getModelType() {
        return ModelTypeEnum.OPENAI;
    }

    @Override
    public ChatModel build(Map<String, Object> nodeParam) {
        String apiKey = requireParam(nodeParam, "apiKey");
        String apiUrl = requireParam(nodeParam, "url");
        String model = requireParam(nodeParam, "domain");

        OpenAiApi openAiApi = initClient(apiKey, apiUrl);
        OpenAiChatOptions options = buildChatOptions(nodeParam, model);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    private OpenAiApi initClient(String apiKey, String apiUrl) {
        Matcher matcher = API_URL_PATTERN.matcher(apiUrl);

        String baseUrl;
        String basePath = null;
        if (matcher.matches()) {
            baseUrl = matcher.group(1);
            basePath = matcher.group(2);
        } else {
            baseUrl = apiUrl;
        }

        if (apiUrl.contains("dashscope.aliyuncs.com")) {
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";
            basePath = null;
        }

        OpenAiApi.Builder builder = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl);
        if (StringUtils.isNotBlank(basePath)) {
            builder.completionsPath(basePath);
        }
        log.info("AI agent OpenAI style API URL: {} - {}", baseUrl, basePath);
        return builder.build();
    }

    private OpenAiChatOptions buildChatOptions(Map<String, Object> nodeParam, String model) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .streamUsage(true);

        Integer maxTokens = getInteger(nodeParam, "maxTokens");
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }

        Object extraParamsObj = nodeParam.get("extraParams");
        if (extraParamsObj instanceof Map<?, ?> extraParams) {
            get(extraParams, "temperature", Double::parseDouble).ifPresent(builder::temperature);
            get(extraParams, "topP", Double::parseDouble).ifPresent(builder::topP);
            get(extraParams, "presencePenalty", Double::parseDouble).ifPresent(builder::presencePenalty);
            get(extraParams, "frequencyPenalty", Double::parseDouble).ifPresent(builder::frequencyPenalty);
            get(extraParams, "maxTokens", Integer::parseInt).ifPresent(builder::maxTokens);
            get(extraParams, "n", Integer::parseInt).ifPresent(builder::N);
        }

        return builder.build();
    }

    private String requireParam(Map<String, Object> nodeParam, String key) {
        String value = getParam(nodeParam, key);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Missing '" + key + "' in AI agent node parameters");
        }
        return value;
    }

    private String getParam(Map<String, Object> nodeParam, String key) {
        Object value = nodeParam.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer getInteger(Map<String, Object> nodeParam, String key) {
        Object value = nodeParam.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.warn("Invalid integer config: {}={}", key, value);
            return null;
        }
    }

    private <T> Optional<T> get(Map<?, ?> map, String key, Function<String, T> parse) {
        Object value = map.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse.apply(String.valueOf(value)));
        } catch (Exception e) {
            log.warn("Error parsing AI agent option: {}={}", key, value);
            return Optional.empty();
        }
    }
}
