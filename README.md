# DIGIT OSS Local Deployment

> [!WARNING]  
> **CRITICAL: Docker VHD Growth on Windows/WSL2**
>
> If JVM services in this stack fail to connect to dependencies (like databases, Kafka, or external configuration URLs) they will crash-loop. Unbounded crash-looping will cause Docker's `json-file` logger to generate massive amounts of logs, rapidly bloating the `docker_data.vhdx` file until it fills your entire host SSD. 
> 
> To prevent this, our `docker-compose` files are configured with:
> - `logging: max-size: 20m, max-file: 3` for all services.
> - `restart: "no"` for all Java microservices (to fail-fast instead of silently looping).
> - `LOGGING_LEVEL_ROOT: "WARN"` to suppress INFO spam during any remaining loops.
> - `depends_on: {condition: service_healthy}` chains to ensure infra starts first.

## Starting the Stack
Ensure you have sufficient memory (at least 16GB, ideally 32GB) allocated to Docker Desktop, as this stack spins up over 20 JVM microservices.

```bash
docker-compose -f docker-compose.bpa.yml up -d
```
