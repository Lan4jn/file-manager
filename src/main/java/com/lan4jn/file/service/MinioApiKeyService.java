package com.lan4jn.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lan4jn.file.model.BucketPermission;
import com.lan4jn.file.model.MinioApiKey;
import com.lan4jn.file.repository.MinioApiKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MinioApiKeyService {

    @Autowired
    private MinioApiKeyRepository apiKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MinioApiKey> getAllApiKeys() {
        return apiKeyRepository.findAll();
    }

    public MinioApiKey getApiKeyById(Long id) {
        return apiKeyRepository.findById(id).orElse(null);
    }

    @Transactional
    public MinioApiKey createApiKey(String name, List<BucketPermission> bucketPermissions, Integer expireDays) {
        MinioApiKey apiKey = new MinioApiKey();
        apiKey.setName(name);
        apiKey.setAccessKey(generateAccessKey());
        apiKey.setSecretKey(generateSecretKey());

        try {
            apiKey.setBucketsJson(objectMapper.writeValueAsString(bucketPermissions));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("搴忓垪鍖栨《鏉冮檺澶辫触", e);
        }

        apiKey.setCreateTime(LocalDateTime.now());
        apiKey.setIsActive(true);

        if (expireDays != null && expireDays > 0) {
            apiKey.setExpireTime(LocalDateTime.now().plusDays(expireDays));
        }

        return apiKeyRepository.save(apiKey);
    }

    @Transactional
    public MinioApiKey updateApiKey(Long id, String name, List<BucketPermission> bucketPermissions) {
        Optional<MinioApiKey> opt = apiKeyRepository.findById(id);
        if (!opt.isPresent()) {
            throw new RuntimeException("API Key涓嶅瓨鍦?");
        }

        MinioApiKey apiKey = opt.get();
        apiKey.setName(name);

        try {
            apiKey.setBucketsJson(objectMapper.writeValueAsString(bucketPermissions));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("搴忓垪鍖栨《鏉冮檺澶辫触", e);
        }

        return apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void deleteApiKey(Long id) {
        apiKeyRepository.deleteById(id);
    }

    @Transactional
    public void toggleApiKeyStatus(Long id) {
        Optional<MinioApiKey> opt = apiKeyRepository.findById(id);
        if (opt.isPresent()) {
            MinioApiKey apiKey = opt.get();
            apiKey.setIsActive(!apiKey.getIsActive());
            apiKeyRepository.save(apiKey);
        }
    }

    public boolean validateAccessKey(String accessKey) {
        Optional<MinioApiKey> opt = apiKeyRepository.findByAccessKey(accessKey);
        if (!opt.isPresent()) {
            return false;
        }
        MinioApiKey apiKey = opt.get();
        if (!Boolean.TRUE.equals(apiKey.getIsActive())) {
            return false;
        }
        if (apiKey.getExpireTime() != null && LocalDateTime.now().isAfter(apiKey.getExpireTime())) {
            return false;
        }
        return true;
    }

    public MinioApiKey getApiKeyByAccessKey(String accessKey) {
        return apiKeyRepository.findByAccessKey(accessKey).orElse(null);
    }

    public List<BucketPermission> getBucketPermissions(MinioApiKey apiKey) {
        if (StrUtil.isEmpty(apiKey.getBucketsJson())) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(apiKey.getBucketsJson(), new TypeReference<List<BucketPermission>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    public boolean hasPermission(String accessKey, String bucket, String permission) {
        MinioApiKey apiKey = getApiKeyByAccessKey(accessKey);
        if (apiKey == null) {
            return false;
        }

        List<BucketPermission> permissions = getBucketPermissions(apiKey);
        for (BucketPermission bp : permissions) {
            if (bp.getBucket().equals(bucket)) {
                switch (permission) {
                    case "read": return Boolean.TRUE.equals(bp.getCanRead());
                    case "write": return Boolean.TRUE.equals(bp.getCanWrite());
                    case "delete": return Boolean.TRUE.equals(bp.getCanDelete());
                }
            }
        }
        return false;
    }

    private String generateAccessKey() {
        return "MK" + IdUtil.fastSimpleUUID().substring(0, 16).toUpperCase();
    }

    private String generateSecretKey() {
        return IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
    }
}

