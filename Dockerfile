FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /app

COPY --chown=nonroot:nonroot build/libs/app.jar /app/app.jar

USER nonroot

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
