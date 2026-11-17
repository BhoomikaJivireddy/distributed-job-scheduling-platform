# Distributed Job Scheduling and Execution Platform

This is a project I built to understand how distributed job processing systems work.

The platform allows users to submit jobs and process them asynchronously using multiple independent workers. It also handles job failures, retries and worker failures.

## Tech Stack

**Backend**
- Java 21
- Spring Boot
- Spring Data JPA
- Maven

**Frontend**
- React
- JavaScript
- HTML and CSS

**Database and Messaging**
- MySQL
- RabbitMQ
- Flyway

**Infrastructure and Monitoring**
- Docker and Docker Compose
- Nginx
- Prometheus
- Grafana
- Loki and Grafana Alloy

**Testing and API Tools**
- JUnit 5
- Mockito
- Testcontainers
- Postman
- Swagger / OpenAPI

## Architecture

The application uses separate services for job management and job execution.

<img width="1536" height="1024" alt="image" src="https://github.com/user-attachments/assets/caddea9a-6ebe-4500-b320-995d97ed2a74" />


## How It Works

1. A user submits a job through the React frontend.
2. The Spring Boot backend stores the job information in MySQL.
3. RabbitMQ is used to distribute jobs to available workers.
4. Independent workers receive and execute jobs.
5. Workers report their execution results.
6. The backend updates the job status and maintains execution history.

## Features

- Asynchronous job processing
- Multiple independent worker containers
- Concurrent job execution
- Automatic retries for failed jobs
- Configurable retry limits
- Worker heartbeat monitoring
- Lease-based recovery of abandoned jobs
- Job status tracking and execution history
- Application and infrastructure monitoring
- Centralized application logs
- Docker-based service deployment
- Nginx reverse proxy

## Monitoring

Prometheus collects application and infrastructure metrics, while Grafana provides dashboards to monitor the platform.

The monitoring dashboard includes:

- Jobs submitted and completed
- Failed job attempts
- Active workers
- Queue backlog
- Job execution duration
- Worker execution statistics
- JVM memory usage

Loki and Grafana Alloy are used for centralized log collection.
<img width="1912" height="716" alt="Screenshot 2026-11-17 080530" src="https://github.com/user-attachments/assets/f98269d8-40ff-446d-93ec-dfcd463f5636" />


## Running the Project

### Prerequisites

- Java 21 (for local backend development)
- Docker Desktop
- Docker Compose
- Git

### Steps

**1. Clone the repository**

```bash
git clone https://github.com/BhoomikaJivireddy/distributed-job-scheduling-platform.git
```

**2. Open the project directory**

```bash
cd distributed-job-scheduling-platform
```

**3. Create the environment file**

Copy `.env.example` to `.env` and update the required values.

**4. Start the services**

```bash
docker compose up -d --build
```

**5. Check running containers**

```bash
docker compose ps
```

### Application URLs

| Service | URL |
|---|---|
| Frontend | http://localhost |
| Job Manager API | http://localhost:8081 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| RabbitMQ Management | http://localhost:15672 |

Use the credentials configured in your `.env` file for Grafana and RabbitMQ.

## Testing

I tested the platform with different scenarios:

- Submitted jobs and verified successful execution.
- Submitted multiple jobs to verify concurrent processing across workers.
- Simulated job failures and verified automatic retries.
- Stopped a worker during execution and verified lease-based recovery by another worker.
- Checked Prometheus targets and Grafana dashboards.

## What I Learned

While building this project, I learned about:

- Asynchronous processing
- Message queues
- Distributed worker architecture
- Concurrent job execution
- Failure handling and recovery
- Docker containerization
- Reverse proxy configuration
- Application monitoring and logging

## Repository

GitHub: https://github.com/BhoomikaJivireddy/distributed-job-scheduling-platform
