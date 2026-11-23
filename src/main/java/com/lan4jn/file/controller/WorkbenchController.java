package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.FavoriteDirectoryRequest;
import com.lan4jn.file.dto.WorkbenchOverviewResponse;
import com.lan4jn.file.dto.WorkbenchPreferenceRequest;
import com.lan4jn.file.service.WorkbenchService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/overview")
    public Result<WorkbenchOverviewResponse> overview(Authentication authentication) {
        return Result.success(workbenchService.getOverview(authentication.getName()));
    }

    @PutMapping("/preference")
    public Result<Void> updatePreference(@RequestBody WorkbenchPreferenceRequest request,
                                         Authentication authentication) {
        if (request == null || request.getDefaultWorkbench() == null) {
            throw new IllegalArgumentException("defaultWorkbench不能为空");
        }
        workbenchService.updatePreference(authentication.getName(), request.getDefaultWorkbench());
        return Result.success();
    }

    @GetMapping("/favorites")
    public Result<WorkbenchOverviewResponse> favorites(Authentication authentication) {
        return Result.success(workbenchService.getOverview(authentication.getName()));
    }

    @PostMapping("/favorites")
    public Result<Void> addFavorite(@RequestBody FavoriteDirectoryRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getBucketName() == null || request.getBucketName().trim().isEmpty()) {
            throw new IllegalArgumentException("bucketName不能为空");
        }
        if (request.getDirectoryPath() == null || request.getDirectoryPath().trim().isEmpty()) {
            throw new IllegalArgumentException("directoryPath不能为空");
        }
        workbenchService.addFavorite(
                authentication.getName(),
                request.getBucketName(),
                request.getDirectoryPath(),
                request.getDirectoryName());
        return Result.success();
    }

    @DeleteMapping("/favorites")
    public Result<Void> deleteFavorite(@RequestParam("bucketName") String bucketName,
                                       @RequestParam("directoryPath") String directoryPath,
                                       Authentication authentication) {
        workbenchService.deleteFavorite(authentication.getName(), bucketName, directoryPath);
        return Result.success();
    }
}
