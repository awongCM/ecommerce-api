package com.example.ecommerce.config;

import com.example.ecommerce.ai.StubChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prevents silent stub fallback when triage is enabled in docker/prod without a real API key.
 */
@Component
@Profile("docker")
public class OrderAnomalyTriageStartupValidator {

    private final AppProperties appProperties;
    private final ChatModel chatModel;

    public OrderAnomalyTriageStartupValidator(AppProperties appProperties, ChatModel chatModel) {
        this.appProperties = appProperties;
        this.chatModel = chatModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        if (appProperties.getFeatures().isOrderAnomalyTriage()
            && chatModel instanceof StubChatModel) {
            throw new IllegalStateException(
                "ORDER_ANOMALY_TRIAGE_ENABLED=true requires SPRING_AI_OPENAI_API_KEY "
                    + "in the docker profile (stub model is not allowed for live triage)");
        }
    }
}
