package org.myragent.ai.chat;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.myragent.ai.config.AIModelProperties;
import org.myragent.ai.convention.ChatMessage;
import org.myragent.ai.convention.ChatRequest;
import org.myragent.ai.enums.ModelCapability;
import org.myragent.ai.http.*;
import org.myragent.ai.model.ModelRef;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

/**
 * OpenAI 兼容协议 ChatClient 抽象基类
 */
@Slf4j
public abstract class AbstractOpenAIStyleChatClient implements ChatClient {

    protected Gson gson = new Gson();
    @Autowired
    private OkHttpClient syncHttpClient;
//    @Autowired
//    private OkHttpClient streamingHttpClient;
//    @Autowired
//    private Executor modelStreamExecutor;
//    @Autowired
//    private RagStreamTraceSupport streamTraceSupport;

    // ==================== 子类钩子方法 ====================

    /**
     * 流式调用时是否启用 reasoning_content 解析，默认根据请求中的 thinking 标志决定
     */
//    protected boolean isReasoningEnabledForStream(ChatRequest request) {
//        return Boolean.TRUE.equals(request.getThinking());
//    }

    /**
     * 子类可覆写此方法添加提供商特有的请求体字段
     * 默认实现：当请求开启 thinking 时添加 enable_thinking 字段
     */
    protected void customizeRequestBody(JsonObject body, ChatRequest request) {
        if (Boolean.TRUE.equals(request.getEnableThinking())) {
            body.addProperty("enable_thinking", true);
        }
    }

    /**
     * 是否要求提供商配置 API Key
     */
    protected boolean requiresApiKey() {
        return true;
    }

    // ==================== 模板方法：同步调用 ====================

    protected String doChat(ChatRequest request, ModelRef model) {
        // 1. 通过 provider 验证并提取当前 model 名称
        AIModelProperties.ProviderConfig provider = HttpResponseHelper.checkAndReturnProvider(model, provider());

        // 2. requiresApiKey 是一个可以被override的撰写逻辑，根据实例自定义，默认为true，一般是为了ollama服务的
        if (requiresApiKey()) {
            HttpResponseHelper.checkApiKey(provider, provider());
        }

        JsonObject reqBody = buildRequestBody(request, model, false);
        // 这里 newAuthorizedRequest 会根据 provider 和 model 信息写好一个请求头
        Request requestHttp = newAuthorizedRequest(provider, model)
                // 在这一步通过链式调用，继续在这个request中写入 requestBody
                .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
                .build();

        JsonObject respJson;
        try (Response response = syncHttpClient.newCall(requestHttp).execute()) {
            if (!response.isSuccessful()) {
                String body = HttpResponseHelper.readBody(response.body());
                log.warn("{} 同步请求失败: status={}, body={}", provider(), response.code(), body);
                throw new ModelClientException(
                        provider() + " 同步请求失败: HTTP " + response.code(),
                        ModelClientErrorType.fromHttpStatus(response.code()),
                        response.code()
                );
            }
            respJson = HttpResponseHelper.parseJson(response.body(), provider());
        } catch (IOException e) {
            throw new ModelClientException(
                    provider() + " 同步请求失败: " + e.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR, null, e);
        }

        return extractChatContent(respJson);
    }

    // ==================== 模板方法：流式调用 ====================

//    protected StreamCancellationHandle doStreamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
//        AIModelProperties.ProviderConfig provider = HttpResponseHelper.requireProvider(target, provider());
//        if (requiresApiKey()) {
//            HttpResponseHelper.requireApiKey(provider, provider());
//        }
//
//        JsonObject reqBody = buildRequestBody(request, target, true);
//        Request streamRequest = newAuthorizedRequest(provider, target)
//                .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
//                .addHeader("Accept", "text/event-stream")
//                .build();
//
//        Call call = streamingHttpClient.newCall(streamRequest);
//        boolean reasoningEnabled = isReasoningEnabledForStream(request);
//
//        // 在调用线程开 stream span，使后续 first-packet 子节点能正确归属父节点；
//        // 该 span 由 SSE 终态（onComplete / onError）或 cancel 时收尾，记录真实端到端耗时
//        StreamSpan span = streamTraceSupport.beginStreamNode(provider() + "-stream-chat", "LLM_PROVIDER");
//        StreamSpanCallback wrappedCallback;
//        try {
//            wrappedCallback = new StreamSpanCallback(callback, span);
//            StreamCancellationHandle inner = StreamAsyncExecutor.submit(
//                    modelStreamExecutor,
//                    call,
//                    wrappedCallback,
//                    cancelled -> doStream(call, wrappedCallback, cancelled, reasoningEnabled)
//            );
//            return () -> {
//                try {
//                    inner.cancel();
//                } finally {
//                    wrappedCallback.onCancel();
//                }
//            };
//        } finally {
//            // 同步部分结束：把节点从当前线程的 NODE_STACK 弹出，避免污染兄弟节点的父节点链
//            span.detach();
//        }
//    }
//
//    private void doStream(Call call, StreamCallback callback, AtomicBoolean cancelled, boolean reasoningEnabled) {
//        try (Response response = call.execute()) {
//            if (!response.isSuccessful()) {
//                String body = HttpResponseHelper.readBody(response.body());
//                throw new ModelClientException(
//                        provider() + " 流式请求失败: HTTP " + response.code() + " - " + body,
//                        ModelClientErrorType.fromHttpStatus(response.code()),
//                        response.code()
//                );
//            }
//            ResponseBody body = response.body();
//            if (body == null) {
//                throw new ModelClientException(provider() + " 流式响应为空", ModelClientErrorType.INVALID_RESPONSE, null);
//            }
//            BufferedSource source = body.source();
//            boolean completed = false;
//            while (!cancelled.get()) {
//                String line = source.readUtf8Line();
//                if (line == null) {
//                    break;
//                }
//                if (line.isBlank()) {
//                    continue;
//                }
//                try {
//                    OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(line, gson, reasoningEnabled);
//                    if (event.hasReasoning()) {
//                        callback.onThinking(event.reasoning());
//                    }
//                    if (event.hasContent()) {
//                        callback.onContent(event.content());
//                    }
//                    if (event.completed()) {
//                        callback.onComplete();
//                        completed = true;
//                        break;
//                    }
//                } catch (Exception parseEx) {
//                    log.warn("{} 流式响应解析失败: line={}", provider(), line, parseEx);
//                }
//            }
//            if (cancelled.get()) {
//                log.info("{} 流式响应已被取消", provider());
//                return;
//            }
//            if (!completed) {
//                throw new ModelClientException(provider() + " 流式响应异常结束", ModelClientErrorType.INVALID_RESPONSE, null);
//            }
//        } catch (Exception e) {
//            if (!cancelled.get()) {
//                callback.onError(e);
//            } else {
//                log.info("{} 流式响应取消期间产生异常（可忽略）: {}", provider(), e.getMessage());
//            }
//        }
//    }

    // ==================== 公共构建方法 ====================

    protected JsonObject buildRequestBody(ChatRequest request, ModelRef target, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", HttpResponseHelper.checkAndReturnModel(target, provider()));
        if (stream) {
            body.addProperty("stream", true);
        }

        body.add("messages", buildMessages(request));

        if (request.getTemperature() != null) {
            body.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.addProperty("top_p", request.getTopP());
        }
        if (request.getTopK() != null) {
            body.addProperty("top_k", request.getTopK());
        }
        if (request.getMaxTokens() != null) {
            body.addProperty("max_tokens", request.getMaxTokens());
        }

        customizeRequestBody(body, request);
        return body;
    }

    private JsonArray buildMessages(ChatRequest request) {
        JsonArray arr = new JsonArray();
        List<ChatMessage> messages = request.getMessages();
        if (CollUtil.isNotEmpty(messages)) {
            for (ChatMessage m : messages) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", toOpenAiRole(m.getRole()));
                msg.addProperty("content", m.getContent());
                arr.add(msg);
            }
        }
        return arr;
    }

    private String toOpenAiRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private Request.Builder newAuthorizedRequest(AIModelProperties.ProviderConfig provider, ModelRef model) {
        Request.Builder builder = new Request.Builder()
                .url(ModelUrlResolver.resolveUrl(provider, model.candidate(), ModelCapability.CHAT));

        // 如果当前 model 需要 apiKey 需要单独添加
        if (requiresApiKey()) {
            builder.addHeader("Authorization", "Bearer " + provider.getApiKey());
        }
        return builder;
    }

    private String extractChatContent(JsonObject respJson) {
        if (respJson == null || !respJson.has("choices")) {
            throw new ModelClientException(provider() + " 响应缺少 choices", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonArray choices = respJson.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ModelClientException(provider() + " 响应 choices 为空", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject choice0 = choices.get(0).getAsJsonObject();
        if (choice0 == null || !choice0.has("message")) {
            throw new ModelClientException(provider() + " 响应缺少 message", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject message = choice0.getAsJsonObject("message");
        if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
            throw new ModelClientException(provider() + " 响应缺少 content", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return message.get("content").getAsString();
    }
}
