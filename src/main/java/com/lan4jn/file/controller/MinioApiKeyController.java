package com.lan4jn.file.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lan4jn.file.common.Result;
import com.lan4jn.file.model.BucketPermission;
import com.lan4jn.file.model.MinioApiKey;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.service.MinioApiKeyService;
import com.lan4jn.file.service.PermissionAssertService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/minio-key")
public class MinioApiKeyController {

    private final MinioApiKeyService apiKeyService;
    private final PermissionAssertService permissionAssertService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MinioApiKeyController(MinioApiKeyService apiKeyService,
                                 PermissionAssertService permissionAssertService) {
        this.apiKeyService = apiKeyService;
        this.permissionAssertService = permissionAssertService;
    }

    @GetMapping("/list")
    public Result<List<MinioApiKey>> getAllApiKeys() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_MINIO_KEY_MANAGE);
        List<MinioApiKey> keys = apiKeyService.getAllApiKeys();
        for (MinioApiKey key : keys) {
            List<BucketPermission> perms = apiKeyService.getBucketPermissions(key);
            try {
                key.setBucketsJson(objectMapper.writeValueAsString(perms));
            } catch (JsonProcessingException e) {
                key.setBucketsJson("[]");
            }
        }
        return Result.success(keys);
    }

    @GetMapping("/{id}")
    public Result<MinioApiKey> getApiKey(@PathVariable Long id) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_MINIO_KEY_MANAGE);
        MinioApiKey apiKey = apiKeyService.getApiKeyById(id);
        if (apiKey != null) {
            List<BucketPermission> perms = apiKeyService.getBucketPermissions(apiKey);
            try {
                apiKey.setBucketsJson(objectMapper.writeValueAsString(perms));
            } catch (JsonProcessingException e) {
                apiKey.setBucketsJson("[]");
            }
        }
        return Result.success(apiKey);
    }

    @PostMapping("/create")
    public Result<MinioApiKey> createApiKey(@RequestBody Map<String, Object> params) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_MINIO_KEY_MANAGE);
        String name = (String) params.get("name");
        Integer expireDays = params.get("expireDays") != null
                ? Integer.valueOf(params.get("expireDays").toString())
                : null;
        List<BucketPermission> bucketPermissions = parseBucketPermissions(params.get("bucketPermissions"));
        return Result.success(apiKeyService.createApiKey(name, bucketPermissions, expireDays));
    }

    @PutMapping("/{id}")
    public Result<MinioApiKey> updateApiKey(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_MINIO_KEY_MANAGE);
        String name = (String) params.get("name");
        List<BucketPermission> bucketPermissions = parseBucketPermissions(params.get("bucketPermissions"));
        return Result.success(apiKeyService.updateApiKey(id, name, bucketPermissions));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@PathVariable Long id) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_MINIO_KEY_MANAGE);
        apiKeyService.deleteApiKey(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_MINIO_KEY_MANAGE);
        apiKeyService.toggleApiKeyStatus(id);
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    private List<BucketPermission> parseBucketPermissions(Object bucketPermissionsObj) {
        List<BucketPermission> result = new ArrayList<>();
        if (!(bucketPermissionsObj instanceof List<?> list)) {
            return result;
        }
        for (Map<String, Object> item : (List<Map<String, Object>>) list) {
            BucketPermission perm = new BucketPermission();
            perm.setBucket((String) item.get("bucket"));
            perm.setCanRead((Boolean) item.get("canRead"));
            perm.setCanWrite((Boolean) item.get("canWrite"));
            perm.setCanDelete((Boolean) item.get("canDelete"));
            result.add(perm);
        }
        return result;
    }
}
