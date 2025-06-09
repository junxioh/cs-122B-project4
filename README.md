# CS 122B Project 5


- #### Names:
  Junxiong Huang, Issac Zhang

- #### Project 5 Video Demo Link:
    Youtube link:https://youtu.be/tzeIc063oPc

- #### Collaborations and Work Distribution:
- Junxiong Huang led the project end-to-end:

- uilt and containerized the Fabflix application.

- Provisioned and configured the AWS Kubernetes cluster.

- Deployed Fabflix to K8s and refactored it into a multi-service architecture.

- Produced and edited the final video presentation.

- Issac Zhang contributed to supporting tasks, including:

- Writing configuration and deployment documentation.

- Troubleshooting IAM policies and access settings.

- erforming smoke tests and logging results.



1. This example application allows you to log in, see a movie by Eddie Murphy and leave comments.
2. In branch `Docker`, you will see how this application is turned into a docker image and deployed to AWS using Docker
3. In branch `Kubernetes`, you will see how this application is modified to be deployed using Kubernetes pods
4. In branch `Multi-Service`, you will see how this application is modified to a multi-service architecture.

## This README.md file is used for the Multi-Service branch

## Brief Explanation

- The Multi-Service branch uses Json Web Token(JWT) instead of session to store the user information. 
- We modified the `pom.xml` to compile different part of the projects into different war files.

### Json Web Token(JWT)

We will use JWT to replace session. A utility class `JwtUtil` is added to help you use JWT. 
The changes in `LoginServlet` and `LoginFilter` show you how to replace session with JWT.
- `common/JwtUtil.java`: It contains functions to generate JWT, validate JWT and set JWT into cookies.
- `login/LoginServlet.java` It shows how to generate JWT. The username and the loginTime are encoded to a JWT string. Then the JWT string is set to cookies, so the later requests will always contain the JWT string. 
- `common/LoginFilter.java`: It shows how to get JWT from cookies and how to validate the JWT string. 
- `star/SingleStarServlet.java`: It shows different way of storing states. `loginTime` is a state shared by both services, we use JWT to store it. `accessCount` is a state for star service only, we store it in session and use sticky session to ensure the requests of a client always go to the same pod under star service.

### Maven Profiles
The original Maven configuration will compile everything in the codebase into a war file. Using Maven Profiles, we can compile different part of project into different war files.
In this branch, we split `/api/login` endpoint to a login profile, and the other endpoints to a star profile.
- First, split the source files into different packages. Note that we have a package called `common`, which is shared among different profiles.
- Next, modify the `pom.xml` to set up different profiles. 
  - Line 46: we change the sourceDirectory from `src` to a parameter `${endpointDir}`. You can set different value to this parameter for different profiles. 
  - Line 61: we set a parameter `${excludes}`, which can be used to exclude some static files inside `WebContent`.
  - Line 64-81 show how to add common package to all profiles
  - Line 85-107 show how to use Profiles. For each profile we define the value of `endpointDir` and `excludes`.

The `Dockerfile` is also updated. At line 7 we defined an argument `MVN_PROFILE`, you can set its value when building an image.

## Build different profiles
- Compile different part of the project into war file with
  ```
  mvn package -P ${profileName}
  ``` 
- Login endpoint: 
  ```
  mvn package -P login
  ```
- Star endpoints:
  ```
  mvn package -P star
  ```

If you see errors in Intellij, open the Maven panel at the right. Expand "Profiles" and select only "default". Then reload the Maven Project.

## Build different Docker images

- Build the image for login endpoint with 
  ```
  sudo docker build . --build-arg MVN_PROFILE=login --platform linux/amd64 -t <DockerHub-user-name>/cs122b-p5-murphy-login:v1
  ```
  - We specify the Maven profile name with `--build-arg MVN_PROFILE=${profileName}`
- Push the image to DockerHub with 
  ```
  sudo docker push <DockerHub-user-name>/cs122b-p5-murphy-login:v1
  ```
- Repeat the steps for star endpoint:
  ```
  sudo docker build . --build-arg MVN_PROFILE=star --platform linux/amd64 -t <DockerHub-user-name>/cs122b-p5-murphy-star:v1
  ```
  ```
  sudo docker push <DockerHub-user-name>/cs122b-p5-murphy-star:v1
  ```

# Fabflix Multi-Service Deployment Guide

This repository contains Kubernetes deployment scripts for the Fabflix application with microservices architecture.

## Pre-requisites

1. **AWS Kubernetes Cluster Setup**
   - Make sure you have an AWS Kubernetes cluster set up (follow Task 2.1 and 2.2 from Project 5 in your AWS Ubuntu instance).

2. **Create Docker Registry Secret**
   ```bash
   kubectl create secret docker-registry regcred \
     --docker-server=https://index.docker.io/v1/ \
     --docker-username=wanli945 \
     --docker-password=<YOUR_DOCKER_PASSWORD> \
     --docker-email=<YOUR_DOCKER_EMAIL>
   ```

3. **Set up MySQL Database**
   ```bash
   helm install mysql \
     --set auth.rootPassword=root,auth.database=moviedb,auth.username=fabflix,auth.password='Fabflix@123',secondary.persistence.enabled=true,secondary.persistence.size=2Gi,primary.persistence.enabled=true,primary.persistence.size=2Gi,architecture=replication,auth.replicationPassword=fabflix,secondary.replicaCount=1 \
     oci://registry-1.docker.io/bitnamicharts/mysql
   ```

4. **Verify MySQL Deployment**
   ```bash
   kubectl get pods
   ```
   Both MySQL pods should be in RUNNING state and READY (1/1).

5. **Populate MySQL Database**
   ```bash
   # Access MySQL pod
   kubectl exec -it pod/mysql-primary-0 -- /bin/bash
   
   # Connect to MySQL
   mysql -u root -p
   # Enter password: root
   
   # Import your database schema and data
   # ...
   
   # Grant privileges to fabflix user
   GRANT ALL PRIVILEGES ON * . * TO 'fabflix'@'%';
   FLUSH PRIVILEGES;
   ```

6. **Enable Ingress in AWS Cluster**
   ```bash
   helm upgrade --install ingress-nginx ingress-nginx \
     --repo https://kubernetes.github.io/ingress-nginx \
     --namespace ingress-nginx --create-namespace
   ```

## Deployment Steps

1. **Build Docker Images**
   ```bash
   # Set UTF-8 encoding
   export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
   
   # Build login service
   mvn clean package -P login
   
   # Build movies service
   mvn clean package -P movies
   
   # Build Docker images
   docker build -f Dockerfile.login -t wanli945/fabflix-login:latest .
   docker build -f Dockerfile.movies -t wanli945/fabflix-movies:latest .
   
   # Push images to Docker Hub
   docker push wanli945/fabflix-login:latest
   docker push wanli945/fabflix-movies:latest
   ```

2. **Deploy Services**
   ```bash
   kubectl apply -f fabflix-multi.yaml
   ```

3. **Verify Deployment**
   ```bash
   kubectl get pods
   ```
   Both fabflix-login and fabflix-movies pods should be in RUNNING state and READY (1/1).

4. **Check Logs for Errors**
   ```bash
   kubectl logs deployment/fabflix-login
   kubectl logs deployment/fabflix-movies
   ```
   
   If you see JDBC connection exceptions, check your database configuration:
   - Verify username, password, and database name
   - Check user permissions
   - After fixing errors, redeploy:
     ```bash
     kubectl delete -f fabflix-multi.yaml
     kubectl apply -f fabflix-multi.yaml
     ```

5. **Deploy Ingress**
   ```bash
   kubectl apply -f ingress-multi.yaml 
   ```

6. **Get Access URL**
   ```bash
   kubectl get ingress
   ```
   After a couple of minutes, you should see an ADDRESS. You can access the application at:
   - Login service: `http://<AWS_ELB_URL>/fabflix-login/login.html`
   - Movies service: `http://<AWS_ELB_URL>/fabflix-movies/`

7. **Verify Session Stickiness**
   - The application uses cookie-based session affinity
   - You can inspect cookies to find the `fabflix_session` cookie
   - This cookie ensures your requests are routed to the same pod

## Troubleshooting

1. **Pods in PENDING state**
   ```bash
   kubectl describe pod <POD_NAME>
   ```
   Check the pod events to identify resource constraints or other issues.

2. **Database Connection Issues**
   - Verify database credentials in context.xml
   - Check if MySQL service is running
   - Ensure database schema is properly imported

3. **404 Errors**
   - Check WAR file structure
   - Verify Ingress path configuration
   - Check application context paths in Tomcat

4. **Scaling Services**
   ```bash
   kubectl scale deployment fabflix-movies --replicas=3
   ```

5. **Viewing All Resources**
   ```bash
   kubectl get all
   ```

## Architecture

The Fabflix application is split into two microservices:
1. **Login Service**: Handles user authentication and JWT token generation
2. **Movies Service**: Handles movie browsing, search, and purchase functionality

Both services use JWT for stateless authentication, allowing them to scale independently.

## Performance Testing Results with Jmeter

Using the `fabflix-test.jmx` script with 5 concurrent users and a single iteration (loop=1), we observed:

- **/fabflix/movie-list.html**: throughput of approximately **120 requests/s**
- **/fabflix/payment**: throughput of approximately **45 requests/s**
