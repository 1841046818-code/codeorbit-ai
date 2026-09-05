package com.codeorbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class CodeOrbitApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeOrbitApplication.class, args);
    }

    @RestController
    @RequestMapping("/api/system")
    static class SystemController {
        @GetMapping("/health")
        public HealthResponse health() {
            return new HealthResponse("UP", "星码空间后端已启动", "第一阶段骨架");
        }
    }

    @RestController
    @RequestMapping("/api/ai")
    @CrossOrigin(origins = "*")
    static class AiController {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        private final List<AiConfig> configs = new ArrayList<>(List.of(new AiConfig("local-ollama", "本地 Ollama", "http://127.0.0.1:11434/v1", "", "qwen2.5-coder:7b", true)));

        @GetMapping("/config")
        public Map<String, Object> getConfig() {
            AiConfig active = activeConfig();
            return Map.of("active", publicConfig(active), "configs", configs.stream().map(this::publicConfig).toList());
        }

        @PostMapping("/config")
        public synchronized Map<String, Object> saveConfig(@RequestBody AiConfig input) {
            String id = input.id() == null || input.id().isBlank() ? UUID.randomUUID().toString() : input.id();
            String name = input.name() == null || input.name().isBlank() ? "未命名模型" : input.name();
            String baseUrl = input.baseUrl() == null || input.baseUrl().isBlank() ? "http://127.0.0.1:11434/v1" : input.baseUrl().replaceAll("/+$", "");
            String model = input.model() == null || input.model().isBlank() ? "qwen2.5-coder:7b" : input.model();
            configs.removeIf(item -> item.id().equals(id));
            boolean active = input.active() || configs.isEmpty();
            if (active) configs.replaceAll(item -> new AiConfig(item.id(), item.name(), item.baseUrl(), item.apiKey(), item.model(), false));
            configs.add(new AiConfig(id, name, baseUrl, input.apiKey() == null ? "" : input.apiKey(), model, active));
            return Map.of("message", "模型配置已保存", "active", publicConfig(activeConfig()), "configs", configs.stream().map(this::publicConfig).toList());
        }

        @PostMapping("/config/{id}/activate")
        public synchronized Map<String, Object> activate(@PathVariable String id) {
            if (configs.stream().noneMatch(item -> item.id().equals(id))) throw new IllegalArgumentException("模型配置不存在");
            configs.replaceAll(item -> new AiConfig(item.id(), item.name(), item.baseUrl(), item.apiKey(), item.model(), item.id().equals(id)));
            return Map.of("message", "已切换当前模型", "active", publicConfig(activeConfig()));
        }

        @DeleteMapping("/config/{id}")
        public synchronized Map<String, Object> delete(@PathVariable String id) {
            if (configs.size() == 1) throw new IllegalArgumentException("至少保留一个模型配置");
            boolean deletingActive = activeConfig().id().equals(id);
            configs.removeIf(item -> item.id().equals(id));
            if (deletingActive) {
                AiConfig first = configs.getFirst();
                configs.replaceAll(item -> new AiConfig(item.id(), item.name(), item.baseUrl(), item.apiKey(), item.model(), item.id().equals(first.id())));
            }
            return Map.of("message", "模型配置已删除", "configs", configs.stream().map(this::publicConfig).toList(), "active", publicConfig(activeConfig()));
        }

        @PostMapping("/config/{id}/test")
        public Map<String, Object> test(@PathVariable String id) throws Exception {
            AiConfig target = configs.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("模型配置不存在"));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target.baseUrl() + "/models")).timeout(Duration.ofSeconds(15)).GET();
            if (!target.apiKey().isBlank()) request.header("Authorization", "Bearer " + target.apiKey());
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return Map.of("ok", response.statusCode() < 400, "status", response.statusCode(), "message", response.statusCode() < 400 ? "连接成功" : "接口返回 HTTP " + response.statusCode());
        }

        @PostMapping(value = "/code/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> generate(@RequestBody GenerateRequest input) throws Exception {
            if (input.prompt() == null || input.prompt().isBlank()) throw new IllegalArgumentException("请输入代码需求");
            AiConfig config = activeConfig();
            String endpoint = config.baseUrl() + "/chat/completions";
            String body = "{\"model\":\"" + json(config.model()) + "\",\"temperature\":0.2,\"messages\":[{\"role\":\"system\",\"content\":\"你是星码空间的代码生成助手，只输出可运行的代码或简短说明。\"},{\"role\":\"user\",\"content\":\"" + json(input.prompt()) + "\"}]}";
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (!config.apiKey().isBlank()) request.header("Authorization", "Bearer " + config.apiKey());
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("模型接口返回 HTTP " + response.statusCode());
            String content = extractContent(response.body());
            return Map.of("content", content, "model", config.model());
        }

        private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }
        private static String extractContent(String body) {
            String marker = "\"content\":\"";
            int start = body.indexOf(marker);
            if (start < 0) return body;
            start += marker.length();
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            for (int i = start; i < body.length(); i++) {
                char c = body.charAt(i);
                if (escaped) { result.append(c == 'n' ? '\n' : c == 'r' ? '\r' : c); escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == '"') break;
                result.append(c);
            }
            return result.toString();
        }
        private AiConfig activeConfig() { return configs.stream().filter(AiConfig::active).findFirst().orElse(configs.getFirst()); }
        private Map<String, Object> publicConfig(AiConfig config) { return Map.of("id", config.id(), "name", config.name(), "baseUrl", config.baseUrl(), "model", config.model(), "active", config.active(), "hasApiKey", !config.apiKey().isBlank()); }
    }

    record AiConfig(String id, String name, String baseUrl, String apiKey, String model, boolean active) {}
    record GenerateRequest(String prompt) {}

    record HealthResponse(String status, String message, String stage) {}
}
