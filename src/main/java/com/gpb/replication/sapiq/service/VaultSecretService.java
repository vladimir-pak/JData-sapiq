package com.gpb.replication.sapiq.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpb.replication.sapiq.dto.SourceDbConnections;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class VaultSecretService {
    private final VaultTemplate vaultTemplate;
    private final String basePath;

    public VaultSecretService(VaultTemplate vaultTemplate,
                              @Value("${spring.cloud.vault.kv.backend:secret}") String backend,
                              @Value("${spring.cloud.vault.kv.default-context:ord/src/connections}") String endpoint) {
        this.vaultTemplate = vaultTemplate;
        this.basePath = String.format("%s/data/%s", backend, endpoint);
    }

    /**
     * Получить все секреты для конкретного сервиса
     * @param serviceName имя сервиса (добавляется к базовому пути)
     * @return Map с секретами или empty Map если секрет не найден
     */
    public SourceDbConnections getServiceSecrets(String serviceName) {
        String fullPath = buildFullPath(serviceName);
        log.info("Reading secrets from Vault path: {}", fullPath);
        try {
            VaultResponse response = vaultTemplate.read(fullPath);
            if (response != null && response.getData() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
                if (data != null) {
                    ObjectMapper mapper = new ObjectMapper();

                    // Настройка для обработки примитивных типов
                    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    return mapper.convertValue(data, SourceDbConnections.class);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to read source DB connection from Vault path: {}", fullPath, e);
            return null;
        }
    }

    /**
     * Проверить существование секретов для сервиса
     * @param serviceName имя сервиса
     * @return true если секреты существуют
     */
    public boolean serviceSecretsExist(String serviceName) {
        String fullPath = buildFullPath(serviceName);
        try {
            VaultResponse response = vaultTemplate.read(fullPath);
            return response != null && response.getData() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildFullPath(String serviceName) {
        return basePath + "/" + serviceName;
    }

    /**
     * Простая проверка подключения к Vault
     * @return true если подключение активно, false если нет
     */
    public boolean isVaultConnected() {
        try {
            // Пробуем вызвать системный эндпоинт health - он не требует специальных прав
            vaultTemplate.opsForSys().health();
            log.debug("Vault connection check: SUCCESS");
            return true;
        } catch (Exception e) {
            log.warn("Vault connection check: FAILED - {}", e.getMessage());
            return false;
        }
    }
}