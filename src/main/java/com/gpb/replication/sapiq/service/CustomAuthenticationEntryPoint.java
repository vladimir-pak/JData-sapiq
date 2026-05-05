package com.gpb.replication.sapiq.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.gpb.replication.sapiq.log.SvoiCustomLogger;

import java.io.IOException;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final SvoiCustomLogger svoiLogger;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String endpoint = request.getRequestURI();
        String clientIp = request.getRemoteAddr();
        String username = extractUsername(request.getHeader("Authorization"));

        if (authException instanceof BadCredentialsException) {
            log.warn("Ошибка авторизации: неверный логин или пароль. User: {}, Endpoint: {}, IP: {}",
                    username, endpoint, clientIp);

            svoiLogger.logBadCredentials(clientIp, username, endpoint);
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid username or password");

        } else {
            log.error("Ошибка аутентификации: {}. User: {}, Endpoint: {}, IP: {}",
                    authException.getMessage(), username, endpoint, clientIp);

            svoiLogger.logBadCredentials(clientIp, username, endpoint);
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication failed");
        }
    }
    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }

    private String extractUsername(String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Basic ")) {
                String base64Credentials = authHeader.substring("Basic ".length());
                String decoded = new String(Base64.getDecoder().decode(base64Credentials));
                return decoded.split(":", 2)[0];
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}