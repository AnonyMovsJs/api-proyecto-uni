package com.ronald.proyecto.proyecto_uni;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAIProperties {

    private ApiConfig api = new ApiConfig();

    public ApiConfig getApi() {
        return api;
    }

    public void setApi(ApiConfig api) {
        this.api = api;
    }

    public static class ApiConfig {
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }
}