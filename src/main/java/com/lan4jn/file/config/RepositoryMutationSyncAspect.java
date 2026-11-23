package com.lan4jn.file.config;

import com.lan4jn.file.service.DatabaseMirrorService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(name = "app.datasource-mirror.enabled", havingValue = "true")
public class RepositoryMutationSyncAspect {

    private final DatabaseMirrorService databaseMirrorService;

    public RepositoryMutationSyncAspect(DatabaseMirrorService databaseMirrorService) {
        this.databaseMirrorService = databaseMirrorService;
    }

    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object synchronizeAfterMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if (isMutation(signature.getMethod().getName())) {
            databaseMirrorService.requestAfterMasterCommit();
        }
        return result;
    }

    private boolean isMutation(String methodName) {
        return methodName.startsWith("save")
                || methodName.startsWith("delete")
                || "flush".equals(methodName);
    }
}
