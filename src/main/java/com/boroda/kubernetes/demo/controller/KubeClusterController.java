package com.boroda.kubernetes.demo.controller;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import com.boroda.kubernetes.demo.factory.KubernetesClientFactory;
import com.boroda.kubernetes.demo.model.ClusterCredentials;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.container.Container;
import com.google.api.services.container.model.Cluster;
import com.google.api.services.container.model.CreateClusterRequest;
import com.google.api.services.container.model.Operation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.APIGroupNotAvailableException;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import lombok.extern.log4j.Log4j2;

import static com.google.common.base.Strings.isNullOrEmpty;

@Log4j2
@Controller
@RequestMapping(value = "/cluster")
@PropertySource("classpath:application.properties")
public class KubeClusterController {

    private static final String CLUSTER_NAME = "cbs-cluster";
    private static final String NAMESPACE_NAME = "cbs-namespace";
    private static final String ZONE = "us-central1-a";
    private static final String CLUSTER_VERSION = "1.12.7-gke.10";
    private static final String THISISATEST_NAMESPACE = "thisisatest";
    private static final String TESTSERVICE = "testservice";
    private static final String SERVER = "server";
    private static final String NGINX = "nginx";
    private static final String NGINX_CONTROLLER = "nginx-controller";

    @Value("${k8s.project_name}")
    private String projectName;

    @GetMapping("/create-default")
    public String installBasicCluster(Model model,
        @RequestParam(name = "cluster_version", required = false) String version,
        @RequestParam(name = "cluster_name", required = false) String name) throws IOException,
        GeneralSecurityException{

        String clusterVersion = isNullOrEmpty(version) ? CLUSTER_VERSION : version;
        String clusterName = isNullOrEmpty(name) ? CLUSTER_NAME : name;

        CreateClusterRequest requestBody = new CreateClusterRequest();
        Cluster cluster = new Cluster();
        cluster.setName(clusterName);
        cluster.setInitialClusterVersion(clusterVersion);
        cluster.setInitialNodeCount(1);
        requestBody.setCluster(cluster);

        Container containerService = createContainerService();
        Container.Projects.Zones.Clusters.Create request =
            containerService.projects().zones().clusters().create(projectName, ZONE, requestBody);

        Operation response = request.execute();

        log.info(response);

        model.addAttribute("message", "Successfully created default cluster with name " + CLUSTER_NAME);
        model.addAttribute("deploy_details", response.toPrettyString());
        return "create_namespace";
    }

    @ResponseBody
    @GetMapping("/get-services")
    public String getServiceList(@RequestParam(name = "cluster_name", required = false) String name)
        throws IOException, GeneralSecurityException {
        String clusterName = isNullOrEmpty(name) ? CLUSTER_NAME : name;

        Container containerService = createContainerService();
        Container.Projects.Zones.Clusters.Get getRequest = containerService.projects()
            .zones().clusters().get(projectName, ZONE, clusterName);
        Cluster cluster = getRequest.execute();

        ClusterCredentials clusterCredentials = getClusterCredentials(cluster);
        KubernetesClientFactory clientFactory = new KubernetesClientFactory(clusterCredentials);
        try (KubernetesClient client = clientFactory.create()) {
            ServiceList myNsServices = client.services().inNamespace("default").list();
            return myNsServices.toString();
        }
    }

    @ResponseBody
    @GetMapping("/run-test")
    public String runTest(@RequestParam(name = "cluster_name", required = false) String name)
        throws IOException, GeneralSecurityException, InterruptedException {
        String clusterName = isNullOrEmpty(name) ? CLUSTER_NAME : name;

        Container containerService = createContainerService();
        Container.Projects.Zones.Clusters.Get getRequest = containerService.projects()
            .zones().clusters().get(projectName, ZONE, clusterName);
        Cluster cluster = getRequest.execute();

        ClusterCredentials clusterCredentials = getClusterCredentials(cluster);
        KubernetesClientFactory clientFactory = new KubernetesClientFactory(clusterCredentials);
        try (KubernetesClient client = clientFactory.create()) {
            try (Watch ignored = client.replicationControllers().inNamespace(
                THISISATEST_NAMESPACE).withResourceVersion("0").watch(new Watcher<ReplicationController>() {
                @Override
                public void eventReceived(Action action, ReplicationController resource) {
                    log.info("{}: {}", action, resource);
                }

                @Override
                public void onClose(KubernetesClientException e) {
                    if (e != null) {
                        log.error(e.getMessage(), e);
                    }
                }
            })) {
                // Create a namespace for all our stuff
                Namespace ns = new NamespaceBuilder().withNewMetadata()
                    .withName(THISISATEST_NAMESPACE)
                    .addToLabels("this", "rocks")
                    .endMetadata()
                    .build();
                log("Created namespace", client.namespaces().create(ns));

                // Get the namespace by name
                log("Get namespace by name", client.namespaces().withName(
                    THISISATEST_NAMESPACE).get());
                // Get the namespace by label
                log("Get namespace by label", client.namespaces().withLabel("this", "rocks").list());

                ResourceQuota
                    quota = new ResourceQuotaBuilder()
                    .withNewMetadata()
                    .withName("pod-quota")
                    .endMetadata()
                    .withNewSpec()
                    .addToHard("pods", new Quantity("10"))
                    .endSpec()
                    .build();
                log("Create resource quota", client.resourceQuotas()
                    .inNamespace(THISISATEST_NAMESPACE).create(quota));

                try {
                    log("Get jobs in namespace", client.batch().jobs().inNamespace(
                        THISISATEST_NAMESPACE).list());
                } catch (APIGroupNotAvailableException e) {
                    log("Skipping jobs example - extensions API group not available");
                }

                // Create an RC
                ReplicationController rc = new ReplicationControllerBuilder()
                    .withNewMetadata().withName(NGINX_CONTROLLER).addToLabels(
                        SERVER, NGINX).endMetadata()
                    .withNewSpec().withReplicas(3)
                    .withNewTemplate()
                    .withNewMetadata().addToLabels(SERVER, NGINX).endMetadata()
                    .withNewSpec()
                    .addNewContainer().withName(NGINX).withImage(NGINX)
                    .addNewPort().withContainerPort(80).endPort()
                    .endContainer()
                    .endSpec()
                    .endTemplate()
                    .endSpec().build();

                log("Created RC", client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).create(rc));

                log("Created RC with inline DSL",
                    client.replicationControllers().inNamespace(
                        THISISATEST_NAMESPACE).createNew()
                        .withNewMetadata().withName("nginx2-controller").addToLabels(
                        SERVER, NGINX).endMetadata()
                        .withNewSpec().withReplicas(0)
                        .withNewTemplate()
                        .withNewMetadata().addToLabels(SERVER, "nginx2").endMetadata()
                        .withNewSpec()
                        .addNewContainer().withName(NGINX).withImage(NGINX)
                        .addNewPort().withContainerPort(80).endPort()
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec().done());

                // Get the RC by name in namespace
                ReplicationController gotRc = client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).withName(NGINX_CONTROLLER).get();
                log("Get RC by name in namespace", gotRc);
                // Dump the RC as YAML
                log("Dump RC as YAML", SerializationUtils.dumpAsYaml(gotRc));
                log("Dump RC as YAML without state", SerializationUtils.dumpWithoutRuntimeStateAsYaml(gotRc));

                // Get the RC by label
                log("Get RC by label", client.replicationControllers().withLabel(
                    SERVER, NGINX).list());
                // Get the RC without label
                log("Get RC without label", client.replicationControllers().withoutLabel(
                    SERVER, "apache").list());
                // Get the RC with label in
                log("Get RC with label in", client.replicationControllers().withLabelIn(
                    SERVER, NGINX).list());
                // Get the RC with label in
                log("Get RC with label not in", client.replicationControllers().withLabelNotIn(
                    SERVER, "apache").list());
                // Get the RC by label in namespace
                log("Get RC by label in namespace", client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).withLabel(SERVER, NGINX).list());
                // Update the RC
                client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).withName(NGINX_CONTROLLER).cascading(false).edit().editMetadata().addToLabels("new", "label").endMetadata().done();

                client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).withName(NGINX_CONTROLLER).scale(8);

                Thread.sleep(1000);

                // Update the RC - change the image to apache
                client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).withName(NGINX_CONTROLLER).edit().editSpec().editTemplate().withNewSpec()
                    .addNewContainer().withName(NGINX).withImage("httpd")
                    .addNewPort().withContainerPort(80).endPort()
                    .endContainer()
                    .endSpec()
                    .endTemplate()
                    .endSpec().done();

                Thread.sleep(1000);

                log("Updated RC");
                // Clean up the RC
                client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).withName(NGINX_CONTROLLER).delete();
                client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).withName("nginx2-controller").delete();
                log("Deleted RCs");

                //Create another RC inline
                client.replicationControllers().inNamespace(
                    THISISATEST_NAMESPACE).createNew().withNewMetadata().withName(

                    NGINX_CONTROLLER).addToLabels(
                    SERVER, NGINX).endMetadata()
                    .withNewSpec().withReplicas(3)
                    .withNewTemplate()
                    .withNewMetadata().addToLabels(SERVER, NGINX).endMetadata()
                    .withNewSpec()
                    .addNewContainer().withName(NGINX).withImage(NGINX)
                    .addNewPort().withContainerPort(80).endPort()
                    .endContainer()
                    .endSpec()
                    .endTemplate()
                    .endSpec().done();
                log("Created inline RC");

                Thread.sleep(1000);

                client.replicationControllers()
                    .inNamespace(THISISATEST_NAMESPACE)
                    .withName(NGINX_CONTROLLER)
                    .delete();
                log("Deleted RC");

                log("Created RC", client.replicationControllers()
                    .inNamespace(THISISATEST_NAMESPACE)
                    .create(rc));
                client.replicationControllers()
                    .inAnyNamespace()
                    .withLabel(SERVER, NGINX)
                    .delete();
                log("Deleted RC by label");

                log("Created RC", client.replicationControllers()
                    .inNamespace(THISISATEST_NAMESPACE)
                    .create(rc));
                client.replicationControllers()
                    .inNamespace(THISISATEST_NAMESPACE)
                    .withField("metadata.name", NGINX_CONTROLLER)
                    .delete();
                log("Deleted RC by field");

                log("Created service",
                    client.services().inNamespace(THISISATEST_NAMESPACE).createNew()
                        .withNewMetadata().withName(TESTSERVICE).endMetadata()
                        .withNewSpec()
                        .addNewPort().withPort(80).withNewTargetPort().withIntVal(80).endTargetPort().endPort()
                        .endSpec()
                        .done());
                log("Updated service", client.services().inNamespace(
                    THISISATEST_NAMESPACE).withName(TESTSERVICE).edit().editMetadata().addToLabels("test", "label").endMetadata().done());
                client.replicationControllers()
                    .inNamespace(THISISATEST_NAMESPACE)
                    .withField("metadata.name", TESTSERVICE)
                    .delete();
                log("Deleted service by field");

                log("Root paths:", client.rootPaths());

            } finally {
                // And finally clean up the namespace
                client.namespaces().withName(THISISATEST_NAMESPACE).delete();
                log("Deleted namespace");
            }
        }
        return "Done!";
    }

    @ResponseBody
    @GetMapping("/create-default-namespace")
    public String createBasicNamespace(@RequestParam(name = "cluster_name", required = false) String name)
        throws IOException, GeneralSecurityException {
        String clusterName = isNullOrEmpty(name) ? CLUSTER_NAME : name;

        Container containerService = createContainerService();
        Container.Projects.Zones.Clusters.Get getRequest = containerService.projects()
            .zones().clusters().get(projectName, ZONE, clusterName);
        Cluster cluster = getRequest.execute();

        ClusterCredentials clusterCredentials = getClusterCredentials(cluster);
        KubernetesClientFactory clientFactory = new KubernetesClientFactory(clusterCredentials);
        try (KubernetesClient client = clientFactory.create()) {
            Namespace ns = new NamespaceBuilder().withNewMetadata().withName(NAMESPACE_NAME).addToLabels("this", "rocks").endMetadata().build();
            client.namespaces().create(ns);
            Namespace namespace =
                client.namespaces().withName(NAMESPACE_NAME).get();
            log.info(namespace.toString());

            return "Successfully created default namespace with name " +
                NAMESPACE_NAME + ". Details: \n" + namespace.toString();
        }
    }

    private Container createContainerService() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        GoogleCredential credential = getGoogleCredential();

        return new Container.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Google-ContainerSample/0.1")
            .build();
    }

    private GoogleCredential getGoogleCredential() throws IOException {
        File credentialsJson = new ClassPathResource("google_credentials.json").getFile();
        final InputStream targetStream =
            new DataInputStream(new FileInputStream(credentialsJson));

        GoogleCredential credential = GoogleCredential.fromStream(targetStream);
        if (credential.createScopedRequired()) {
            credential =
                credential.createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform",
                    "https://www.googleapis.com/auth/devstorage.read_write"));
        }
        credential.refreshToken();
        return credential;
    }

    private ClusterCredentials getClusterCredentials(final Cluster cluster)
        throws IOException {
        return ClusterCredentials.builder()
            .masterUrl(cluster.getEndpoint())
            .caCertData(cluster.getMasterAuth().getClusterCaCertificate())
            .clientKey(cluster.getMasterAuth().getClientKey())
            .clientCertificate(cluster.getMasterAuth().getClientCertificate())
            .username(cluster.getMasterAuth().getUsername())
            .password(cluster.getMasterAuth().getPassword())
            .oauthToken(getGoogleCredential().getAccessToken())
            .build();
    }

    private static void log(String action, Object obj) {
        log.info("{}: {}", action, obj);
    }

    private static void log(String action) {
        log.info(action);
    }

}
