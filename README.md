Configuration before start:
- add your GCP creadentials to google_credentials.json file
- add your project name to k8s.project_name property in the application.properties file

1. Launch the project: com.boroda.kubernetes.demo.DemoApplication
2. To create a cluster with default name (cbs-cluster) and default version (1.12.7-gke.10) - visit http://localhost:8080/cluster/create-default
To specify cluster version, please use cluster_version request param.
For example: http://localhost:8080/cluster/create-default?cluster_version=1.11.8-gke.6
3. To create a namespace - visit http://localhost:8080/cluster/create-default-namespace


In all provided above URLs you may use cluster_name request param. For example: 
http://localhost:8080/cluster/create-default-namespace?cluster_name=mycluster