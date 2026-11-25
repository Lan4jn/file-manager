package com.lan4jn.file.config;

import com.google.common.collect.ImmutableMultimap;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.http.Method;
import io.minio.messages.ListPartsResult;
import io.minio.messages.Part;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MinioMultipartClient {

    private static final int PRESIGNED_URL_EXPIRY_SECONDS = 600;
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_PARTS_PER_PAGE = 1000;

    private final MinioClient minioClient;
    private final OkHttpClient httpClient;
    private final MultipartOperations multipartOperations;

    public MinioMultipartClient(MinioClient minioClient, String endpoint, String accessKey, String secretKey,
                                long connectTimeoutMs, long writeTimeoutMs, long readTimeoutMs) {
        this.minioClient = minioClient;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        this.multipartOperations = new MultipartOperations(MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build());
    }

    public String createMultipartUpload(String bucket, String objectName, String contentType) throws Exception {
        return multipartOperations.createMultipartUpload(bucket, objectName, contentType);
    }

    public String uploadPartStream(String bucket, String objectName, String uploadId, int partNumber,
                                   InputStream bodyStream, long contentLength) throws Exception {
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length must not be negative");
        }
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("uploadId", uploadId);
        queryParams.put("partNumber", Integer.toString(partNumber));
        String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(objectName)
                .expiry(PRESIGNED_URL_EXPIRY_SECONDS)
                .extraQueryParams(queryParams)
                .build());

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public long contentLength() {
                return contentLength;
            }

            @Override
            public boolean isOneShot() {
                return true;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                long remaining = contentLength;
                do {
                    int count = remaining == 0 ? 0
                            : bodyStream.read(buffer, 0, (int) Math.min(remaining, buffer.length));
                    if (count < 0) {
                        throw new EOFException("Part stream is shorter than the declared content length");
                    }
                    remaining -= count;
                    // Check for excess data before sending the final bytes of the declared body.
                    if (remaining == 0 && bodyStream.read() != -1) {
                        throw new IOException("Part stream is longer than the declared content length");
                    }
                    sink.write(buffer, 0, count);
                } while (remaining > 0);
            }
        };

        Request request = new Request.Builder().url(url).put(body).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("MinIO part upload failed with HTTP status " + response.code());
            }
            String etag = response.header("ETag");
            if (etag == null || etag.isEmpty()) {
                throw new IOException("MinIO part upload response is missing an ETag");
            }
            return etag.replace("\"", "");
        }
    }

    public String completeMultipartUpload(String bucket, String objectName, String uploadId, List<Part> parts)
            throws Exception {
        Part[] completedParts = parts.stream()
                .map(part -> new Part(part.partNumber(), part.etag()))
                .toArray(Part[]::new);
        return multipartOperations.completeMultipartUpload(bucket, objectName, uploadId, completedParts);
    }

    public void abortMultipartUpload(String bucket, String objectName, String uploadId) throws Exception {
        multipartOperations.abortMultipartUpload(bucket, objectName, uploadId);
    }

    public List<Part> listParts(String bucket, String objectName, String uploadId) throws Exception {
        List<Part> parts = new ArrayList<>();
        int marker = 0;
        while (true) {
            ListPartsResult page = multipartOperations.listParts(bucket, objectName, uploadId, marker);
            parts.addAll(page.partList());
            if (!page.isTruncated()) {
                return parts;
            }
            int nextMarker = page.nextPartNumberMarker();
            if (nextMarker <= marker) {
                throw new IOException("MinIO returned a non-advancing part number marker");
            }
            marker = nextMarker;
        }
    }

    private static final class MultipartOperations extends MinioAsyncClient {

        private MultipartOperations(MinioAsyncClient client) {
            super(client);
        }

        private String createMultipartUpload(String bucket, String objectName, String contentType) throws Exception {
            ImmutableMultimap<String, String> headers = contentType == null || contentType.isEmpty()
                    ? ImmutableMultimap.of() : ImmutableMultimap.of("Content-Type", contentType);
            return super.createMultipartUpload(bucket, null, objectName, headers, null).result().uploadId();
        }

        private String completeMultipartUpload(String bucket, String objectName, String uploadId, Part[] parts)
                throws Exception {
            return super.completeMultipartUpload(bucket, null, objectName, uploadId, parts, null, null).etag();
        }

        private void abortMultipartUpload(String bucket, String objectName, String uploadId) throws Exception {
            super.abortMultipartUpload(bucket, null, objectName, uploadId, null, null);
        }

        private ListPartsResult listParts(String bucket, String objectName, String uploadId, int marker)
                throws Exception {
            return super.listParts(bucket, null, objectName, MAX_PARTS_PER_PAGE, marker, uploadId, null, null).result();
        }
    }
}
