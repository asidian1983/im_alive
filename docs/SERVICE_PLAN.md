# im_alive 서비스 기획서

## 서비스 개요

**im_alive**는 AI 기반 콘텐츠 생성 플랫폼의 백엔드 서비스입니다.
사용자 인증, AI 텍스트 생성, 비동기 작업 처리, 관리자 기능을 제공하며
Next.js 기반 아키텍처에서 Spring Boot로 마이그레이션된 프로젝트입니다.

---

## 1. 전체 서비스 흐름 (User Flow)

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Web/App)                         │
└──────────┬──────────────────────────────────┬───────────────────┘
           │                                  │
     [비인증 영역]                       [인증 영역]
           │                                  │
    ┌──────▼──────┐                   ┌───────▼────────┐
    │  회원가입    │                   │  프로필 관리    │
    │  POST /signup│                   │  GET/PATCH/DEL │
    └──────┬──────┘                   │  /users/me     │
           │                          └───────┬────────┘
    ┌──────▼──────┐                           │
    │  로그인      │                   ┌───────▼────────┐
    │  POST /login │                   │  AI 생성        │
    └──────┬──────┘                   │  POST /generate │
           │                          └───────┬────────┘
    ┌──────▼──────┐                           │
    │  토큰 발급   │                   ┌───────▼────────┐
    │  access +   │                   │  생성 이력 조회  │
    │  refresh    │                   │  GET /history   │
    └──────┬──────┘                   └───────┬────────┘
           │                                  │
    ┌──────▼──────┐                   ┌───────▼────────┐
    │  토큰 갱신   │                   │  비동기 작업     │
    │  POST       │                   │  상태 폴링      │
    │  /refresh   │                   │  GET /jobs/:id  │
    └─────────────┘                   └────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      ADMIN FLOW                                 │
│  사용자 관리 / 시스템 모니터링 / AI 설정 / 사용량 대시보드          │
└─────────────────────────────────────────────────────────────────┘
```

### 핵심 플로우 요약

| 단계 | 행동 | API |
|------|------|-----|
| 1 | 회원가입 | `POST /api/auth/signup` |
| 2 | 로그인 → 토큰 수신 | `POST /api/auth/login` |
| 3 | AI 콘텐츠 생성 요청 | `POST /api/ai/generate` |
| 4 | 생성 이력 확인 | `GET /api/ai/history` (MVP-3) |
| 5 | 토큰 만료 시 갱신 | `POST /api/auth/refresh` |
| 6 | 로그아웃 | `POST /api/auth/logout` |

---

## 2. 주요 기능별 시나리오 (User Journey)

### Journey 1: 신규 사용자 온보딩
```
사용자 → 회원가입 페이지 진입
      → 이메일/비밀번호/이름 입력
      → POST /api/auth/signup
      → accessToken + refreshToken 수신
      → 자동 로그인 → 메인 화면 진입
```

**성공 조건:** 토큰 발급 완료, 프로필 조회 가능
**실패 시나리오:**
- 이미 등록된 이메일 → `400 Email already in use`
- 비밀번호 8자 미만 → `400 Validation failed`

### Journey 2: AI 콘텐츠 생성
```
사용자 → 프롬프트 입력
      → POST /api/ai/generate { prompt }
      ← 동기 응답: AI 생성 결과 + 사용 토큰 수
      → 결과 확인 / 복사 / 저장
```

**성공 조건:** AI 응답 수신, tokensUsed 표시
**실패 시나리오:**
- 프롬프트 비어있음 → `400 Validation failed`
- AI API 키 미설정 → `500 Internal Server Error`
- 토큰 만료 → `401` → 클라이언트가 refresh 후 재시도

### Journey 3: 비동기 AI 생성 (MVP-3)
```
사용자 → 대용량 프롬프트 입력
      → POST /api/ai/generate-async { prompt }
      ← 202 Accepted { jobId, status: "QUEUED" }
      → 폴링: GET /api/ai/jobs/{jobId}
      ← { status: "PROCESSING" }
      → 폴링: GET /api/ai/jobs/{jobId}
      ← { status: "COMPLETED", result: "..." }
```

### Journey 4: 토큰 라이프사이클
```
로그인 → accessToken(1h) + refreshToken(7d) 수신
      → API 요청 시 Bearer 토큰 첨부
      → accessToken 만료 (1시간 후)
      → 401 응답 수신
      → POST /api/auth/refresh { refreshToken }
      → 새 accessToken + refreshToken 수신
      → 7일 후 refreshToken 만료 → 재로그인 필요
```

### Journey 5: 관리자 운영 (MVP-3)
```
관리자 → 관리자 로그인 (ROLE_ADMIN)
       → 사용자 목록 조회: GET /api/admin/users
       → 사용자 상세/비활성화: PATCH /api/admin/users/{id}
       → 시스템 상태 확인: GET /actuator/health
       → AI 사용량 확인: GET /api/admin/stats
```

---

## 3. API 목록 설계

### Auth API (`/api/auth`)
| Method | Endpoint | Auth | 설명 | Status |
|--------|----------|------|------|--------|
| POST | `/signup` | No | 회원가입 | ✅ Done |
| POST | `/login` | No | 로그인 | ✅ Done |
| POST | `/refresh` | No | 토큰 갱신 | ✅ Done |
| POST | `/logout` | Yes | 로그아웃 | ✅ Done |
| POST | `/password-reset` | No | 비밀번호 재설정 요청 | MVP-4 |
| POST | `/password-reset/confirm` | No | 비밀번호 재설정 확인 | MVP-4 |
| POST | `/oauth2/{provider}` | No | 소셜 로그인 | MVP-4 |

### User API (`/api/users`)
| Method | Endpoint | Auth | 설명 | Status |
|--------|----------|------|------|--------|
| GET | `/me` | Yes | 내 프로필 조회 | ✅ Done |
| PATCH | `/me` | Yes | 프로필 수정 | ✅ Done |
| DELETE | `/me` | Yes | 계정 삭제 | ✅ Done |
| PATCH | `/me/profile-image` | Yes | 프로필 이미지 변경 | MVP-4 |

### AI API (`/api/ai`)
| Method | Endpoint | Auth | 설명 | Status |
|--------|----------|------|------|--------|
| POST | `/generate` | Yes | AI 텍스트 생성 (동기) + 이력 저장 | ✅ Done |
| POST | `/generate-async` | Yes | AI 생성 (비동기, 202 Accepted) | ✅ Done |
| GET | `/jobs/{jobId}` | Yes | 비동기 작업 상태 폴링 | ✅ Done |
| GET | `/history` | Yes | 내 생성 이력 조회 (페이징) | ✅ Done |
| GET | `/history/{id}` | Yes | 생성 이력 상세 | ✅ Done |
| DELETE | `/history/{id}` | Yes | 생성 이력 삭제 | ✅ Done |

### Admin API (`/api/admin`)
| Method | Endpoint | Auth | 설명 | Status |
|--------|----------|------|------|--------|
| GET | `/users` | ADMIN | 사용자 목록 (페이징) | ✅ Done |
| GET | `/users/{id}` | ADMIN | 사용자 상세 | ✅ Done |
| PATCH | `/users/{id}` | ADMIN | 사용자 역할/이름 변경 | ✅ Done |
| DELETE | `/users/{id}` | ADMIN | 사용자 삭제 | ✅ Done |
| GET | `/stats` | ADMIN | 시스템 통계 | ✅ Done |

### System API
| Method | Endpoint | Auth | 설명 | Status |
|--------|----------|------|------|--------|
| GET | `/actuator/health` | No | 서버 상태 | ✅ Done |
| GET | `/actuator/info` | No | 서버 정보 | ✅ Done |

---

## 4. DB 모델 초안 (ERD)

```
┌──────────────────────┐       ┌──────────────────────────┐
│       users          │       │     refresh_tokens       │
├──────────────────────┤       ├──────────────────────────┤
│ id         BIGSERIAL │◄──┐   │ id          BIGSERIAL    │
│ email      VARCHAR   │   │   │ token       VARCHAR (UQ) │
│ password   VARCHAR   │   └───│ user_id     BIGINT (FK)  │
│ name       VARCHAR   │       │ expires_at  TIMESTAMP    │
│ role       VARCHAR   │       │ created_at  TIMESTAMP    │
│ created_at TIMESTAMP │       └──────────────────────────┘
└──────────┬───────────┘
           │
           │ 1:N
           ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│    ai_generations        │    │      ai_jobs             │
│    (MVP-3)               │    │      (MVP-3)             │
├──────────────────────────┤    ├──────────────────────────┤
│ id          BIGSERIAL    │    │ id          BIGSERIAL    │
│ user_id     BIGINT (FK)  │    │ user_id     BIGINT (FK)  │
│ prompt      TEXT         │    │ generation_id BIGINT (FK) │
│ result      TEXT         │    │ status      VARCHAR      │
│ model       VARCHAR      │    │  (QUEUED/PROCESSING/     │
│ tokens_used INT          │    │   COMPLETED/FAILED)      │
│ created_at  TIMESTAMP    │    │ created_at  TIMESTAMP    │
└──────────────────────────┘    │ updated_at  TIMESTAMP    │
                                └──────────────────────────┘

┌──────────────────────────┐
│    admin_audit_logs      │
│    (MVP-3)               │
├──────────────────────────┤
│ id          BIGSERIAL    │
│ admin_id    BIGINT (FK)  │
│ action      VARCHAR      │
│ target_type VARCHAR      │
│ target_id   BIGINT       │
│ details     JSONB        │
│ created_at  TIMESTAMP    │
└──────────────────────────┘
```

### 테이블 관계 요약

| 관계 | 설명 |
|------|------|
| users → refresh_tokens | 1:N, 사용자당 여러 refresh token 가능 |
| users → ai_generations | 1:N, 사용자의 AI 생성 이력 |
| ai_generations → ai_jobs | 1:1, 비동기 작업 추적 |
| users → admin_audit_logs | 1:N, 관리자 행동 감사 로그 |

### 현재 구현된 테이블
- ✅ `users` — 사용자 정보 + 역할
- ✅ `refresh_tokens` — JWT refresh token 저장

### MVP-3에서 추가될 테이블
- `ai_generations` — AI 생성 이력 (prompt, result, 토큰 사용량)
- `ai_jobs` — 비동기 작업 상태 추적
- `admin_audit_logs` — 관리자 행동 감사

---

## 5. 시스템 아키텍처

```
                    ┌─────────────┐
                    │   Client    │
                    │ (Web / App) │
                    └──────┬──────┘
                           │ HTTPS
                    ┌──────▼──────┐
                    │   Nginx     │
                    │ (Reverse    │
                    │  Proxy)     │
                    └──────┬──────┘
                           │
              ┌────────────▼────────────┐
              │    Spring Boot App      │
              │    (port 8080)          │
              │                         │
              │  ┌───────────────────┐  │
              │  │  Security Filter  │  │
              │  │  (JWT Auth)       │  │
              │  └────────┬──────────┘  │
              │           │             │
              │  ┌────────▼──────────┐  │
              │  │   Controllers     │  │
              │  │  Auth│User│AI│Admin│  │
              │  └────────┬──────────┘  │
              │           │             │
              │  ┌────────▼──────────┐  │
              │  │    Services       │  │
              │  │  (Business Logic) │  │
              │  └───┬────┬────┬─────┘  │
              │      │    │    │        │
              └──────┼────┼────┼────────┘
                     │    │    │
          ┌──────────┘    │    └──────────┐
          │               │              │
   ┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼───────┐
   │ PostgreSQL  │ │   Redis     │ │ Anthropic   │
   │             │ │  (MVP-3)    │ │ Claude API  │
   │ - users     │ │             │ │             │
   │ - tokens    │ │ - Cache     │ │ - Generate  │
   │ - ai_gens   │ │ - Job Queue │ │             │
   │ - jobs      │ │ - Rate Limit│ │             │
   └─────────────┘ └──────┬──────┘ └─────────────┘
                          │
                   ┌──────▼──────┐
                   │   Worker    │
                   │  ✅ 구현    │
                   │             │
                   │ 비동기 AI   │
                   │ 작업 처리   │
                   └─────────────┘
```

### 레이어별 책임

| 레이어 | 패키지 | 책임 |
|--------|--------|------|
| **Presentation** | `controller/` | HTTP 요청 수신, 유효성 검증, 응답 반환 |
| **Business** | `service/` | 비즈니스 로직, 트랜잭션 관리 |
| **Persistence** | `repository/` | 데이터 접근, JPA 쿼리 |
| **Domain** | `domain/` | 엔티티 정의, 도메인 규칙 |
| **Infrastructure** | `infra/` | 외부 서비스 연동 (AI API, Redis) |
| **Config** | `config/` | Security, CORS, Exception Handler |
| **DTO** | `dto/` | 요청/응답 데이터 전송 객체 |

### 요청 처리 흐름

```
HTTP Request
  → CorsFilter (CORS 검증)
  → JwtAuthenticationFilter (토큰 검증 → SecurityContext 설정)
  → Controller (@Valid 검증 → Service 호출)
  → Service (비즈니스 로직 → Repository/Infra 호출)
  → Repository (JPA → PostgreSQL)
  → Service (결과 가공 → DTO 변환)
  → Controller (ResponseEntity 반환)
  → GlobalExceptionHandler (에러 시 ErrorResponse 반환)
HTTP Response
```

---

## 6. 구현 로드맵

```
MVP-1 ✅ (완료)          MVP-2 ✅ (완료)
────────────────        ────────────────
JWT 로그인/회원가입      Refresh Token
사용자 CRUD             CORS 설정
에러 핸들링             AI 동기 생성
                       헬스체크
                       단위 테스트 11개

MVP-3 ✅ (완료)          MVP-4 (확장)
────────────────        ────────────────
Redis 캐시/큐           Kafka 이벤트
비동기 AI 생성          OAuth2 소셜 로그인
AI 생성 이력 저장       비밀번호 재설정
관리자 API             알림 시스템
Rate Limiting          프로필 이미지
감사 로그              모니터링 (Grafana)
테스트 21개
```

### MVP-4 우선순위

| 순서 | 기능 | 이유 |
|------|------|------|
| 1 | OAuth2 소셜 로그인 | 사용자 유입 확대, 가입 허들 제거 |
| 2 | 비밀번호 재설정 | 필수 사용자 기능 |
| 3 | Kafka 이벤트 스트리밍 | 서비스 간 결합도 감소, 확장성 |
| 4 | 알림 시스템 | 비동기 작업 완료 알림, UX 개선 |
| 5 | 모니터링 (Grafana) | 운영 가시성 |
| 6 | 프로필 이미지 | 사용자 경험 개선 |
