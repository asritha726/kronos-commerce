# Multi-stage build for lightweight production deployment
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY . .

# Compile Java backend
RUN mkdir -p backend/bin && \
    javac -d backend/bin backend/src/com/fintech/oms/*.java

# Production runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy compiled classes and web assets
COPY --from=builder /app/backend/bin ./backend/bin
COPY --from=builder /app/frontend ./frontend
COPY --from=builder /app/database ./database

# Expose port (dynamic cloud support)
EXPOSE 8080
ENV PORT=8080

CMD ["java", "-cp", "backend/bin", "com.fintech.oms.Main"]
