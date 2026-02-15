package com.grash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.license.*;
import com.grash.utils.FingerprintGenerator;
import com.grash.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicenseService {

    private final ObjectMapper objectMapper;

    public synchronized LicensingState getLicensingState() {
        return LicensingState.builder()
                .valid(true)
                .hasLicense(true)
                .entitlements(Stream.of(LicenseEntitlement.values()).map(LicenseEntitlement::toString)
                        .collect(Collectors.toSet()))
                .planName("Unlimited (Self-Hosted)")
                .expirationDate(null) // Never expires
                .usersCount(999999)
                .build();
    }

    public boolean isSSOEnabled() {
        return true;
    }

    public boolean hasEntitlement(LicenseEntitlement entitlement) {
        return true;
    }
}