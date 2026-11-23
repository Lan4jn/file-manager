package com.lan4jn.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.StandardCertificateIssueRequest;
import com.lan4jn.file.repository.UserCertificateRepository;
import com.lan4jn.file.service.IssuerBridgeClient;
import com.lan4jn.file.service.IssuerBridgeSettings;
import com.lan4jn.file.service.IssuerCertificatePolicy;
import com.lan4jn.file.service.PermissionAssertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/standard-client-certificates")
@PreAuthorize("hasRole('ADMIN')")
public class StandardClientCertificateAdminController {

    private final IssuerBridgeClient bridgeClient;
    private final IssuerBridgeSettings bridgeSettings;
    private final IssuerCertificatePolicy certificatePolicy;
    private final PermissionAssertService permissionAssertService;
    private final UserCertificateRepository userCertificateRepository;

    public StandardClientCertificateAdminController(
            IssuerBridgeClient bridgeClient,
            IssuerBridgeSettings bridgeSettings,
            IssuerCertificatePolicy certificatePolicy,
            PermissionAssertService permissionAssertService,
            UserCertificateRepository userCertificateRepository) {
        this.bridgeClient = bridgeClient;
        this.bridgeSettings = bridgeSettings;
        this.certificatePolicy = certificatePolicy;
        this.permissionAssertService = permissionAssertService;
        this.userCertificateRepository = userCertificateRepository;
    }

    private void ensureSuperAdmin() {
        if (!permissionAssertService.isSuperAdmin()) {
            throw new SecurityException("仅超级管理员可访问");
        }
    }

    private void ensureEnabled() {
        if (!bridgeSettings.isEnabled()) {
            throw new IllegalArgumentException("标准证书中心未启用");
        }
    }

    @GetMapping
    public Result<JsonNode> listCertificates(
            @RequestParam(value = "fingerprint", required = false) String fingerprint,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        ensureSuperAdmin();
        ensureEnabled();

        JsonNode pageNode = bridgeClient.list(fingerprint, page, size);
        return Result.success(enrichPageNode(pageNode));
    }

    @PostMapping
    public Result<JsonNode> issueCertificate(@RequestBody StandardCertificateIssueRequest request) {
        ensureSuperAdmin();
        ensureEnabled();

        if (request == null || request.getCsrPem() == null || request.getCsrPem().trim().isEmpty()) {
            throw new IllegalArgumentException("CSR内容不能为空");
        }

        JsonNode certNode = bridgeClient.issue(
                request.getRequestId(),
                request.getCsrPem(),
                request.getDeviceLabel()
        );
        return Result.success(enrichSingleCertificate(certNode));
    }

    @GetMapping("/{id}")
    public Result<JsonNode> getCertificate(@PathVariable String id) {
        ensureSuperAdmin();
        ensureEnabled();

        JsonNode certNode = bridgeClient.get(id);
        return Result.success(enrichSingleCertificate(certNode));
    }

    @PostMapping("/{id}/revoke")
    public Result<JsonNode> revokeCertificate(@PathVariable String id) {
        ensureSuperAdmin();
        ensureEnabled();

        // 必须通过 IssuerCertificatePolicy.revoke 执行以保证同步缓存
        JsonNode certNode = certificatePolicy.revoke(id);
        return Result.success(enrichSingleCertificate(certNode));
    }

    private JsonNode enrichPageNode(JsonNode raw) {
        if (raw == null) {
            return null;
        }
        JsonNode copy = raw.deepCopy();
        if (copy.isObject() && copy.has("items") && copy.get("items").isArray()) {
            ArrayNode items = (ArrayNode) copy.get("items");
            for (JsonNode item : items) {
                if (item.isObject()) {
                    enrichItem((ObjectNode) item);
                }
            }
        }
        return copy;
    }

    private JsonNode enrichSingleCertificate(JsonNode raw) {
        if (raw == null) {
            return null;
        }
        JsonNode copy = raw.deepCopy();
        if (copy.isObject()) {
            enrichItem((ObjectNode) copy);
        }
        return copy;
    }

    private void enrichItem(ObjectNode item) {
        String fp = item.path("fingerprint").asText(null);
        boolean isBound = fp != null && !fp.isBlank() && userCertificateRepository.findByFingerprint(fp).isPresent();
        item.put("bound", isBound);
    }
}
