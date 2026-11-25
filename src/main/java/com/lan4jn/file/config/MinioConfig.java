package com.lan4jn.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.default-bucket}")
    private String bucketName;

    @Value("${minio.http.connect-timeout-ms:30000}")
    private long connectTimeoutMs;

    @Value("${minio.http.write-timeout-ms:300000}")
    private long writeTimeoutMs;

    @Value("${minio.http.read-timeout-ms:300000}")
    private long readTimeoutMs;

    @Bean
    public MinioClient minioClient() {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        minioClient.setTimeout(connectTimeoutMs, writeTimeoutMs, readTimeoutMs);

        // 纭繚 bucket 瀛樺湪
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("Bucket '{}' created successfully", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", e.getMessage(), e);
        }

        return minioClient;
    }

    @Bean
    public MinioMultipartClient minioMultipartClient(MinioClient minioClient) {
        return new MinioMultipartClient(minioClient, endpoint, accessKey, secretKey,
                connectTimeoutMs, writeTimeoutMs, readTimeoutMs);
    }
}
