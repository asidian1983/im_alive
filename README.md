# im_alive

AI 기반 콘텐츠 생성 플랫폼 백엔드 API 서버

## Tech Stack

| 항목 | 기술 |
|------|------|
| Language | Java 17+ |
| Framework | Spring Boot 3.2 |
| Build | Gradle |
| Database | PostgreSQL 16 |
| Cache / Queue | Redis 7 |
| Auth | JWT (jjwt 0.12.5) + Spring Security |
| AI | Anthropic Claude API |
| Monitoring | Prometheus + Grafana + Actuator |
| Deploy | Docker, k3s (Kubernetes), GitHub Actions |

---

## Quick Start

### Prerequisites
- Java 17+
- PostgreSQL
- Redis

### Environment Variables
```bash
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-256-bit-secret-key
export REDIS_HOST=localhost
export REDIS_PORT=6379
export AI_API_KEY=sk-ant-xxxxx   # Anthropic API key (optional)
```

### Run
```bash
./gradlew bootRun
```

### Docker
```bash
docker-compose up -d
```

### Verify
```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

---

## Project Structure

```
src/main/java/com/project/
├── config/           → Security, CORS, Redis, Exception Handler, Metrics
├── controller/       → Auth, User, AI, Admin REST controllers
├── service/          → Business logic (Auth, User, AI, AiJob, Admin)
├── domain/           → JPA entities (User, RefreshToken, AiGeneration, AiJob, AdminAuditLog)
├── repository/       → Spring Data JPA repositories
├── dto/              → Request/Response DTOs (Java records)
├── infra/            → JWT, AI Client, Redis Worker, Rate Limiter
└── common/exception/ → Custom exception hierarchy
```

---

## API Endpoints

### Auth (Public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 → accessToken + refreshToken |
| POST | `/api/auth/refresh` | 토큰 갱신 (rotation) |
| POST | `/api/auth/logout` | 로그아웃 (refresh token 삭제) |

### User (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users/me` | 내 프로필 조회 |
| PATCH | `/api/users/me` | 프로필 수정 (name, password) |
| DELETE | `/api/users/me` | 계정 삭제 |

### AI (Authenticated + Rate Limit: 10/min)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ai/generate` | AI 텍스트 생성 (동기) |
| POST | `/api/ai/generate-async` | AI 텍스트 생성 (비동기, 202) |
| GET | `/api/ai/jobs/{jobId}` | 비동기 작업 상태 조회 |
| GET | `/api/ai/history` | 생성 이력 목록 (페이징) |
| GET | `/api/ai/history/{id}` | 생성 이력 상세 |
| DELETE | `/api/ai/history/{id}` | 생성 이력 삭제 |

### Admin (ROLE_ADMIN)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | 사용자 목록 (페이징) |
| GET | `/api/admin/users/{id}` | 사용자 상세 |
| PATCH | `/api/admin/users/{id}` | 사용자 수정 (name, role) + 감사 로그 |
| DELETE | `/api/admin/users/{id}` | 사용자 삭제 + 감사 로그 |
| GET | `/api/admin/stats` | 시스템 통계 |

### System (Public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | 헬스 체크 |
| GET | `/actuator/prometheus` | Prometheus 메트릭 |

---

## Architecture

### Authentication Flow
```
Client → POST /api/auth/login
Server → BCrypt 검증 → JWT accessToken(1h) + refreshToken(7d, DB UUID) 발급

Client → Authorization: Bearer <accessToken>
JwtAuthenticationFilter → 토큰 검증 → SecurityContext에 userId + ROLE 설정

Token Refresh → 기존 삭제 → 새 쌍 발급 (rotation)
Logout → 해당 사용자 refresh token 전체 삭제
```

### Async Processing (Redis Queue)
```
Client → POST /api/ai/generate-async
       → AiJob 생성 (QUEUED) → Redis LPUSH ai:jobs
       ← 202 Accepted { jobId }

AiWorker (@Scheduled 1초)
       → Redis BRPOP ai:jobs (5초 대기)
       → PROCESSING → AI API 호출
       → 성공: COMPLETED / 실패: retry < 3 → 재큐잉, ≥ 3 → FAILED

Client → GET /api/ai/jobs/{jobId} (폴링)
```

### Database Schema
```
User (1) ──── (N) RefreshToken
  ├── (1) ──── (N) AiGeneration ──── (1:1) AiJob
  └── (1) ──── (N) AdminAuditLog
```

| Table | Description |
|-------|-------------|
| `users` | 사용자 (email, password, name, role) |
| `refresh_tokens` | Refresh token (UUID, user FK, expires) |
| `ai_generations` | AI 생성 이력 (prompt, result, model, tokens) |
| `ai_jobs` | 비동기 작업 (status, retry_count, error) |
| `admin_audit_logs` | 관리자 감사 로그 (action, target, details) |

### Exception Hierarchy
```
BusinessException (abstract)
├── NotFoundException        → 404
├── DuplicateException       → 409
├── ForbiddenException       → 403
└── RateLimitException       → 429
AiServiceException           → 502
```

---

## Deployment

### Docker Compose
```bash
# App + PostgreSQL + Redis
docker-compose up -d

# Monitoring (Prometheus + Grafana)
docker-compose -f monitoring/docker-compose.monitoring.yml up -d
```

### Kubernetes (k3s)
```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml      # 사전에 값 변경 필요
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/app.yaml          # replica 2, rolling update
kubectl apply -f k8s/monitoring.yaml    # Prometheus + Grafana
```

### CI/CD (GitHub Actions)
| Workflow | Trigger | Pipeline |
|----------|---------|----------|
| `ci.yml` | PR → main | Build → Test → Report |
| `cd.yml` | Push → main | Build → Test → GHCR Push → k3s Deploy |

---

## Monitoring

### Grafana Dashboard (`:3001`)
- Request Rate / Response Time (p95)
- Error Rate (5xx) / JVM Heap / GC Pause
- HikariCP Connection Pool / AI Queue Size

### Health Check
| Component | Check |
|-----------|-------|
| `db` | PostgreSQL 연결 |
| `redis` | Redis 연결 |
| `diskSpace` | 500MB 이상 여유 |
| `redisQueue` | AI 큐 사이즈 < 100 |

### Logging
- **Dev**: 콘솔 (컬러 포맷)
- **Prod**: 콘솔 + 파일 (100MB x 30일) + 에러 분리 (50MB x 90일)
- `RequestLoggingFilter`: X-Request-Id 추적, slow request 감지 (3s+)

---

## Test

```bash
./gradlew test
```

| Test Class | Cases | Target |
|------------|-------|--------|
| AuthServiceTest | 7 | signup, login, refresh, error |
| JwtProviderTest | 4 | token generate/parse/validate/expire |
| AiJobServiceTest | 5 | job create/process/fail/not found |
| ExceptionTest | 5 | custom exception hierarchy |
| UserServiceTest | 9 | user CRUD, partial update |
| AiServiceTest | 11 | AI generate, history, ownership |
| AdminServiceTest | 13 | admin CRUD, audit log, stats |
| **Total** | **54** | |

---

## MVP Progress

| Phase | Status | Features |
|-------|--------|----------|
| MVP-1 | Done | JWT auth, User CRUD, Exception handling |
| MVP-2 | Done | Refresh Token, CORS, AI sync generation, Health check, Tests |
| MVP-3 | Done | Redis Queue, Async AI, Rate Limiting, Admin API, Audit Log |
| MVP-4 | Planned | Kafka, OAuth2, Notifications, Monitoring enhancement |

---

## Documentation

| Document | Description |
|----------|-------------|
| [API.md](docs/API.md) | API 상세 명세 (request/response 예시) |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 아키텍처, DB 스키마, 배포 구성 |
| [BACKEND_DESIGN.md](docs/BACKEND_DESIGN.md) | 패키지 설계, Entity/DTO, 인증/인가, 비동기 처리 |
| [SERVICE_PLAN.md](docs/SERVICE_PLAN.md) | 서비스 흐름, User Journey, 로드맵 |
| [SETUP.md](docs/SETUP.md) | 환경 설정 가이드, Docker Compose |
| [OPERATIONS.md](docs/OPERATIONS.md) | 운영 가이드 (로깅, 모니터링, 장애 대응, 성능) |
