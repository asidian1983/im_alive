# Backend Design Document

## 1. 패키지 구조

```
src/main/java/com/project/
├── ImAliveApplication.java
│
├── config/                          # 설정 및 인프라 구성
│   ├── SecurityConfig.java          # Spring Security 설정
│   ├── JwtAuthenticationFilter.java # JWT 인증 필터
│   ├── CorsConfig.java             # CORS 정책
│   ├── RedisConfig.java            # [MVP-3] Redis 연결 설정
│   ├── AsyncConfig.java            # [MVP-3] 비동기 스레드풀 설정
│   └── GlobalExceptionHandler.java  # 전역 예외 핸들러
│
├── controller/                      # Presentation Layer (HTTP 진입점)
│   ├── AuthController.java          # POST /api/auth/*
│   ├── UserController.java          # GET|PATCH|DELETE /api/users/me
│   ├── AiController.java           # POST /api/ai/*
│   └── AdminController.java        # [MVP-3] GET|PATCH|DELETE /api/admin/*
│
├── service/                         # Business Layer (핵심 로직)
│   ├── AuthService.java             # 인증: signup, login, refresh, logout
│   ├── UserService.java             # 사용자: 조회, 수정, 삭제
│   ├── AiService.java              # AI: 동기 생성, 비동기 생성
│   ├── AiJobService.java           # [MVP-3] 비동기 작업 관리
│   └── AdminService.java           # [MVP-3] 관리자 기능
│
├── domain/                          # Domain Layer (엔티티)
│   ├── User.java                    # 사용자
│   ├── RefreshToken.java           # 리프레시 토큰
│   ├── AiGeneration.java           # [MVP-3] AI 생성 이력
│   ├── AiJob.java                  # [MVP-3] 비동기 작업
│   ├── AiJobStatus.java            # [MVP-3] 작업 상태 enum
│   └── AdminAuditLog.java          # [MVP-3] 관리자 감사 로그
│
├── repository/                      # Persistence Layer (데이터 접근)
│   ├── UserRepository.java
│   ├── RefreshTokenRepository.java
│   ├── AiGenerationRepository.java # [MVP-3]
│   ├── AiJobRepository.java        # [MVP-3]
│   └── AdminAuditLogRepository.java# [MVP-3]
│
├── dto/                             # Data Transfer Objects
│   ├── request/                     # 요청 DTO
│   │   ├── LoginRequest.java
│   │   ├── SignupRequest.java
│   │   ├── TokenRefreshRequest.java
│   │   ├── UpdateUserRequest.java
│   │   ├── AiRequest.java
│   │   └── AdminUserUpdateRequest.java  # [MVP-3]
│   │
│   └── response/                    # 응답 DTO
│       ├── AuthResponse.java
│       ├── UserResponse.java
│       ├── AiResponse.java
│       ├── AiJobResponse.java       # [MVP-3]
│       ├── AiHistoryResponse.java   # [MVP-3]
│       ├── AdminStatsResponse.java  # [MVP-3]
│       ├── ErrorResponse.java
│       └── PageResponse.java        # [MVP-3] 페이징 공통 응답
│
├── infra/                           # Infrastructure Layer (외부 연동)
│   ├── JwtProvider.java             # JWT 토큰 생성/검증
│   ├── AiClient.java               # Anthropic API 클라이언트
│   └── RedisClient.java            # [MVP-3] Redis 연동
│
└── common/                          # [MVP-3] 공통 유틸리티
    ├── exception/                   # 커스텀 예외
    │   ├── BusinessException.java
    │   ├── UnauthorizedException.java
    │   ├── ForbiddenException.java
    │   └── NotFoundException.java
    └── annotation/                  # 커스텀 어노테이션
        └── CurrentUserId.java       # 인증된 사용자 ID 주입
```

### 패키지 설계 원칙

| 원칙 | 설명 |
|------|------|
| **단방향 의존** | Controller → Service → Repository. 역방향 금지 |
| **DTO 분리** | Entity를 Controller에 노출하지 않음. 반드시 DTO로 변환 |
| **Infra 격리** | 외부 API/Redis 등은 infra에서만 처리. Service는 인터페이스만 의존 |
| **Config 집중** | Bean 설정, 필터, 예외 핸들러는 config 패키지에 집중 |

---

## 2. 주요 Entity 설계

### User (현재 구현)
```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;                    // 로그인 ID

    @Column(nullable = false)
    private String password;                 // BCrypt 해싱

    @Column(nullable = false)
    private String name;                     // 표시명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;           // USER | ADMIN

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

### RefreshToken (현재 구현)
```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;                    // UUID

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;         // 발급 후 7일

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

### AiGeneration (MVP-3)
```java
@Entity
@Table(name = "ai_generations", indexes = {
    @Index(name = "idx_ai_gen_user_id", columnList = "user_id"),
    @Index(name = "idx_ai_gen_created", columnList = "created_at DESC")
})
public class AiGeneration {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;                       // 요청자

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;                   // 입력 프롬프트

    @Column(columnDefinition = "TEXT")
    private String result;                   // AI 응답 결과

    @Column(nullable = false, length = 50)
    private String model;                    // 사용된 모델명

    @Column(name = "tokens_used")
    private Integer tokensUsed;              // 사용 토큰 수

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

### AiJob (MVP-3 — 비동기 작업)
```java
@Entity
@Table(name = "ai_jobs", indexes = {
    @Index(name = "idx_ai_job_user_status", columnList = "user_id, status")
})
public class AiJob {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "generation_id")
    private AiGeneration generation;         // 완료 시 연결

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiJobStatus status = AiJobStatus.QUEUED;

    @Column(columnDefinition = "TEXT")
    private String prompt;                   // 요청 프롬프트 (큐잉 시 저장)

    @Column(name = "error_message")
    private String errorMessage;             // 실패 시 에러 메시지

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;              // 재시도 횟수

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### AiJobStatus (MVP-3)
```java
public enum AiJobStatus {
    QUEUED,       // 큐 대기중
    PROCESSING,   // 처리중
    COMPLETED,    // 완료
    FAILED,       // 실패
    CANCELLED     // 취소됨
}
```

### AdminAuditLog (MVP-3)
```java
@Entity
@Table(name = "admin_audit_logs", indexes = {
    @Index(name = "idx_audit_admin_id", columnList = "admin_id"),
    @Index(name = "idx_audit_created", columnList = "created_at DESC")
})
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;                      // 행동한 관리자

    @Column(nullable = false, length = 50)
    private String action;                   // CREATE, UPDATE, DELETE, SUSPEND

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;               // USER, AI_JOB 등

    @Column(name = "target_id")
    private Long targetId;                   // 대상 ID

    @Column(columnDefinition = "jsonb")
    private String details;                  // 변경 상세 (JSON)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

### Entity 관계 다이어그램
```
User (1) ──── (N) RefreshToken
  │
  ├── (1) ──── (N) AiGeneration
  │                     │
  │               (1) ──── (1) AiJob
  │
  └── (1) ──── (N) AdminAuditLog (admin만)
```

---

## 3. DTO 설계

### 요청 DTO

#### Auth
```java
// 회원가입
record SignupRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotBlank String name
) {}

// 로그인
record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}

// 토큰 갱신
record TokenRefreshRequest(
    @NotBlank String refreshToken
) {}
```

#### User
```java
// 프로필 수정
record UpdateUserRequest(
    String name,                              // nullable → 변경 안함
    @Size(min = 8) String password            // nullable → 변경 안함
) {}
```

#### AI
```java
// AI 동기 생성
record AiRequest(
    @NotBlank @Size(max = 10000) String prompt
) {}

// [MVP-3] AI 비동기 생성 (동일 형태, 별도 엔드포인트)
record AiAsyncRequest(
    @NotBlank @Size(max = 50000) String prompt,  // 비동기는 더 긴 프롬프트 허용
    @Max(4096) Integer maxTokens                  // 선택적 오버라이드
) {}
```

#### Admin (MVP-3)
```java
// 관리자 사용자 수정
record AdminUserUpdateRequest(
    String name,
    String role                               // "USER" | "ADMIN"
) {}
```

### 응답 DTO

```java
// 인증 응답
record AuthResponse(
    String accessToken,
    String refreshToken,
    String email,
    String name
) {}

// 사용자 프로필
record UserResponse(
    Long id,
    String email,
    String name,
    String role,
    LocalDateTime createdAt
) {
    static UserResponse from(User user) { ... }  // Entity → DTO 변환
}

// AI 생성 결과
record AiResponse(
    String content,
    String model,
    int tokensUsed
) {}

// [MVP-3] AI 비동기 작업 응답
record AiJobResponse(
    Long jobId,
    String status,                            // QUEUED | PROCESSING | COMPLETED | FAILED
    String result,                            // COMPLETED일 때만 포함
    String errorMessage,                      // FAILED일 때만 포함
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    static AiJobResponse from(AiJob job) { ... }
}

// [MVP-3] AI 생성 이력
record AiHistoryResponse(
    Long id,
    String prompt,
    String result,
    String model,
    int tokensUsed,
    LocalDateTime createdAt
) {
    static AiHistoryResponse from(AiGeneration gen) { ... }
}

// [MVP-3] 페이징 공통 응답
record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    static <T> PageResponse<T> from(Page<T> page) { ... }
}

// [MVP-3] 관리자 통계
record AdminStatsResponse(
    long totalUsers,
    long totalGenerations,
    long totalTokensUsed,
    long activeJobCount
) {}

// 에러 응답 (공통)
record ErrorResponse(
    int status,
    String message,
    Map<String, String> errors,               // 유효성 검증 에러 필드별 메시지
    LocalDateTime timestamp
) {}
```

### DTO 설계 원칙

| 원칙 | 설명 |
|------|------|
| **record 사용** | Java record로 불변성 보장, boilerplate 제거 |
| **요청/응답 분리** | 같은 도메인이라도 Request/Response 별도 정의 |
| **from() 패턴** | Entity → DTO 변환은 DTO의 static method로 |
| **Validation은 요청에만** | `@NotBlank`, `@Size` 등은 Request DTO에만 적용 |
| **null = 미변경** | PATCH 요청에서 null 필드는 기존 값 유지 |

---

## 4. 인증/인가 구조 (JWT 기반)

### 토큰 구조

```
Access Token (JWT)
├── Header:  { "alg": "HS256" }
├── Payload: {
│     "sub": "1",              // userId
│     "email": "user@test.com",
│     "role": "USER",
│     "iat": 1711500000,
│     "exp": 1711503600        // +1시간
│   }
└── Signature: HMAC-SHA256(secret)

Refresh Token
└── UUID v4 (DB 저장, 7일 만료)
```

### 인증 흐름

```
┌──────────────┐     ┌───────────────────────────┐     ┌──────────────┐
│   Client     │     │   JwtAuthenticationFilter  │     │  Controller  │
└──────┬───────┘     └─────────────┬─────────────┘     └──────┬───────┘
       │                           │                          │
       │  Authorization: Bearer xxx│                          │
       │──────────────────────────►│                          │
       │                           │                          │
       │                    ┌──────▼──────┐                   │
       │                    │ 1. Header에서│                   │
       │                    │ "Bearer " 추출│                  │
       │                    └──────┬──────┘                   │
       │                           │                          │
       │                    ┌──────▼──────┐                   │
       │                    │ 2. JwtProvider│                  │
       │                    │ .validateToken│                  │
       │                    └──────┬──────┘                   │
       │                           │                          │
       │                    ┌──────▼──────┐                   │
       │                    │ 3. Claims에서│                   │
       │                    │ userId, role│                    │
       │                    │ 추출         │                   │
       │                    └──────┬──────┘                   │
       │                           │                          │
       │                    ┌──────▼──────┐                   │
       │                    │ 4. Security  │                  │
       │                    │ Context 설정 │                   │
       │                    │ (userId,     │                   │
       │                    │  ROLE_USER)  │                   │
       │                    └──────┬──────┘                   │
       │                           │                          │
       │                           │  doFilter                │
       │                           │─────────────────────────►│
       │                           │                          │
```

### 접근 제어 매트릭스

| Endpoint Pattern | 접근 권한 | 설명 |
|-----------------|----------|------|
| `POST /api/auth/signup` | `permitAll` | 회원가입 |
| `POST /api/auth/login` | `permitAll` | 로그인 |
| `POST /api/auth/refresh` | `permitAll` | 토큰 갱신 |
| `GET /actuator/health` | `permitAll` | 헬스체크 |
| `GET /actuator/info` | `permitAll` | 서버 정보 |
| `/api/users/**` | `authenticated` | 로그인 사용자 |
| `/api/ai/**` | `authenticated` | 로그인 사용자 |
| `/api/admin/**` | `hasRole('ADMIN')` | [MVP-3] 관리자만 |
| 나머지 | `authenticated` | 기본 인증 필요 |

### SecurityConfig 설계 (MVP-3 포함)

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")   // MVP-3
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

### Refresh Token 로테이션 전략

```
1. 로그인 시 → 기존 refresh token 전부 삭제 → 새 쌍 발급
2. 갱신 시 → 사용된 token 삭제 → 새 쌍 발급 (rotation)
3. 로그아웃 시 → 해당 사용자 refresh token 전부 삭제
4. 만료된 token 사용 시 → token 삭제 + 에러 반환
```

이 전략은 토큰 탈취 시 피해를 최소화합니다. 탈취된 refresh token이 사용되면 원래 사용자의 다음 갱신 시도가 실패하여 재로그인을 유도합니다.

---

## 5. 예외 처리 구조

### 예외 계층

```
RuntimeException
└── BusinessException (추상)          ← 비즈니스 로직 에러 기반 클래스
    ├── NotFoundException             ← 404: 리소스 없음
    ├── DuplicateException            ← 409: 중복
    ├── UnauthorizedException         ← 401: 인증 실패
    ├── ForbiddenException            ← 403: 권한 없음
    └── RateLimitException            ← 429: [MVP-3] 요청 제한 초과

외부 연동 에러
├── AiServiceException               ← AI API 호출 실패
└── RedisException                   ← [MVP-3] Redis 연결 실패
```

### 커스텀 예외 클래스

```java
// 비즈니스 예외 기반 클래스
public abstract class BusinessException extends RuntimeException {
    private final int status;

    protected BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}

// 404 Not Found
public class NotFoundException extends BusinessException {
    public NotFoundException(String resource) {
        super(404, resource + " not found");
    }
}

// 409 Conflict (중복)
public class DuplicateException extends BusinessException {
    public DuplicateException(String message) {
        super(409, message);
    }
}

// 401 Unauthorized
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super(401, message);
    }
}

// 403 Forbidden
public class ForbiddenException extends BusinessException {
    public ForbiddenException() {
        super(403, "Access denied");
    }
}

// 429 Rate Limit (MVP-3)
public class RateLimitException extends BusinessException {
    public RateLimitException() {
        super(429, "Too many requests");
    }
}
```

### GlobalExceptionHandler 설계

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 통합 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity
            .status(ex.getStatus())
            .body(new ErrorResponse(ex.getStatus(), ex.getMessage()));
    }

    // Bean Validation 에러
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(400, "Validation failed", errors));
    }

    // AI 외부 서비스 에러
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorResponse> handleAiService(AiServiceException ex) {
        return ResponseEntity
            .status(502)
            .body(new ErrorResponse(502, "AI service unavailable"));
    }

    // 예상치 못한 에러 (로깅 포함)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .internalServerError()
            .body(new ErrorResponse(500, "Internal server error"));
    }
}
```

### 에러 응답 표준 포맷

```json
{
  "status": 404,
  "message": "User not found",
  "errors": null,
  "timestamp": "2026-03-27T10:00:00"
}
```

### 에러 처리 원칙

| 원칙 | 설명 |
|------|------|
| **Controller에서 try-catch 금지** | 모든 예외는 GlobalExceptionHandler에서 처리 |
| **Service에서 예외 발생** | 비즈니스 규칙 위반 시 BusinessException 하위 클래스 throw |
| **외부 에러 래핑** | AI API, Redis 등 외부 에러는 커스텀 예외로 래핑 후 throw |
| **민감 정보 노출 금지** | 500 에러 시 스택트레이스 노출 안 함. 서버 로그에만 기록 |
| **일관된 응답 포맷** | 모든 에러는 ErrorResponse 형태로 통일 |

---

## 6. 비동기 처리 구조 (Redis Queue)

### 아키텍처 개요

```
┌──────────┐    ┌─────────────┐    ┌───────────┐    ┌──────────────┐
│  Client  │───►│ AiController│───►│ AiService │───►│ Redis Queue  │
│          │    │             │    │           │    │ (ai:jobs)    │
│          │    │ 202 Accepted│◄───│ Job 생성  │    └──────┬───────┘
│          │    └─────────────┘    └───────────┘           │
│          │                                                │ BRPOP
│          │    ┌─────────────┐    ┌───────────┐    ┌──────▼───────┐
│          │───►│ GET /jobs/  │───►│AiJobService│◄──│  AiWorker    │
│  (폴링)  │    │   {jobId}   │    │           │    │  (@Scheduled)│
│          │◄───│ 200 status  │    │ 상태 조회  │    │              │
│          │    └─────────────┘    └───────────┘    │ AI API 호출  │
└──────────┘                                        │ 결과 저장    │
                                                    └──────────────┘
```

### Redis 구조

```
Redis Keys:
├── ai:jobs                          # LIST — 작업 큐 (LPUSH / BRPOP)
├── ai:job:{jobId}:status            # STRING — 작업 상태 캐시 (TTL 1h)
├── ai:ratelimit:{userId}            # STRING — [MVP-3] 분당 요청 카운터 (TTL 60s)
└── ai:cache:{promptHash}            # STRING — [MVP-4] 동일 프롬프트 캐시
```

### 비동기 처리 흐름

```
1. 요청 수신
   POST /api/ai/generate-async { prompt }
   → AiJob 생성 (status: QUEUED) → DB 저장
   → Redis LIST에 jobId LPUSH
   → 202 Accepted { jobId, status: "QUEUED" } 반환

2. Worker 처리
   AiWorker (@Scheduled, fixedDelay=1000)
   → Redis BRPOP (ai:jobs, timeout=5s)
   → jobId 수신 → DB에서 AiJob 조회
   → status → PROCESSING (DB + Redis 캐시 업데이트)
   → AiClient.generate(prompt) 호출
   → 성공: AiGeneration 저장 → status → COMPLETED
   → 실패: retryCount < 3 → 재큐잉 / retryCount >= 3 → FAILED

3. 상태 폴링
   GET /api/ai/jobs/{jobId}
   → Redis 캐시 먼저 확인 → 없으면 DB 조회
   → { jobId, status, result(완료시), errorMessage(실패시) }
```

### 주요 컴포넌트 설계

#### RedisConfig
```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

#### AiWorker (비동기 작업 소비자)
```java
@Component
public class AiWorker {

    private final RedisTemplate<String, String> redis;
    private final AiJobService aiJobService;
    private final AiClient aiClient;

    @Scheduled(fixedDelay = 1000)             // 1초마다 폴링
    public void processQueue() {
        String jobId = redis.opsForList()
            .rightPop("ai:jobs", Duration.ofSeconds(5));

        if (jobId == null) return;

        aiJobService.process(Long.parseLong(jobId), aiClient);
    }
}
```

#### AiJobService
```java
@Service
public class AiJobService {

    private static final int MAX_RETRIES = 3;

    @Transactional
    public AiJobResponse createJob(Long userId, String prompt) {
        // 1. AiJob 생성 (QUEUED)
        // 2. Redis 큐에 LPUSH
        // 3. 202 응답용 DTO 반환
    }

    @Transactional
    public void process(Long jobId, AiClient aiClient) {
        // 1. status → PROCESSING
        // 2. aiClient.generate(prompt)
        // 3. 성공 → AiGeneration 저장, status → COMPLETED
        // 4. 실패 → retryCount 확인 → 재큐잉 or FAILED
    }

    @Transactional(readOnly = true)
    public AiJobResponse getJobStatus(Long jobId, Long userId) {
        // 1. Redis 캐시 확인
        // 2. 없으면 DB 조회
        // 3. 본인 작업만 조회 가능 (userId 검증)
    }
}
```

### Rate Limiting (MVP-3)

```java
@Component
public class RateLimiter {

    private final RedisTemplate<String, String> redis;

    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    public void checkRateLimit(Long userId) {
        String key = "ai:ratelimit:" + userId;
        Long count = redis.opsForValue().increment(key);

        if (count == 1) {
            redis.expire(key, Duration.ofSeconds(60));
        }

        if (count > MAX_REQUESTS_PER_MINUTE) {
            throw new RateLimitException();
        }
    }
}
```

### 재시도 전략

```
실패 시:
├── retryCount < 3 → 1초 대기 후 재큐잉 (exponential backoff 미적용, 단순 재시도)
├── retryCount >= 3 → status: FAILED, errorMessage 저장
└── AI API 타임아웃 → 30초 (WebClient timeout 설정)
```

### 비동기 처리 설계 원칙

| 원칙 | 설명 |
|------|------|
| **멱등성** | 같은 jobId로 중복 처리되어도 결과가 동일 |
| **관찰 가능성** | 모든 상태 변경은 DB + Redis 캐시에 반영 |
| **Graceful 실패** | 재시도 3회 후 FAILED 마킹, 에러 메시지 저장 |
| **본인 작업만 조회** | jobId + userId로 소유권 검증 |
| **TTL 관리** | Redis 캐시는 1시간 TTL, Rate Limit은 60초 TTL |
