package com.lan4jn.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * 上游证书签发中心客户端
 * 固定 HTTPS 源服务/项目/专用Bearer，禁重定向、时限保护、DTO白名单及校验记录 projectId/路径id
 * 路径格式: {baseUrl}/integration/v1/projects/{projectId}/standard-client-certificates
 */
@Service
public class IssuerBridgeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    // 标准证书状态三值
    private static final Set<String> VALID_STATUSES = Set.of("VALID", "REVOKED", "EXPIRED");

    private final IssuerBridgeSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public IssuerBridgeClient(IssuerBridgeSettings settings, ObjectMapper objectMapper) {
        this(settings, objectMapper, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT).build());
    }

    IssuerBridgeClient(IssuerBridgeSettings settings, ObjectMapper objectMapper, HttpClient httpClient) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    private void ensureEnabled() {
        if (!settings.isEnabled()) {
            throw new IllegalStateException("Issuer bridge is disabled");
        }
    }

    private String getBaseEndpoint() {
        String base = settings.getBaseUrl().replaceAll("/+$", "");
        return base + "/integration/v1/projects/" + encodePath(settings.getProjectId()) + "/standard-client-certificates";
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Certificate id cannot be empty");
        }
        if (".".equals(id) || "..".equals(id) || id.contains("/") || id.contains("\\") || id.contains(" ") || !id.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Certificate id is invalid");
        }
    }

    public JsonNode list(String fingerprint, int page, int size) {
        ensureEnabled();
        if (page < 0 || size <= 0 || size > 100) {
            throw new IllegalArgumentException("Invalid page or size parameter (size must be 1-100, page >= 0)");
        }

        StringBuilder url = new StringBuilder(getBaseEndpoint())
                .append("?page=").append(page)
                .append("&size=").append(size);

        if (fingerprint != null && !fingerprint.isBlank()) {
            String trimmed = fingerprint.trim();
            if (trimmed.contains("\n") || trimmed.contains("\r")) {
                throw new IllegalArgumentException("Fingerprint contains invalid newline characters");
            }
            url.append("&fingerprint=").append(URLEncoder.encode(trimmed, StandardCharsets.UTF_8));
        }

        HttpRequest request = newRequestBuilder(URI.create(url.toString()))
                .GET()
                .build();

        JsonNode raw = sendAndReceive(request);
        return filterAndValidateListResponse(raw, size);
    }

    public JsonNode issue(String requestId, String csrPem, String deviceLabel) {
        ensureEnabled();
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId cannot be empty");
        }
        if (csrPem == null || csrPem.isBlank()) {
            throw new IllegalArgumentException("csrPem cannot be empty");
        }

        ObjectNode reqBody = objectMapper.createObjectNode();
        reqBody.put("requestId", requestId.trim());
        reqBody.put("csrPem", csrPem.trim());
        reqBody.put("deviceLabel", deviceLabel != null ? deviceLabel.trim() : "");

        HttpRequest request = newRequestBuilder(URI.create(getBaseEndpoint()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody.toString(), StandardCharsets.UTF_8))
                .build();

        JsonNode raw = sendAndReceive(request);
        ObjectNode clean = buildCleanCertObject(raw);

        // 校验同 requestId
        if (!requestId.trim().equals(clean.get("requestId").asText())) {
            throw new SecurityException("Response requestId mismatch");
        }

        // 核对请求设备标签
        if (deviceLabel != null && !deviceLabel.isBlank()) {
            if (!deviceLabel.trim().equals(clean.get("deviceLabel").asText())) {
                throw new SecurityException("Response deviceLabel mismatch");
            }
        }

        return clean;
    }

    public JsonNode get(String id) {
        ensureEnabled();
        validateId(id);

        URI uri = URI.create(getBaseEndpoint() + "/" + encodePath(id));
        HttpRequest request = newRequestBuilder(uri)
                .GET()
                .build();

        JsonNode raw = sendAndReceive(request);
        ObjectNode clean = buildCleanCertObject(raw);

        // 校验同路径 id
        if (!id.equals(clean.get("id").asText())) {
            throw new SecurityException("Response id mismatch");
        }

        return clean;
    }

    public JsonNode revoke(String id) {
        ensureEnabled();
        validateId(id);

        URI uri = URI.create(getBaseEndpoint() + "/" + encodePath(id) + "/revoke");
        HttpRequest request = newRequestBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();

        JsonNode raw = sendAndReceive(request);
        ObjectNode clean = buildCleanCertObject(raw);

        // 校验同路径 id
        if (!id.equals(clean.get("id").asText())) {
            throw new SecurityException("Response id mismatch");
        }

        // revoke 响应必须 status=REVOKED
        if (!"REVOKED".equals(clean.get("status").asText())) {
            throw new SecurityException("Revoke response status must be REVOKED");
        }

        return clean;
    }

    private HttpRequest.Builder newRequestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Accept", "application/json");
    }

    private JsonNode sendAndReceive(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            // 禁重定向防护（不暴露Location或内部敏感路径）
            if (status >= 300 && status < 400) {
                throw new IllegalStateException("Issuer bridge redirect is forbidden");
            }

            // 非 2xx 响应拒绝（不向外部泄露上游原始body）
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Issuer bridge request failed with HTTP " + status);
            }

            // Content-Type 校验
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
                throw new IllegalStateException("Invalid response content type from issuer bridge");
            }

            JsonNode tree = objectMapper.readTree(response.body());
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                throw new IllegalStateException("Empty or null JSON response from issuer bridge");
            }
            return tree;
        } catch (HttpTimeoutException te) {
            throw new IllegalStateException("Issuer bridge request timed out", te);
        } catch (IOException ie) {
            throw new IllegalStateException("Issuer bridge communication failed", ie);
        } catch (InterruptedException inter) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Issuer bridge request interrupted", inter);
        }
    }

    private JsonNode filterAndValidateListResponse(JsonNode raw, int size) {
        if (raw == null || !raw.isObject()) {
            throw new IllegalStateException("Expected JSON object from issuer bridge");
        }

        JsonNode itemsNode = raw.get("items");
        JsonNode totalNode = raw.get("total");
        if (itemsNode == null || !itemsNode.isArray() || totalNode == null || !totalNode.isIntegralNumber()
                || !totalNode.canConvertToLong() || totalNode.longValue() < itemsNode.size() || itemsNode.size() > size) {
            throw new IllegalStateException("Response must strictly contain valid items array and non-negative total number");
        }

        ObjectNode cleanPage = objectMapper.createObjectNode();
        cleanPage.put("total", totalNode.asLong());

        ArrayNode cleanItems = objectMapper.createArrayNode();
        for (JsonNode item : itemsNode) {
            cleanItems.add(buildCleanCertObject(item));
        }
        cleanPage.set("items", cleanItems);

        return cleanPage;
    }

    private ObjectNode buildCleanCertObject(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new IllegalStateException("Expected JSON object for certificate item");
        }

        if (!settings.getProjectId().equals(item.path("projectId").textValue())) {
            throw new SecurityException("Response projectId mismatch");
        }
        ObjectNode clean = objectMapper.createObjectNode();
        for (String field : new String[]{"id", "projectId", "requestId", "deviceLabel", "fingerprint", "serial",
                "status", "certificatePem", "chainPem"}) {
            String value = item.path(field).textValue();
            if (value == null || (value.isBlank() && !"deviceLabel".equals(field))) {
                throw new IllegalStateException("Invalid certificate field: " + field);
            }
            clean.put(field, value);
        }
        validateId(clean.get("id").textValue());
        if (!VALID_STATUSES.contains(clean.get("status").textValue())) {
            throw new IllegalStateException("Invalid certificate status");
        }
        JsonNode expires = item.path("expiresAt");
        if (!expires.isIntegralNumber() || !expires.canConvertToLong() || expires.longValue() <= 0) {
            throw new IllegalStateException("Invalid certificate expiresAt");
        }
        return clean.put("expiresAt", expires.longValue());
    }
}
