package com.example.ecommerce.config;

import com.example.ecommerce.ai.StubChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
public class AiConfig {

    /**
     * Dev and tests always use the stub — no live LLM calls or API keys required.
     */
    @Bean
    @Primary
    @Profile("dev")
    ChatModel devStubChatModel() {
        return new StubChatModel();
    }

    /**
     * Fallback when OpenAI auto-config does not activate (e.g. docker without API key).
     */
    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    ChatModel fallbackStubChatModel() {
        return new StubChatModel();
    }
}
