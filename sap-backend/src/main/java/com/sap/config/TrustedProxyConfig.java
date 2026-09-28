package com.sap.config;

import com.sap.util.IpUtil;
import com.sap.service.RegistrationProtectionSettingsService;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrustedProxyConfig {
    public TrustedProxyConfig(RegistrationProtectionSettingsService settings) {
        IpUtil.useTrustedProxySource(settings::trustedProxyAddresses);
    }
}
