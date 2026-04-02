package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.model.OperationLog;
import com.lan4jn.file.repository.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    public PageResult<OperationLog> query(String username,
                                          String module,
                                          String action,
                                          Boolean success,
                                          LocalDateTime startTime,
                                          LocalDateTime endTime,
                                          int page,
                                          int size) {
        int normalizedPage = page <= 0 ? 1 : page;
        int normalizedSize = size <= 0 ? 20 : size;
        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<OperationLog> result = operationLogRepository.findAll(buildSpec(username, module, action, success, startTime, endTime), pageable);
        return new PageResult<>(result.getContent(), result.getTotalElements(), normalizedPage, normalizedSize);
    }

    public void record(OperationLog operationLog) {
        operationLogRepository.save(operationLog);
    }

    public void safeRecord(OperationLog operationLog) {
        try {
            operationLogRepository.save(operationLog);
        } catch (Exception ignored) {
        }
    }

    public void safeRecord(String operatorUsername,
                           String module,
                           String action,
                           String targetType,
                           String targetId,
                           String targetName,
                           Boolean success,
                           String errorMessage,
                           String bucketName,
                           String objectName,
                           HttpServletRequest request) {
        OperationLog operationLog = new OperationLog();
        operationLog.setOperatorUsername(operatorUsername);
        operationLog.setModule(module);
        operationLog.setAction(action);
        operationLog.setTargetType(targetType);
        operationLog.setTargetId(targetId);
        operationLog.setTargetName(targetName);
        operationLog.setSuccess(success);
        operationLog.setErrorMessage(errorMessage);
        operationLog.setBucketName(bucketName);
        operationLog.setObjectName(objectName);
        operationLog.setCreateTime(LocalDateTime.now());
        if (request != null) {
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setRequestPath(request.getRequestURI());
            operationLog.setClientIp(resolveClientIp(request));
        }
        safeRecord(operationLog);
    }

    public void cleanupExpiredLogs() {
        operationLogRepository.deleteByCreateTimeBefore(LocalDateTime.now().minusDays(30));
    }

    private Specification<OperationLog> buildSpec(String username,
                                                  String module,
                                                  String action,
                                                  Boolean success,
                                                  LocalDateTime startTime,
                                                  LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(root.get("operatorUsername"), "%" + username.trim() + "%"));
            }
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module.trim()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
