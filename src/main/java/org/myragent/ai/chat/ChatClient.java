package org.myragent.ai.chat;

import org.myragent.ai.convention.ChatRequest;
import org.myragent.ai.model.ModelRef;

public interface ChatClient {
    String provider();

    String chat(ChatRequest request, ModelRef model);
}