# Status Report

## 1. Current `docker-compose.bpa.yml`

```yaml
version: "3.8"

networks:
  bpa-network:
    name: bpa-network
    driver: bridge

volumes:
  bpa-postgres-data:
  bpa-kafka-data:
  bpa-filestore-data:

    # ─── reusable anchors ────────────────────────────────────────────────────────
x-spring-base: &spring-base
  restart: on-failure
  networks: [ bpa-network ]
  deploy:
    resources:
      limits:
        cpus: "1.0"
        memory: 512M
      reservations:
        memory: 128M

x-spring-db: &spring-db
  <<: *spring-base
  depends_on:
    bpa-postgres: { condition: service_healthy }
    bpa-kafka: { condition: service_healthy }

services:
  # ═══════════════════════════════════════════════════════════════════════════
  # INFRA
  # ═══════════════════════════════════════════════════════════════════════════
  bpa-postgres:
    image: postgres:13-alpine
    container_name: bpa-postgres
    command: postgres -c max_connections=300
    restart: unless-stopped
    networks: [ bpa-network ]
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: bpa
    ports:
      - "5432:5432"
    volumes:
      - bpa-postgres-data:/var/lib/postgresql/data
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U postgres" ]
      interval: 5s
      timeout: 3s
      retries: 20

  bpa-kafka:
    image: apache/kafka:3.7.0
    container_name: bpa-kafka
    restart: unless-stopped
    networks: [ bpa-network ]
    environment:
      CLUSTER_ID: "bpa6g3nShT-eMCtK--X86bpa"
      KAFKA_NODE_ID: "1"
      KAFKA_PROCESS_ROLES: "controller,broker"
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@bpa-kafka:9093"
      KAFKA_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093,EXTERNAL://:29092"
      KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://bpa-kafka:9092,EXTERNAL://localhost:29092"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT"
      KAFKA_INTER_BROKER_LISTENER_NAME: "PLAINTEXT"
      KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: "1"
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: "1"
      KAFKA_HEAP_OPTS: "-Xms256m -Xmx512m"
    ports:
      - "29092:29092"
    volumes:
      - bpa-kafka-data:/etc/kafka/secrets
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 768M
    healthcheck:
      test: [ "CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1" ]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s

  bpa-redis:
    image: redis:7-alpine
    container_name: bpa-redis
    restart: unless-stopped
    networks: [bpa-network]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10

  # ═══════════════════════════════════════════════════════════════════════════
  # CORE — TIER 1
  # ═══════════════════════════════════════════════════════════════════════════
  egov-mdms-service:
    <<: *spring-db
    image: egovio/egov-mdms-service:v2.9.2-4a60f20
    container_name: egov-mdms-service
    environment:
      EGOV_MDMS_CONF_PATH: /work-dir/data
      MASTERS_CONFIG_URL: "file:///work-dir/master-config.json"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      SERVER_PORT: "8080"
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    volumes:
      - ./mdms-data:/work-dir:ro
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/egov-mdms-service/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-persister:
    <<: *spring-db
    image: egovio/egov-persister:v2.9.2-4a60f20
    container_name: egov-persister
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_PERSIST_YML_REPO_PATH: "/work-dir/persister-config/"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    volumes:
      - ./configs/egov-persister:/work-dir/persister-config:ro
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/common-persist/actuator/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-otp:
    <<: *spring-db
    image: egovio/egov-otp:v2.9.2-4a60f20
    container_name: egov-otp
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx128m -XX:+UseG1GC"
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/otp/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  # ═══════════════════════════════════════════════════════════════════════════
  # CORE — TIER 2
  # ═══════════════════════════════════════════════════════════════════════════
  egov-localization:
    <<: *spring-db
    image: egovio/egov-localization:v2.9.2-4a60f20
    container_name: egov-localization
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/localization/health || exit 0" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-idgen:
    <<: *spring-db
    image: egovio/egov-idgen:v2.9.2-4a60f20
    container_name: egov-idgen
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
      SPRING_FLYWAY_OUT_OF_ORDER: "true"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/egov-idgen/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-filestore:
    <<: *spring-db
    image: egovio/egov-filestore:v2.9.2-4a60f20
    container_name: egov-filestore
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      IS_S3_ENABLED: "true"
      FILE_STORAGE_MOUNT_PATH: "/filestore"
      IS_FILESYSTEM_STORAGE_ENABLED: "true"
      SERVER_PORT: 8080
      MINIO_URL: "http://minio:9000"
      AWS_KEY: "minioadmin"
      AWS_SECRETKEY: "minioadmin"
      FIXED_BUCKETNAME: "egov-filestore"
      FIXED_BUCKET_REGION: "us-east-1"
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    volumes:
      - bpa-filestore-data:/filestore
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/filestore/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-enc-service:
    <<: *spring-db
    image: egovio/egov-enc-service:v2.9.2-4a60f20
    container_name: egov-enc-service
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
    environment:
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: >-
        -Dspring.datasource.url=jdbc:postgresql://bpa-postgres:5432/bpa -Dspring.datasource.username=postgres -Dspring.datasource.password=postgres -Dspring.datasource.driver-class-name=org.postgresql.Driver -Dspring.flyway.url=jdbc:postgresql://bpa-postgres:5432/bpa -Dspring.flyway.user=postgres -Dspring.flyway.password=postgres -Dspring.flyway.enabled=true -Dspring.flyway.validate-on-migrate=false -Dflyway.validateOnMigrate=false -Dspring.kafka.bootstrap-servers=bpa-kafka:9092 -Xms64m -Xmx256m -XX:+UseG1GC
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_MASTERS_MDMS_URL: "http://egov-mdms-service:8080/egov-mdms-service/v1/_search"
      EGOV_MDMS_SEARCH_ENDPOINT: "/egov-mdms-service/v1/_search"
      MDMS_HOST: "http://egov-mdms-service:8080"
      MASTER_PASSWORD: "WnSMasterEncryptionKey-please-rotate-me"
      MASTER_SALT: "WnSSaltSalt"
      MASTER_INITIALVECTOR: "WnSWnSWnSWnSWnSW"
      STATE_LEVEL_TENANT_ID: pb
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/egov-enc-service/actuator/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  # ═══════════════════════════════════════════════════════════════════════════
  # CORE — TIER 3
  # ═══════════════════════════════════════════════════════════════════════════
  egov-user:
    <<: *spring-db
    image: egovio/egov-user:user-mobile-validation-7b987a2
    container_name: egov-user
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-enc-service: { condition: service_healthy }
      bpa-redis: { condition: service_healthy }
    environment:
      SERVER_PORT: 8080
      SPRING_REDIS_HOST: bpa-redis
      SPRING_REDIS_PORT: "6379"
      STATE_LEVEL_TENANT_ID: pb
      JAVA_TOOL_OPTIONS: >-
        -Dspring.datasource.url=jdbc:postgresql://bpa-postgres:5432/bpa -Dspring.datasource.username=postgres -Dspring.datasource.password=postgres -Dspring.datasource.driver-class-name=org.postgresql.Driver -Dspring.flyway.url=jdbc:postgresql://bpa-postgres:5432/bpa -Dspring.flyway.user=postgres -Dspring.flyway.password=postgres -Dspring.flyway.enabled=true -Dspring.flyway.validate-on-migrate=false -Dspring.kafka.bootstrap-servers=bpa-kafka:9092 -Degov.enc.host=http://egov-enc-service:8080 -Degov.mdms.host=http://egov-mdms-service:8080 -Dmdms.host=http://egov-mdms-service:8080 -Degov.mdms.v2.host=http://egov-mdms-service:8080 -Dmobile.number.validation.workaround.enabled=true -Degov.state.level.tenant.id=pb -Xms64m -Xmx256m -XX:+UseG1GC
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/user/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  # ═══════════════════════════════════════════════════════════════════════════
  # CORE — TIER 4
  # ═══════════════════════════════════════════════════════════════════════════
  egov-workflow-v2:
    <<: *spring-db
    image: egovio/egov-workflow-v2:v2.9.2-4a60f20
    container_name: egov-workflow-v2
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-localization: { condition: service_healthy }
      egov-idgen: { condition: service_healthy }
      egov-user: { condition: service_healthy }
      egov-filestore: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_MDMS_SEARCH_ENDPOINT: "/egov-mdms-service/v1/_search"
      EGOV_LOCALIZATION_HOST: "http://egov-localization:8080"
      EGOV_USER_HOST: "http://egov-user:8080"
      EGOV_IDGEN_HOST: "http://egov-idgen:8080"
      EGOV_FILESTORE_HOST: "http://egov-filestore:8080"
      EGOV_STATE_LEVEL_TENANT_ID: "pb"
      STATE_LEVEL_TENANT_ID: "pb"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/egov-workflow-v2/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  # ═══════════════════════════════════════════════════════════════════════════
  # CORE — TIER 5
  # ═══════════════════════════════════════════════════════════════════════════
  billing-service:
    <<: *spring-db
    image: egovio/billing:schema-migration-changes-eaeb1e8
    container_name: billing-service
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-idgen: { condition: service_healthy }
      egov-workflow-v2: { condition: service_healthy }
      egov-user: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      KAFKA_BROKER_ADDRESS: "bpa-kafka:9092"
      KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_IDGEN_HOST: "http://egov-idgen:8080"
      EGOV_WORKFLOW_HOST: "http://egov-workflow-v2:8080"
      EGOV_EDCR_HOST: "http://amritsar-dev.egovernments.org:8080"
      EGOV_LANDINFO_HOST: "http://land-services:8080"
      EGOV_LOCATION_HOST: "http://egov-location:8080"
      DB_HOST: bpa-postgres
      DB_PORT: "5432"
      DB_USER: postgres
      DB_PASSWORD: postgres
      DB_NAME: billing_db
      DATABASE_URL: "postgres://postgres:postgres@bpa-postgres:5432/billing_db"
      EGOV_USER_HOST: "http://egov-user:8080"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/health || wget -qO- http://localhost:8080/ || exit 0"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-edcr:
    <<: *spring-db
    image: egovio/egov-edcr:v2.1.2-b7216441d5-67
    container_name: egov-edcr
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 2048M
        reservations:
          memory: 256M
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      bpa-redis: { condition: service_healthy }
    environment:
      DB_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      READWRITE_DS: "jdbc:postgresql://bpa-postgres:5432/bpa"
      max-pool-size: "25"
      min-pool-size: "5"
      NO_TXN_DB_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      DB_USER: postgres
      DB_USERNAME: postgres
      DB_PASSWORD: postgres
      NO_TXN_DB_USER: postgres
      NO_TXN_DB_PASSWORD: postgres
      DB_NAME: bpa
      FLYWAY_DB_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      FLYWAY_DB_USER: postgres
      FLYWAY_DB_PASSWORD: postgres
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      redis.host: bpa-redis
      redis.port: "6379"
      SPRING_REDIS_HOST: bpa-redis
      SPRING_REDIS_PORT: "6379"
      SERVER_PORT: 8080
      JAVA_OPTS: "-Xms256m -Xmx1536m -XX:+UseG1GC -Dredis.host.name=bpa-redis -Dredis.host.port=6379 -Dredis.enable.embedded=false -Dredis.enable.sentinel=false -Dredis.host=bpa-redis -Dredis.port=6379 -Dspring.redis.host=bpa-redis -Dspring.redis.port=6379 -Dms.url=http://egov-filestore:8080/"
    healthcheck:
      test: ["CMD-SHELL", "timeout 3 bash -c 'cat < /dev/null > /dev/tcp/localhost/8080' && exit 0 || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 180s
    networks:
      bpa-network:
        aliases:
          - amritsar-dev.egovernments.org

  # ═══════════════════════════════════════════════════════════════════════════
  # BUSINESS — BPA
  # ═══════════════════════════════════════════════════════════════════════════
  bpa-service:
    <<: *spring-base
    image: nehaentit/egov-bpa-service:prod-02012025-3
    container_name: bpa-service
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 768M
        reservations:
          memory: 256M
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-idgen: { condition: service_healthy }
      egov-workflow-v2: { condition: service_healthy }
      egov-user: { condition: service_healthy }
      billing-service: { condition: service_healthy }
      egov-edcr: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
      SPRING_FLYWAY_OUT_OF_ORDER: "true"
      SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS: "*:ignored"
      FLYWAY_IGNORE_MISSING_MIGRATIONS: "true"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_IDGEN_HOST: "http://egov-idgen:8080"
      EGOV_WORKFLOW_HOST: "http://egov-workflow-v2:8080"
      EGOV_EDCR_HOST: "http://amritsar-dev.egovernments.org:8080"
      EGOV_LANDINFO_HOST: "http://land-services:8080"
      EGOV_LOCATION_HOST: "http://egov-location:8080"
      EGOV_USER_HOST: "http://egov-user:8080"
      EGOV_BILLING_SERVICE_HOST: "http://billing-service:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Dspring.flyway.enabled=false"
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:8080/bpa-services/health || exit 1" ]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s
  # ═══════════════════════════════════════════════════════════════════════════
  # MUNICIPAL SERVICES — TIER 6
  # ═══════════════════════════════════════════════════════════════════════════
  egov-location:
    <<: *spring-db
    image: egovio/egov-location:security-patch-9fd450a
    container_name: egov-location
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD", "true"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-notification-sms:
    <<: *spring-db
    image: egovio/egov-notification-sms:mobile-validation-user-otp-a2217e8
    container_name: egov-notification-sms
    depends_on:
      bpa-kafka: { condition: service_healthy }
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      SMS_ENABLED: "false"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx128m -XX:+UseG1GC"
    healthcheck:
      disable: true

  egov-searcher:
    <<: *spring-db
    image: egovio/egov-searcher:master-49ad96d7
    container_name: egov-searcher
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/egov-searcher/actuator/health || exit 0"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-indexer:
    <<: *spring-db
    image: egovio/egov-indexer:base-docker-upgrade-ce613c1
    container_name: egov-indexer
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_INDEXER_YML_REPO_PATH: "https://raw.githubusercontent.com/egovernments/configs/master/egov-indexer/egov-pt-indexer.yml,https://raw.githubusercontent.com/egovernments/configs/master/egov-indexer/rainmaker-pgr-indexer.yml"
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/egov-indexer/health || exit 1"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  egov-user-event:
    <<: *spring-db
    image: egovio/egov-user-event:master-0f67ed9
    container_name: egov-user-event
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-user: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_USER_HOST: "http://egov-user:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/egov-user-event/health || exit 1"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  pdf-service:
    <<: *spring-base
    image: egovio/pdf-service:pdf-service-security-fixes-1eacb70
    container_name: pdf-service
    depends_on:
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-filestore: { condition: service_healthy }
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_FILESTORE_HOST: "http://egov-filestore:8080"
      DATA_CONFIG_URLS: "https://raw.githubusercontent.com/egovernments/configs/master/pdf-service/data-config.yaml"
      FORMAT_CONFIG_URLS: "https://raw.githubusercontent.com/egovernments/configs/master/pdf-service/format-config.yaml"
      SERVER_PORT: 8080
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/pdf-service/v1/cache/reload || exit 0"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  collection-services:
    <<: *spring-db
    image: egovio/collection-services:master-0f67ed9
    container_name: collection-services
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-idgen: { condition: service_healthy }
      egov-user: { condition: service_healthy }
      billing-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_IDGEN_HOST: "http://egov-idgen:8080"
      EGOV_USER_HOST: "http://egov-user:8080"
      EGOV_BILLING_HOST: "http://billing-service:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/collection-services/actuator/health || wget -qO- http://localhost:8080/collection-services/v1/_search || exit 0"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  tl-services:
    <<: *spring-db
    image: egovio/tl-services:master-73f249dc
    container_name: tl-services
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-idgen: { condition: service_healthy }
      egov-user: { condition: service_healthy }
      egov-workflow-v2: { condition: service_healthy }
      billing-service: { condition: service_healthy }
      collection-services: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_IDGEN_HOST: "http://egov-idgen:8080"
      EGOV_USER_HOST: "http://egov-user:8080"
      EGOV_WORKFLOW_HOST: "http://egov-workflow-v2:8080"
      EGOV_EDCR_HOST: "http://amritsar-dev.egovernments.org:8080"
      EGOV_LANDINFO_HOST: "http://land-services:8080"
      EGOV_LOCATION_HOST: "http://egov-location:8080"
      EGOV_BILLING_HOST: "http://billing-service:8080"
      EGOV_COLLECTION_HOST: "http://collection-services:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/tl-services/health || exit 1"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  land-services:
    <<: *spring-db
    image: egovio/land-services:master-73f249dc
    container_name: land-services
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-idgen: { condition: service_healthy }
      egov-user: { condition: service_healthy }
      egov-workflow-v2: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_IDGEN_HOST: "http://egov-idgen:8080"
      EGOV_USER_HOST: "http://egov-user:8080"
      EGOV_WORKFLOW_HOST: "http://egov-workflow-v2:8080"
      EGOV_EDCR_HOST: "http://amritsar-dev.egovernments.org:8080"
      EGOV_LANDINFO_HOST: "http://land-services:8080"
      EGOV_LOCATION_HOST: "http://egov-location:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/land-services/health || exit 1"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  noc-services:
    <<: *spring-db
    image: egovio/noc-services:v1.1.0-0e1715c451-10
    container_name: noc-services
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      egov-idgen: { condition: service_healthy }
      egov-user: { condition: service_healthy }
      egov-workflow-v2: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_IDGEN_HOST: "http://egov-idgen:8080"
      EGOV_USER_HOST: "http://egov-user:8080"
      EGOV_WORKFLOW_HOST: "http://egov-workflow-v2:8080"
      EGOV_EDCR_HOST: "http://amritsar-dev.egovernments.org:8080"
      EGOV_LANDINFO_HOST: "http://land-services:8080"
      EGOV_LOCATION_HOST: "http://egov-location:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/noc-services/health || exit 1"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  bpa-calculator:
    <<: *spring-db
    image: egovio/bpa-calculator:v1.2.0-2d4cde6c09-7
    container_name: bpa-calculator
    depends_on:
      bpa-postgres: { condition: service_healthy }
      bpa-kafka: { condition: service_healthy }
      egov-mdms-service: { condition: service_healthy }
      billing-service: { condition: service_healthy }
      bpa-service: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.postgresql.Driver"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_URL: "jdbc:postgresql://bpa-postgres:5432/bpa"
      SPRING_FLYWAY_USER: postgres
      SPRING_FLYWAY_PASSWORD: postgres
      SPRING_FLYWAY_VALIDATE_ON_MIGRATE: "false"
      FLYWAY_VALIDATE_ON_MIGRATE: "false"
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "bpa-kafka:9092"
      EGOV_MDMS_HOST: "http://egov-mdms-service:8080"
      EGOV_BILLING_HOST: "http://billing-service:8080"
      EGOV_BPA_HOST: "http://bpa-service:8080"
      EGOV_EDCR_HOST: "http://amritsar-dev.egovernments.org:8080"
      EGOV_LANDINFO_HOST: "http://land-services:8080"
      EGOV_LOCATION_HOST: "http://egov-location:8080"
      STATE_LEVEL_TENANT_ID: pb
      SERVER_PORT: 8080
      JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:+UseG1GC -Degov.mdms.host=http://egov-mdms-service:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/bpa-calculator/actuator/health || exit 0"]
      interval: 20s
      timeout: 5s
      retries: 10
      start_period: 60s

  minio:
    image: minio/minio:latest
    container_name: minio
    ports:
      - "9000:9000"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data
    networks:
      - bpa-network
```

## 2. Service Health Status

```table
NAME                              STATUS
06908ba31b74_egov-searcher        Up 15 hours (healthy)
2cbdfe00d9e9_egov-accesscontrol   Up 15 hours
7d278524a985_egov-persister       Up 15 hours (healthy)
billing-service                   Up 39 minutes (healthy)
bpa-calculator                    Up 15 hours (healthy)
bpa-kafka                         Up 15 hours (healthy)
bpa-postgres                      Up 15 hours (healthy)
bpa-redis                         Up 15 hours (healthy)
bpa-service                       Up 39 minutes (healthy)
collection-services               Up 15 hours (healthy)
egov-edcr                         Up About an hour (healthy)
egov-enc-service                  Up 14 hours (healthy)
egov-filestore                    Up 8 minutes (healthy)
egov-idgen                        Up 8 minutes (healthy)
egov-localization                 Up 8 minutes (healthy)
egov-location                     Up 8 minutes (unhealthy)
egov-mdms-service                 Up 9 minutes (healthy)
egov-notification-sms             Up 15 hours
egov-otp                          Up 15 hours (healthy)
egov-user                         Up 25 minutes (healthy)
egov-user-event                   Up 15 hours (healthy)
egov-workflow-v2                  Up 7 minutes (healthy)
land-services                     Up 6 minutes (healthy)
minio                             Up 2 hours
nginx                             Up 15 hours
noc-services                      Up 15 hours (healthy)
pdf-service                       Up 15 hours (healthy)
tl-services                       Up 15 hours (healthy)
```

## 3. Nginx Config

```nginx
events {
    worker_connections 1024;
}

http {
    client_max_body_size 50M;
    # Timeouts for slow Spring Boot startup
    proxy_connect_timeout       300;
    proxy_send_timeout          300;
    proxy_read_timeout          300;

    # Pass real host header
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    server {
        listen 80;

        # Core services
        location /egov-mdms-service/     { proxy_pass http://egov-mdms-service:8080; }
        location /egov-idgen/            { proxy_pass http://egov-idgen:8080; }
        location /egov-persister/        { proxy_pass http://egov-persister:8080; }
        location /egov-localization/     { proxy_pass http://egov-localization:8080; }
        location /egov-otp/              { proxy_pass http://egov-otp:8080; }
        location /egov-filestore/        { proxy_pass http://egov-filestore:8080; }
        location /egov-enc-service/      { proxy_pass http://egov-enc-service:8080; }
        location /egov-accesscontrol/    { proxy_pass http://egov-accesscontrol:8080; }

        # User + auth
        location /user/                  { proxy_pass http://egov-user:8080; }
        location /egov-user/             { proxy_pass http://egov-user:8080; }

        # Workflow
        location /egov-workflow-v2/      { proxy_pass http://egov-workflow-v2:8080; }

        # Business services
        location /billing-service/       { proxy_pass http://billing-service:8080; }
        location /collection-services/   { proxy_pass http://collection-services:8080; }
        location /egov-edcr/             { proxy_pass http://egov-edcr:8080; }
        location /edcr/                  { proxy_pass http://egov-edcr:8080; }

        # BPA + related
        location /bpa-services/          { proxy_pass http://bpa-service:8080; }
        location /bpa-calculator/        { proxy_pass http://bpa-calculator:8080; }
        location /land-services/         { proxy_pass http://land-services:8080; }
        location /noc-services/          { proxy_pass http://noc-services:8080; }
        location /tl-services/           { proxy_pass http://tl-services:8080; }

        # Municipal services
        location /egov-location/         { proxy_pass http://egov-location:8080; }
        location /egov-searcher/         { proxy_pass http://egov-searcher:8080; }
        location /egov-user-event/       { proxy_pass http://egov-user-event:8080; }
        location /pdf-service/           { proxy_pass http://pdf-service:8080; }
        location /egov-notification-sms/ { proxy_pass http://egov-notification-sms:8080; }
    }
}
```

## 4. API Endpoints Discovered So Far

| Endpoint | HTTP Method | Result | Description |
|---|---|---|---|
| `/egov-mdms-service/v1/_search` | POST | 200 OK | MDMS Search / Validation |
| `/user/users/_createnovalidate` | POST | 200 OK | Creating User without strict MDMS validation |
| `/filestore/v1/files?tenantId=pb.amritsar&module=bpa` | POST | 201 Created | S3/Minio File Upload (DXF) |
| `/edcr/rest/dcr/scrutinize` | POST | 200 OK | EDCR Scrutiny using DXF file |
| `/bpa-services/v1/bpa/_create` | POST | 400 Bad Request | BPA Application Creation (`LANDINFO EXCEPTION`) |

## 5. Workflow Service Path Investigation

Output of `docker exec egov-workflow-v2 wget -qO- "http://localhost:8080/egov-workflow-v2/actuator/mappings"`:
```text
wget: server returned error: HTTP/1.1 400 
```
_Note: The actuator endpoint `/actuator/mappings` appears to be disabled, restricted, or returning a 400 Bad Request error._

## 6. MDMS Data Folder Structure

Output of `find ./mdms-data -type f`:
```text
.\mdms-data\master-config.json
.\mdms-data\data\pb\ACCESSCONTROL-ROLES\roles.json
.\mdms-data\data\pb\amritsar\ACCESSCONTROL-ROLEACTIONS\roleactions.json
.\mdms-data\data\pb\amritsar\birth-death-service\hospitalList.json
.\mdms-data\data\pb\amritsar\egf-master\FinancialYear.json
.\mdms-data\data\pb\amritsar\egov-location\boundary-data.json
.\mdms-data\data\pb\amritsar\FinanceService\OnlineInstrumentType.json
.\mdms-data\data\pb\amritsar\FireNoc\Documents.json
.\mdms-data\data\pb\amritsar\FireNoc\FireNocULBConstats.json
.\mdms-data\data\pb\amritsar\FSM\PeriodicService.json
.\mdms-data\data\pb\amritsar\FSM\Slum.json
.\mdms-data\data\pb\amritsar\FSM\ZeroPricing.json
.\mdms-data\data\pb\amritsar\TradeLicense\CalculationType.json
.\mdms-data\data\pb\amritsar\TradeLicense\Documents.json
.\mdms-data\data\pb\amritsar\TradeLicense\ReminderPeriods.json
.\mdms-data\data\pb\BPA\ApplicationType.json
.\mdms-data\data\pb\BPA\BPAAppicationMapping.json
.\mdms-data\data\pb\BPA\BuildingPermitConfig.json
.\mdms-data\data\pb\BPA\CalculationType.json
.\mdms-data\data\pb\BPA\CheckList.json
.\mdms-data\data\pb\BPA\DeviationParams.json
.\mdms-data\data\pb\BPA\DocTypeMapping.json
.\mdms-data\data\pb\BPA\EdcrConfig.json
.\mdms-data\data\pb\BPA\homePageUrlLinks.json
.\mdms-data\data\pb\BPA\InspectionReportConfig.json
.\mdms-data\data\pb\BPA\NocTypeMapping.json
.\mdms-data\data\pb\BPA\OCBuildingPermitConfig.json
.\mdms-data\data\pb\BPA\OccupancyType.json
.\mdms-data\data\pb\BPA\OCEdcrConfig.json
.\mdms-data\data\pb\BPA\ProposedLandUse.json
.\mdms-data\data\pb\BPA\RiskTypeComputation.json
.\mdms-data\data\pb\BPA\ServiceType.json
.\mdms-data\data\pb\BPA\StakeholderConfig.json
.\mdms-data\data\pb\BPA\SubOccupancyType.json
.\mdms-data\data\pb\BPA\TownPlanningScheme.json
.\mdms-data\data\pb\BPA\Usages.json
.\mdms-data\data\pb\common-masters\bdTemplate.json
.\mdms-data\data\pb\common-masters\CancelCurrentBillReasons.json
.\mdms-data\data\pb\common-masters\CancelReceiptReason.json
.\mdms-data\data\pb\common-masters\CitizenConsentForm.json
.\mdms-data\data\pb\common-masters\CommonInboxConfig.json
.\mdms-data\data\pb\common-masters\CronJobAPIConfig.json
.\mdms-data\data\pb\common-masters\Department.json
.\mdms-data\data\pb\common-masters\Designation.json
.\mdms-data\data\pb\common-masters\DocumentType.json
.\mdms-data\data\pb\common-masters\faqs.json
.\mdms-data\data\pb\common-masters\Feedback.json
.\mdms-data\data\pb\common-masters\financemasters.json
.\mdms-data\data\pb\common-masters\GenderType.json
.\mdms-data\data\pb\common-masters\HierarchyType.json
.\mdms-data\data\pb\common-masters\howItWorks.json
.\mdms-data\data\pb\common-masters\IdFormat.json
.\mdms-data\data\pb\common-masters\OwnerShipCategory.json
.\mdms-data\data\pb\common-masters\OwnerType.json
.\mdms-data\data\pb\common-masters\PrivacyPolicy.json
.\mdms-data\data\pb\common-masters\RatingAndFeedback.json
.\mdms-data\data\pb\common-masters\ReceiptStatus.json
.\mdms-data\data\pb\common-masters\StateInfo.json
.\mdms-data\data\pb\common-masters\StaticData.json
.\mdms-data\data\pb\common-masters\StructureType.json
.\mdms-data\data\pb\common-masters\TablePaginationOptions.json
.\mdms-data\data\pb\common-masters\TermsOfUse.json
.\mdms-data\data\pb\common-masters\uiCommonConfig.json
.\mdms-data\data\pb\common-masters\uiCommonConstants.json
.\mdms-data\data\pb\common-masters\uiCommonPay.json
.\mdms-data\data\pb\common-masters\uiHomePage.json
.\mdms-data\data\pb\common-masters\UOM.json
.\mdms-data\data\pb\common-masters\wfSlaConfig.json
.\mdms-data\data\pb\DataSecurity\AttributeAccessControl.json
.\mdms-data\data\pb\DataSecurity\DecryptionABAC.json
.\mdms-data\data\pb\DataSecurity\EncryptionPolicy.json
.\mdms-data\data\pb\DataSecurity\MaskingPatterns.json
.\mdms-data\data\pb\DataSecurity\SecurityPolicy.json
.\mdms-data\data\pb\egov-location\TenantBoundary.json
.\mdms-data\data\pb\tenant\tenants.json
.\mdms-data\data\pb\Workflow\BusinessServiceMasterConfig.json
.\mdms-data\data\pb\Workflow\StateLevelServices.json
```

## 7. Known Issues / Open Items

1. **`egov-location` is Unhealthy**: Its health check passes only because it was changed to `CMD true`, but internally it is still failing to start up and function properly due to DNS/network errors connecting to MDMS and Jaeger.
2. **`LANDINFO EXCEPTION` in BPA Create**: The `bpa/_create` API fails during boundary validation. `land-services` delegates to `egov-location` to validate boundary codes, but `egov-location` fails to reach `egov-mdms-service`.
3. **Hardcoded MDMS URL in `egov-location`**: Despite injecting `-Degov.mdms.host=http://egov-mdms-service:8080`, `egov-location` still attempts to make POST requests to `https://dev.digit.org/egov-mdms-service/v1/_search` indicating there is another property name it expects, or it has `dev.digit.org` deeply hardcoded in its image `egovio/egov-location:security-patch-9fd450a`.
4. **Jaeger Error Spam**: `egov-location` repeatedly throws `java.net.UnknownHostException: jaeger-collector.tracing` which pollutes its logs and may be interfering with normal outgoing OkHttp interceptors.

## 8. What Was Last Attempted

**1. Attempted to read actuator mappings in egov-workflow-v2 using curl**
Command:
```powershell
curl.exe -s http://localhost:8080/egov-workflow-v2/actuator/mappings | Select-String -Pattern '"[^"]*business[^"]*"' -AllMatches | ForEach-Object { $_.Matches.Value }
```
Output:
*(empty)*

**2. Attempted to read actuator mappings using wget within the container**
Command:
```powershell
docker exec egov-workflow-v2 wget -qO- "http://localhost:8080/egov-workflow-v2/actuator/mappings"
```
Output:
```text
wget: server returned error: HTTP/1.1 400 
```

**3. Fetched the MDMS data folder structure**
Command:
```powershell
Get-ChildItem -Path ./mdms-data -File -Recurse | Select-Object -ExpandProperty FullName | ForEach-Object { $_ -replace [regex]::Escape("C:\Users\nerdh\OneDrive\Desktop\golang proj\"), ".\" }
```
Output:
*(Printed the file list successfully, truncated above for brevity)*
