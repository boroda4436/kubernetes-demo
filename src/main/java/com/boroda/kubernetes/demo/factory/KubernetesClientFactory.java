package com.boroda.kubernetes.demo.factory;

import com.boroda.kubernetes.demo.model.ClusterCredentials;

import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import static com.google.api.client.util.Strings.isNullOrEmpty;
import static okhttp3.TlsVersion.TLS_1_2;

public class KubernetesClientFactory {
    /**
     * Default Kubernetes {@link Config} that will be the base configuration to create per-workspace
     * configurations.
     */
    private Config defaultConfig;

    public KubernetesClientFactory() {
    }

    public KubernetesClientFactory(ClusterCredentials clusterCredentials) {
        this.defaultConfig = buildDefaultConfig(clusterCredentials);
    }

    /**
     * Creates an instance of {@link KubernetesClient} that can be used to perform any operation
     * <strong>that is not related to a given workspace</strong>. </br> For all operations performed
     * in the context of a given workspace (workspace start, workspace stop, etc ...), the {@code
     * create(String workspaceId)} method should be used to retrieve a Kubernetes client.
     */
    public KubernetesClient create() {
        return new AutoAdaptableKubernetesClient(this.defaultConfig);
    }

    /**
     * Builds the default Kubernetes {@link Config} that will be the base configuration to create
     * per-workspace configurations.
     */
    private Config buildDefaultConfig(ClusterCredentials clusterCredentials) {
        ConfigBuilder configBuilder = new ConfigBuilder();
        if (!isNullOrEmpty(clusterCredentials.getMasterUrl())) {
            configBuilder.withMasterUrl(clusterCredentials.getMasterUrl());
        }
        if (!isNullOrEmpty(clusterCredentials.getCaCertData())) {
            configBuilder.withCaCertData(clusterCredentials.getCaCertData());
        }
        if (!isNullOrEmpty(clusterCredentials.getPassword()) &&
            !isNullOrEmpty(clusterCredentials.getUsername()) ) {
            configBuilder.withPassword(clusterCredentials.getPassword());
            configBuilder.withUsername(clusterCredentials.getUsername());
        }
        if (!isNullOrEmpty(clusterCredentials.getClientKey()) &&
            !isNullOrEmpty(clusterCredentials.getClientCertificate()) ) {
            configBuilder.withClientKeyData(clusterCredentials.getClientKey());
            configBuilder.withClientCertData(clusterCredentials.getClientCertificate());
        }

        configBuilder.withOauthToken(clusterCredentials.getOauthToken());
        configBuilder.withTrustCerts(true);
        configBuilder.withTlsVersions(TLS_1_2);

        return configBuilder.build();
    }

}
