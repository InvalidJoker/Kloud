# Dockerfile für das cloud-Modul
FROM eclipse-temurin:21-jre

WORKDIR /app

# Kopiere das gebaute JAR in das Image
COPY build/libs/cloud-0.1.0-SNAPSHOT.jar app.jar

# Exponiere ggf. einen Port (z.B. 8080, falls benötigt)
# EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

