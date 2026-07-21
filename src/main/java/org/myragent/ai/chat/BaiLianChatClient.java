package org.myragent.ai.chat;


import lombok.extern.slf4j.Slf4j;
import org.myragent.ai.convention.ChatRequest;
import org.myragent.ai.enums.ModelProvider;
import org.myragent.ai.model.ModelRef;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BaiLianChatClient extends AbstractOpenAIStyleChatClient {

    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
    }

    @Override
//    @RagTraceNode(name = "bailian-chat", type = "LLM_PROVIDER")
    public String chat(ChatRequest request, ModelRef model) {
        return doChat(request, model);
    }

//    @Override
//    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
//        return doStreamChat(request, callback, target);
//    }
}
