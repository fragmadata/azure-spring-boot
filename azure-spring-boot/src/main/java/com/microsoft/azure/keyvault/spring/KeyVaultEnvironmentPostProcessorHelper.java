/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.keyvault.spring;

import com.microsoft.azure.AzureResponseBuilder;
import com.microsoft.azure.keyvault.KeyVaultClient;
import com.microsoft.azure.serializer.AzureJacksonAdapter;
import com.microsoft.azure.spring.support.UserAgent;
import com.microsoft.rest.RestClient;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.Assert;

class KeyVaultEnvironmentPostProcessorHelper {

    private final ConfigurableEnvironment environment;

    public KeyVaultEnvironmentPostProcessorHelper(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    public void addKeyVaultPropertySource() {
        final String clientId = getProperty(environment, Constants.AZURE_CLIENTID);
        final String vaultUri = getProperty(environment, Constants.AZURE_KEYVAULT_VAULT_URI);

        final String clientKey = environment.getProperty(
                Constants.AZURE_CLIENTKEY, String.class, null);
        final String pfxPath = environment.getProperty(
                Constants.AZURE_KEYVAULT_PFX_CERTIFICAT_PATH, String.class, null);
        final String pfxPassword = environment.getProperty(
                Constants.AZURE_KEYVAULT_PFX_CERTIFICAT_PASSWORD, String.class, "");

        final long timeAcquiringTimeoutInSeconds = environment.getProperty(
                Constants.AZURE_TOKEN_ACQUIRE_TIMEOUT_IN_SECONDS, Long.class, 60L);

        final ServiceClientCredentials credentials = getCredentials(clientId, clientKey,
                pfxPath, pfxPassword, timeAcquiringTimeoutInSeconds);

        final RestClient restClient = new RestClient.Builder().withBaseUrl(vaultUri)
                            .withCredentials(credentials)
                            .withSerializerAdapter(new AzureJacksonAdapter())
                            .withResponseBuilderFactory(new AzureResponseBuilder.Factory())
                            .withUserAgent(UserAgent.getUserAgent(Constants.AZURE_KEYVAULT_USER_AGENT,
                                    allowTelemetry(environment)))
                            .build();

        final KeyVaultClient kvClient = new KeyVaultClient(restClient);

        try {
            final MutablePropertySources sources = environment.getPropertySources();
            final KeyVaultOperation kvOperation = new KeyVaultOperation(kvClient, vaultUri);

            if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new KeyVaultPropertySource(kvOperation));
            } else {
                sources.addFirst(new KeyVaultPropertySource(kvOperation));
            }

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to configure KeyVault property source", ex);
        }
    }

    private ServiceClientCredentials getCredentials(String clientId, String clientKey,
                                                    String pfxPath, String pfxPassword,
                                                    long timeAcquiringTimeoutInSeconds) {
        final ServiceClientCredentials credentials;
        if (pfxPath != null && clientKey != null){
            throw new IllegalArgumentException(
                    "Either client-key or pfx-certificate-path can be set. Both at the same time are not allowed");
        } else if (pfxPath != null){
            credentials = new KeyVaultCertificateCredentials(clientId, pfxPath,
                    pfxPassword, timeAcquiringTimeoutInSeconds);
        } else if (clientKey != null){
            credentials = new AzureKeyVaultCredential(clientId, clientKey, timeAcquiringTimeoutInSeconds);
        } else {
            throw new IllegalArgumentException(
                    "Both properties client-key and pfx-certificate-path are null");
        }
        return credentials;
    }

    private String getProperty(ConfigurableEnvironment env, String propertyName) {
        Assert.notNull(env, "env must not be null!");
        Assert.notNull(propertyName, "propertyName must not be null!");

        final String property = env.getProperty(propertyName);

        if (property == null || property.isEmpty()) {
            throw new IllegalArgumentException("property " + propertyName + " must not be null");
        }
        return property;
    }

    private boolean allowTelemetry(ConfigurableEnvironment env) {
        Assert.notNull(env, "env must not be null!");

        return env.getProperty(Constants.AZURE_KEYVAULT_ALLOW_TELEMETRY, Boolean.class, true);
    }
}
