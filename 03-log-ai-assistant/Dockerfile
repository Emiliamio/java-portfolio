# ── 本地用 mvn package 打好 jar，然后 docker 直接打包进镜像
FROM amazoncorretto:17-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY target/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/api/ai/stats || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
