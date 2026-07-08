package org.example.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.model.AiContext;
import org.example.model.AiReview;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


public class HttpAiReviewer implements AiReviewer {

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public HttpAiReviewer(String endpoint, String apiKey, String model, long timeoutMs) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public AiReview review(AiContext context) {
        String prompt = buildPrompt(context);
        String requestBody = buildRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiReviewException("AI request interrupted", e);
        } catch (Exception e) {
            throw new AiReviewException("AI request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new AiReviewException("AI provider returned HTTP " + response.statusCode());
        }

        return new AiReview(extractContent(response.body()));
    }

    private String buildPrompt(AiContext context) {
        String contextJson;
        try {
            contextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        } catch (Exception e) {
            throw new AiReviewException("Failed to serialize AiContext", e);
        }

        return """
                Bạn là chuyên gia đánh giá chất lượng tương tác giữa ứng dụng và cơ sở dữ liệu.
                
                Hãy phân tích schema và ngữ cảnh truy vấn đã được che giấu (JSON) bên dưới.
                
                Chỉ trả về ĐÚNG 3 phần với các tiêu đề sau:
                
                ## Đánh giá Schema
                ## Tối ưu Truy vấn
                ## Tổng kết
                
                Hãy trả lời ngắn gọn, rõ ràng và cụ thể.
                Chỉ sử dụng thông tin có trong context, không suy diễn hoặc bịa thêm.
                Khi nhận xét, hãy tham chiếu đến bảng, cột, truy vấn hoặc finding tương ứng trong context.
                Các truy vấn chỉ là fingerprint với ký tự '?' thay cho giá trị thực (dữ liệu đã được che giấu).
                Trả lời bằng tiếng việt
                Context:
                """ + contextJson;
    }

    private String buildRequestBody(String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", prompt);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiReviewException("Failed to build AI request body", e);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new AiReviewException("AI response missing choices[0].message.content");
            }
            return content.asText();
        } catch (AiReviewException e) {
            throw e;
        } catch (Exception e) {
            throw new AiReviewException("Failed to parse AI response", e);
        }
    }
}
