package com.lan4jn.file.controller;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.common.Result;
import com.lan4jn.file.model.OperationLog;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.service.OperationLogService;
import com.lan4jn.file.service.PermissionAssertService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final PermissionAssertService permissionAssertService;

    public OperationLogController(OperationLogService operationLogService,
                                  PermissionAssertService permissionAssertService) {
        this.operationLogService = operationLogService;
        this.permissionAssertService = permissionAssertService;
    }

    @GetMapping
    public Result<PageResult<OperationLog>> list(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_AUDIT_LOG_READ);
        return Result.success(operationLogService.query(username, module, action, success, startTime, endTime, page, size));
    }
}
