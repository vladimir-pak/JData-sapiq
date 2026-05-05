package com.gpb.replication.sapiq.config;

import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import com.gpb.replication.sapiq.log.SvoiCustomLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationSuccessEventListener implements ApplicationListener<AuthenticationSuccessEvent> {
    private final SvoiCustomLogger svoiLogger;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String clientIp = "unknown";

        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            clientIp = details.getRemoteAddress();
        }

        String username = authentication.getName();

        svoiLogger.logAuth(clientIp, username);
    }
}
