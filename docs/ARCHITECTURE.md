# Architecture Document

## Tech Stack
| 항목 | 기술 |
|------|------|
| Language | Java 17+ |
| Framework | Spring Boot 3.2 |
| Build | Gradle |
| Database | PostgreSQL |
| ORM | Spring Data JPA (Hibernate) |
| Cache / Queue | Redis |
| Auth | JWT (jjwt 0.12.5) + Spring Security |
| AI | Anthropic Claude API (WebClient) |
| Monitoring | Spring Actuator |

---

## Project Structure
```
src/main/java/com/project/
├── ImAliveApplication.java          # Spring Boot entry point
├── common/
│   └── exception/
│       ├── BusinessException.java   # 비즈니스 예외 기반 클래스
│       ├── NotFoundException.java   # 404
│       ├── DuplicateException.java  # 409
│       ├── ForbiddenException.java  # 403
│       ├── RateLimitException.java  # 429
│       └── AiServiceException.java  # AI 외부 서비스 에러
├── config/
│   ├── SecurityConfig.java          # Spring Security (stateless, JWT, RBAC)
│   ├── JwtAuthenticationFilter.java # Bearer 토큰 추출 및 인증
│   ├── CorsConfig.java             # CORS 정책 설정
│   ├── RedisConfig.java            # Redis 연결 및 Template 설정
│   ├── AsyncConfig.java            # @Scheduled 활성화
│   └── GlobalExceptionHandler.java  # 전역 예외 핸들러 (계층별 처리)
├── controller/
│   ├── AuthController.java          # 인증 (signup, login, refresh, logout)
│   ├── UserController.java          # 사용자 (me, update, delete)
│   ├── AiController.java           # AI (generate, async, history, jobs)
│   └── AdminController.java        # 관리자 (users CRUD, stats)
├── service/
│   ├── AuthService.java             # 인증 비즈니스 로직
│   ├── UserService.java             # 사용자 비즈니스 로직
│   ├── AiService.java              # AI 동기 생성 + 이력 관리
│   ├── AiJobService.java           # AI 비동기 작업 관리 (큐, 처리, 재시도)
│   └── AdminService.java           # 관리자 기능 + 감사 로그
├── domain/
│   ├── User.java                    # 사용자 엔티티
│   ├── RefreshToken.java           # 리프레시 토큰 엔티티
│   ├── AiGeneration.java           # AI 생성 이력 엔티티
│   ├── AiJob.java                  # 비동기 작업 엔티티
│   ├── AiJobStatus.java            # 작업 상태 enum
│   └── AdminAuditLog.java          # 관리자 감사 로그 엔티티
├── repository/
│   ├── UserRepository.java
│   ├── RefreshTokenRepository.java
│   ├── AiGenerationRepository.java
│   ├── AiJobRepository.java
│   └── AdminAuditLogRepository.java
├── dto/
│   ├── LoginRequest.java
│   ├── SignupRequest.java
│   ├── AuthResponse.java           # accessToken + refreshToken
│   ├── TokenRefreshRequest.java
│   ├── UpdateUserRequest.java
│   ├── UserResponse.java
│   ├── AiRequest.java              # 동기 생성 요청
│   ├── AiAsyncRequest.java         # 비동기 생성 요청
│   ├── AiResponse.java             # AI 생성 응답
│   ├── AiJobResponse.java          # 비동기 작업 상태
│   ├── AiHistoryResponse.java      # 생성 이력
│   ├── PageResponse.java           # 페이징 공통 응답
│   ├── AdminUserUpdateRequest.java # 관리자 사용자 수정
│   ├── AdminStatsResponse.java     # 시스템 통계
│   └── ErrorResponse.java          # 에러 응답
└── infra/
    ├── JwtProvider.java             # JWT 생성/검증/파싱
    ├── AiClient.java               # Anthropic API 클라이언트 (30s timeout)
    ├── AiWorker.java               # Redis Queue Consumer (@Scheduled)
    └── RateLimiter.java            # Redis 기반 분당 요청 제한
```

---

## Authentication Flow

```
1. Signup/Login
   Client → POST /api/auth/signup or /login
   Server → BCrypt 검증 → JWT accessToken(1h) + refreshToken(7d) 발급

2. Authenticated Request
   Client → Authorization: Bearer <accessToken>
   JwtAuthenticationFilter → 토큰 검증 → SecurityContext에 userId + ROLE 설정

3. Token Refresh
   Client → POST /api/auth/refresh { refreshToken }
   Server → refresh token 검증 → 기존 삭제 → 새 accessToken + refreshToken 발급

4. Logout
   Client → POST /api/auth/logout (with Bearer token)
   Server → 해당 사용자 refresh token 전체 삭제
```

### Access Control
| Pattern | 권한 |
|---------|------|
| `/api/auth/**` | 공개 |
| `/actuator/health,info` | 공개 |
| `/api/admin/**` | ROLE_ADMIN |
| 나머지 | authenticated |

---

## Async Processing (Redis Queue)

```
Client → POST /api/ai/generate-async
       → AiJob 생성 (QUEUED) → Redis LPUSH ai:jobs
       ← 202 Accepted { jobId }

AiWorker (@Scheduled 1초 간격)
       → Redis BRPOP ai:jobs (5초 대기)
       → AiJob PROCESSING → AI API 호출
       → 성공: AiGeneration 저장 → COMPLETED
       → 실패: retryCount < 3 → 재큐잉 / ≥ 3 → FAILED

Client → GET /api/ai/jobs/{jobId} (폴링)
       ← { status, result }
```

### Redis Key 구조
| Key | Type | TTL | 용도 |
|-----|------|-----|------|
| `ai:jobs` | LIST | - | 작업 큐 |
| `ai:job:{id}:status` | STRING | 1h | 작업 상태 캐시 |
| `ai:ratelimit:{userId}` | STRING | 60s | Rate Limit 카운터 |

---

## Database Schema

### users
| Column | Type | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK, auto-increment |
| email | VARCHAR | NOT NULL, UNIQUE |
| password | VARCHAR | NOT NULL (BCrypt) |
| name | VARCHAR | NOT NULL |
| role | VARCHAR | NOT NULL, default 'USER' |
| created_at | TIMESTAMP | NOT NULL |

### refresh_tokens
| Column | Type | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK, auto-increment |
| token | VARCHAR | NOT NULL, UNIQUE |
| user_id | BIGINT | FK → users.id |
| expires_at | TIMESTAMP | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

### ai_generations
| Column | Type | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK, auto-increment |
| user_id | BIGINT | FK → users.id, INDEX |
| prompt | TEXT | NOT NULL |
| result | TEXT | |
| model | VARCHAR(50) | NOT NULL |
| tokens_used | INT | |
| created_at | TIMESTAMP | NOT NULL, INDEX DESC |

### ai_jobs
| Column | Type | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK, auto-increment |
| user_id | BIGINT | FK → users.id |
| generation_id | BIGINT | FK → ai_generations.id |
| status | VARCHAR(20) | NOT NULL (QUEUED/PROCESSING/COMPLETED/FAILED) |
| prompt | TEXT | NOT NULL |
| error_message | VARCHAR | |
| retry_count | INT | NOT NULL, default 0 |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | |

### admin_audit_logs
| Column | Type | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK, auto-increment |
| admin_id | BIGINT | FK → users.id, INDEX |
| action | VARCHAR(50) | NOT NULL |
| target_type | VARCHAR(30) | NOT NULL |
| target_id | BIGINT | |
| details | TEXT | |
| created_at | TIMESTAMP | NOT NULL, INDEX DESC |

---

## Exception Hierarchy

```
RuntimeException
└── BusinessException (abstract, status code)
    ├── NotFoundException        → 404
    ├── DuplicateException       → 409
    ├── ForbiddenException       → 403
    └── RateLimitException       → 429

AiServiceException               → 502 (외부 AI API 에러)
IllegalArgumentException          → 400 (기존 호환)
MethodArgumentNotValidException   → 400 (Bean Validation)
Exception                         → 500 (예상치 못한 에러, 로깅)
```

---

## Configuration

### Environment Variables
| 변수 | 설명 | 기본값 |
|------|------|--------|
| DB_USERNAME | PostgreSQL 사용자명 | postgres |
| DB_PASSWORD | PostgreSQL 비밀번호 | postgres |
| JWT_SECRET | JWT 서명 키 (256bit+) | (개발용 기본값) |
| CORS_ORIGINS | 허용 Origin (쉼표 구분) | http://localhost:3000 |
| REDIS_HOST | Redis 호스트 | localhost |
| REDIS_PORT | Redis 포트 | 6379 |
| AI_API_KEY | Anthropic API 키 | (없음) |
| AI_API_URL | AI API URL | https://api.anthropic.com/v1/messages |
| AI_MODEL | AI 모델명 | claude-sonnet-4-20250514 |
| AI_MAX_TOKENS | AI 최대 토큰 수 | 1024 |
| AI_RATE_LIMIT | 분당 AI 요청 제한 | 10 |
| AI_MAX_RETRIES | 비동기 작업 최대 재시도 | 3 |

---

## MVP Progress

| Phase | Status | Features |
|-------|--------|----------|
| MVP-1 | ✅ Done | JWT 로그인/회원가입, 사용자 CRUD, 에러 핸들링 |
| MVP-2 | ✅ Done | Refresh Token, CORS, AI 동기 생성, 헬스체크, 테스트 |
| MVP-3 | ✅ Done | Redis 큐, 비동기 AI, Rate Limiting, 관리자 API, 감사 로그 |
| MVP-4 | Planned | Kafka, OAuth2 소셜 로그인, 알림, 모니터링 |

## Test Coverage (54 cases)
| Test Class | Cases | 대상 |
|-----------|-------|------|
| AuthServiceTest | 7 | 회원가입, 로그인, refresh, 에러 케이스 |
| JwtProviderTest | 4 | 토큰 생성/파싱/검증/만료 |
| AiJobServiceTest | 5 | 작업 생성, 처리, 실패, 미존재 |
| ExceptionTest | 5 | 커스텀 예외 계층 검증 |
| UserServiceTest | 9 | 사용자 CRUD, 부분 업데이트, 에러 |
| AiServiceTest | 11 | AI 생성, 이력 조회, 소유권 검증 |
| AdminServiceTest | 13 | 관리자 CRUD, 감사 로그, 통계 |

---

## Deployment Architecture

### Infrastructure
```
                    ┌─────────────┐
                    │   Ingress   │  (Traefik, TLS)
                    │ k3s default │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │    im-alive namespace   │
              │                         │
              │  ┌─────────────────┐    │
              │  │  App (replica 2)│    │
              │  │  Spring Boot    │    │
              │  │  :8080          │    │
              │  └───┬────────┬────┘    │
              │      │        │         │
              │  ┌───┴───┐ ┌──┴──────┐  │
              │  │ Redis │ │PostgreSQL│  │
              │  │ :6379 │ │  :5432  │  │
              │  └───────┘ └─────────┘  │
              └─────────────────────────┘
```

### Docker
| 파일 | 설명 |
|------|------|
| `Dockerfile` | Multi-stage 빌드 (JDK 17 빌드 → JRE 17 런타임), non-root user, healthcheck |
| `.dockerignore` | 빌드 불필요 파일 제외 |
| `docker-compose.yml` | App + PostgreSQL 16 + Redis 7, healthcheck 의존성, 볼륨 영속화 |

### Kubernetes (k8s/)
| 파일 | 설명 |
|------|------|
| `namespace.yaml` | `im-alive` 네임스페이스 |
| `secrets.yaml` | DB/JWT/AI 시크릿 (gitignore 처리) |
| `configmap.yaml` | 비민감 환경변수 |
| `postgres.yaml` | Deployment + PVC(5Gi) + Service, liveness/readiness probe |
| `redis.yaml` | Deployment + Service, AOF 영속화 |
| `app.yaml` | Deployment(replica 2) + Service + Ingress(Traefik TLS), rolling update, startup/liveness/readiness probe |

### CI/CD (GitHub Actions)
| Workflow | Trigger | 단계 |
|----------|---------|------|
| `ci.yml` | PR → main | JDK 17 설정 → Gradle 캐시 → 빌드/테스트 → 리포트 업로드 |
| `cd.yml` | Push → main | 빌드/테스트 → GHCR 이미지 푸시 → k3s 배포 → rollout 확인 |

### Production Profile
- `application-prod.yml`: `ddl-auto: validate`, 로그 레벨 조정, Prometheus 엔드포인트 노출

### 배포 전 필수 설정
1. GitHub Secrets: `KUBECONFIG` (base64 인코딩)
2. `k8s/secrets.yaml` 실제 운영 값으로 변경 후 수동 apply
3. `k8s/configmap.yaml`, `k8s/app.yaml` 도메인 변경
4. 운영 환경변수: `SPRING_PROFILES_ACTIVE=prod`
