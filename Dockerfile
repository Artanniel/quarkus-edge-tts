FROM eclipse-temurin:21-jre-jammy

# Install python3 and edge-tts
RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip && \
    pip3 install --no-cache-dir edge-tts && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV LANGUAGE='en_US:en'
ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

WORKDIR /work/

# Copy the Quarkus fast-jar application from the app module target
COPY app/target/quarkus-app/lib/ /work/lib/
COPY app/target/quarkus-app/*.jar /work/
COPY app/target/quarkus-app/app/ /work/app/
COPY app/target/quarkus-app/quarkus/ /work/quarkus/

EXPOSE 8080
USER 1001

CMD ["java", "-Dquarkus.http.host=0.0.0.0", "-jar", "/work/quarkus-run.jar"]
