package com.poss.file.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poss.file.common.Result;
import com.poss.file.model.BucketPermission;
import com.poss.file.model.MinioApiKey;
import com.poss.file.service.MinioApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/minio-key")
public class MinioApiKeyController {

    @Autowired
    private MinioApiKeyService apiKeyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/list")
    public Result<List<MinioApiKey>> getAllApiKeys() {
        List<MinioApiKey> keys = apiKeyService.getAllApiKeys();
        // 将JSON转换为前端显示格式
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
        String name = (String) params.get("name");
        Integer expireDays = params.get("expireDays") != null
            ? Integer.valueOf(params.get("expireDays").toString())
            : null;

        // 解析桶权限列表
        List<BucketPermission> bucketPermissions = parseBucketPermissions(params.get("bucketPermissions"));

        MinioApiKey apiKey = apiKeyService.createApiKey(name, bucketPermissions, expireDays);
        return Result.success(apiKey);
    }

    @PutMapping("/{id}")
    public Result<MinioApiKey> updateApiKey(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String name = (String) params.get("name");

        // 解析桶权限列表
        List<BucketPermission> bucketPermissions = parseBucketPermissions(params.get("bucketPermissions"));

        MinioApiKey apiKey = apiKeyService.updateApiKey(id, name, bucketPermissions);
        return Result.success(apiKey);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@PathVariable Long id) {
        apiKeyService.deleteApiKey(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        apiKeyService.toggleApiKeyStatus(id);
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    private List<BucketPermission> parseBucketPermissions(Object bucketPermissionsObj) {
        List<BucketPermission> result = new ArrayList<>();
        if (bucketPermissionsObj == null) {
            return result;
        }

        if (bucketPermissionsObj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) bucketPermissionsObj;
            for (Map<String, Object> item : list) {
                BucketPermission perm = new BucketPermission();
                perm.setBucket((String) item.get("bucket"));
                perm.setCanRead((Boolean) item.get("canRead"));
                perm.setCanWrite((Boolean) item.get("canWrite"));
                perm.setCanDelete((Boolean) item.get("canDelete"));
                result.add(perm);
            }
        }
        return result;
    }
}
