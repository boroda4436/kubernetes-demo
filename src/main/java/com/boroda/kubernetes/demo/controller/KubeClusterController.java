package com.boroda.kubernetes.demo.controller;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import com.boroda.kubernetes.demo.factory.KubernetesClientFactory;
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

import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Controller
@RequestMapping(value = "/cluster")
@PropertySource("classpath:application.properties")
public class KubeClusterController {

    private static final String CLUSTER_NAME = "cbs-cluster";
    private static final String NAMESPACE_NAME = "cbs-namespace";
    private static final String ZONE = "us-central1-b";
    private static final String CLUSTER_VERSION = "1.12.7-gke.10";

    @Value("${k8s.project_name}")
    private String projectName;

    @GetMapping("/create-default")
    public String installBasicCluster(Model model) throws IOException,
        GeneralSecurityException{

        CreateClusterRequest requestBody = new CreateClusterRequest();
        Cluster cluster = new Cluster();
        cluster.setName(CLUSTER_NAME);
        cluster.setInitialClusterVersion(CLUSTER_VERSION);
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

    @GetMapping("/get-services")
    public String getServiceList()
        throws IOException, GeneralSecurityException {
        Container containerService = createContainerService();
        Container.Projects.Zones.Clusters.Get getRequest = containerService.projects()
            .zones().clusters().get(projectName, ZONE, CLUSTER_NAME);
        Cluster cluster = getRequest.execute();

        KubernetesClientFactory clientFactory = new KubernetesClientFactory(cluster.getEndpoint(),
            cluster.getMasterAuth().getClusterCaCertificate());
        try (KubernetesClient client = clientFactory.create()) {
            ServiceList myNsServices = client.services().inNamespace("default").list();
            return myNsServices.toString();
        }

    }

    @GetMapping("/create-default-namespace")
    public String createBasicNamespace()
        throws IOException, GeneralSecurityException {

        Container containerService = createContainerService();
        Container.Projects.Zones.Clusters.Get getRequest = containerService.projects()
            .zones().clusters().get(projectName, ZONE, CLUSTER_NAME);
        Cluster cluster = getRequest.execute();

        KubernetesClientFactory clientFactory = new KubernetesClientFactory(cluster.getEndpoint(),
            cluster.getMasterAuth().getClusterCaCertificate());
        try (KubernetesClient client = clientFactory.create()) {
            client
                .namespaces()
                .createNew()
                .withNewMetadata()
                .withName(NAMESPACE_NAME)
                .endMetadata()
                .done();
        }

        return "Successfully created default namespace with name " +
            NAMESPACE_NAME + ". <a href=\"/cluster/create-default-namespace\">CREATE " +
            "NAMESPACE</a>";
    }


    private Container createContainerService() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        File credentialsJson = new ClassPathResource("google_credentials.json").getFile();
        final InputStream targetStream =
            new DataInputStream(new FileInputStream(credentialsJson));

        GoogleCredential credential = GoogleCredential.fromStream(targetStream);
        if (credential.createScopedRequired()) {
            credential =
                credential.createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform",
                    "https://www.googleapis.com/auth/devstorage.read_write"));
        }

        return new Container.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Google-ContainerSample/0.1")
            .build();
    }
}
