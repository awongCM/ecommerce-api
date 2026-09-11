package com.example.ecommerce.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Deterministic ChatModel for dev and CI — never calls an external provider.
 */
public class StubChatModel implements ChatModel {

    public static final String MODEL_ID = "stub";

    private static final String STUB_JSON = """
        {"classification":"OPS_REVIEW","confidence":0.85,"rationale":"Stub model: default ops review for development and tests."}
        """;

    @Override
    public ChatResponse call(Prompt prompt) {
        var generation = new Generation(new AssistantMessage(STUB_JSON));
        return new ChatResponse(List.of(generation));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().model(MODEL_ID).build();
    }
}
