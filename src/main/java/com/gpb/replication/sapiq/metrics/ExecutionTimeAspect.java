package com.gpb.replication.sapiq.metrics;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Slf4j
public class ExecutionTimeAspect {
    
    @Around("execution(* com.gpb.replication.sapiq.service.*.*(..)) && " +
            "!within(com.gpb.replication.sapiq.service.CefLogFileService) && " +
            "!within(com.gpb.replication.sapiq.service.KeycloakAuthService) && " +
            "!within(com.gpb.replication.sapiq.service.CustomAuthenticationEntryPoint) && " +
            "!within(com.gpb.replication.sapiq.service.VaultSecretService) && " +
            "!within(com.gpb.replication.sapiq.service.CustomUserDetailsService)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("Метод {} выполнен за {} мс", 
                    joinPoint.getSignature().toShortString(), 
                    executionTime);
            
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Метод {} завершился с ошибкой за {} мс. Ошибка: {}", 
                    joinPoint.getSignature().toShortString(), 
                    executionTime, 
                    e.getMessage());
            throw e;
        }
    }
}
