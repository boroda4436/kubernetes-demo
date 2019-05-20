package com.boroda.kubernetes.demo.factory;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import io.fabric8.kubernetes.client.utils.ImpersonatorInterceptor;
import okhttp3.Authenticator;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import static com.google.api.client.util.Strings.isNullOrEmpty;

public class KubernetesClientFactory {
    /** {@link OkHttpClient} instance shared by all Kubernetes clients. */
    private OkHttpClient httpClient;
    /**
     * Default Kubernetes {@link Config} that will be the base configuration to create per-workspace
     * configurations.
     */
    private Config defaultConfig;

    public KubernetesClientFactory() {
    }

    public KubernetesClientFactory(String masterUrl, String caCert) {
        this.defaultConfig = buildDefaultConfig(masterUrl, caCert);

        OkHttpClient temporary = HttpClientUtils.createHttpClient(defaultConfig);
        OkHttpClient.Builder builder = temporary.newBuilder();
        ConnectionPool oldPool = temporary.connectionPool();
        oldPool.evictAll();
        this.httpClient = builder.build();
    }

    /**
     * Creates an instance of {@link KubernetesClient} that can be used to perform any operation
     * <strong>that is not related to a given workspace</strong>. </br> For all operations performed
     * in the context of a given workspace (workspace start, workspace stop, etc ...), the {@code
     * create(String workspaceId)} method should be used to retrieve a Kubernetes client.
     */
    public KubernetesClient create() {
        OkHttpClient clientHttpClient =
            httpClient.newBuilder().authenticator(Authenticator.NONE).build();
        OkHttpClient.Builder builder = clientHttpClient.newBuilder();
        builder.interceptors().clear();
        clientHttpClient =
            builder
                .addInterceptor(new ImpersonatorInterceptor(defaultConfig))
                .build();

        return new DefaultKubernetesClient(clientHttpClient, defaultConfig);

    }

    /**
     * Builds the default Kubernetes {@link Config} that will be the base configuration to create
     * per-workspace configurations.
     */
    private Config buildDefaultConfig(String masterUrl, String caCert) {
        ConfigBuilder configBuilder = new ConfigBuilder();
        if (!isNullOrEmpty(masterUrl)) {
            configBuilder.withMasterUrl(masterUrl);
        }

        configBuilder.withTrustCerts(true);
        configBuilder.withCaCertData(caCert);

        return configBuilder.build();
    }

}
