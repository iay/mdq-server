#
# Dockerfile for mdq-server.
#
# Performed as a two-stage build so that we can use Maven to generate the application
# but not have it (and the things it downloads) clutter up the deployed image.
#

#
# Build the .jar file in a build container. Run this under the build platform
# even if we're generating an image for an emulated target platform.
#
FROM --platform=$BUILDPLATFORM maven:3.8.5-openjdk-17 AS builder

LABEL maintainer="Ian Young <ian@iay.org.uk>"

WORKDIR /application
COPY pom.xml ./
COPY src src

#
# Maven configuration from the host, including an access token
# for the ukf/packages registry on GitHub.
#
COPY m2 /root/.m2

RUN mvn --batch-mode \
    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
    package

#
# Extract the layers.
#
RUN java -Djarmode=layertools \
    -jar target/mdq-server-0.1.0-SNAPSHOT.jar \
    extract

#
# Build the deployable image.
#
FROM amazoncorretto:17 as deploy

LABEL maintainer="Ian Young <ian@iay.org.uk>"

#
# Copy the layers extracted from the JAR.
#
WORKDIR /application
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./

#
# At this point, we have:
#
# /application/org... containing the Spring Boot loader
# /application/META-INF containing the application metadata
# /application/BOOT-INF containing the application and its dependencies
# /application/BOOT-INF/classes contain the application classes and resources
#
# To customise, add layers modifying /application/BOOT-INF/classes...
#

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
