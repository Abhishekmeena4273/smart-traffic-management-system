FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/smart-traffic-management-1.0.0.jar app.jar

ENV JAVA_OPTS="-Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
