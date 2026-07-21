package org.myragent.ai.controller;

import org.myragent.ai.chat.BaiLianChatClient;
import org.myragent.ai.config.AIModelProperties;
import org.myragent.ai.convention.ChatMessage;
import org.myragent.ai.convention.ChatRequest;
import org.myragent.ai.enums.ModelProvider;
import org.myragent.ai.model.ModelRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private BaiLianChatClient baiLianChatClient;

    @Autowired
    private AIModelProperties aiModelProperties;

    @GetMapping("/test")
    public String testChat(@RequestParam(value = "prompt", defaultValue = "你好") String prompt) {
        // 1. 构造请求体
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(new ChatMessage(ChatMessage.Role.USER, prompt)));
        request.setTemperature(0.7);

        String providerKey = ModelProvider.BAI_LIAN.getId(); // 即 "bailian"

        AIModelProperties.ProviderConfig providerConfig = aiModelProperties.getProviders().get(providerKey);
        if (providerConfig == null) {
            throw new IllegalStateException("未找到对应的 ProviderConfig: " + providerKey);
        }
        // 2. 从配置中精准寻找 provider 为 "bailian" 的候选模型（比如选 qwen-plus-latest）
        AIModelProperties.ModelCandidate candidate = aiModelProperties.getChat().getCandidates().stream()
                .filter(c -> ModelProvider.BAI_LIAN.getId().equals(c.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到配置的百炼模型"));

        // 3. 组装 ModelRef
        ModelRef model = new ModelRef(candidate.getProvider(), candidate, providerConfig);

        // 4. 发起调用
        return baiLianChatClient.chat(request, model);
    }
}