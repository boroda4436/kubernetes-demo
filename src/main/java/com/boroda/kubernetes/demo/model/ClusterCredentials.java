package com.boroda.kubernetes.demo.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClusterCredentials {
    private String masterUrl;
    private String username;
    private String password;
    private String caCertData;
    private String clientKey;
    private String clientCertificate;
    private String oauthToken;
}
