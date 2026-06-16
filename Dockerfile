ARG TEMURIN_IMAGE=eclipse-temurin:21-jre
FROM ${TEMURIN_IMAGE}

WORKDIR /app

COPY build/libs/invoice.jar app.jar

EXPOSE 3201

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
