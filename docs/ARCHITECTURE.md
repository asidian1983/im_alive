# Architecture Document

## Tech Stack
| 항목 | 기술 |
|------|------|
| Language | Java 17+ |
| Framework | Spring Boot 3.2 |
| Build | Gradle |
| Database | PostgreSQL |
| ORM | Spring Data JPA (Hibernate) |
| Auth | JWT (jjwt 0.12.5) + Spring Security |
| AI | Anthropic Claude API (WebClient) |
| Monitoring | Spring Actuator |

---

## Project Structure
```
src/main/java/com/project/
├── ImAliveApplication.java          # Spring Boot entry point
├── config/
│   ├── SecurityConfig.java          # Spring Security 설정 (stateless, JWT filter)
│   ├── JwtAuthenticationFilter.java # Bearer 토큰 추출 및 인증
│   ├── CorsConfig.java             # CORS 정책 설정
│   └── GlobalExceptionHandler.java  # 전역 에러 핸들러
├── controller/
│   ├── AuthController.java          # 인증 API (signup, login, refresh, logout)
│   ├── UserController.java          # 사용자 API (me, update, delete)
│   └── AiController.java           # AI API (generate)
├── service/
│   ├── AuthService.java             # 인증 비즈니스 로직
│   ├── UserService.java             # 사용자 비즈니스 로직
│   └── AiService.java              # AI 비즈니스 로직
├── domain/
│   ├── User.java                    # 사용자 엔티티 (id, email, password, name, role)
│   └── RefreshToken.java           # 리프레시 토큰 엔티티
├── repository/
│   ├── UserRepository.java          # 사용자 JPA 리포지토리
│   └── RefreshTokenRepository.java # 리프레시 토큰 리포지토리
├── dto/
│   ├── LoginRequest.java            # 로그인 요청
│   ├── SignupRequest.java           # 회원가입 요청
│   ├── AuthResponse.java           # 인증 응답 (accessToken + refreshToken)
│   ├── TokenRefreshRequest.java    # 토큰 갱신 요청
│   ├── UpdateUserRequest.java      # 사용자 수정 요청
│   ├── UserResponse.java           # 사용자 응답
│   ├── AiRequest.java              # AI 요청
│   ├── AiResponse.java             # AI 응답
│   └── ErrorResponse.java          # 에러 응답
└── infra/
    ├── JwtProvider.java             # JWT 생성/검증/파싱
    └── AiClient.java               # Anthropic API 클라이언트
```

---

## Authentication Flow

```
1. Signup/Login
   Client → POST /api/auth/signup or /login
   Server → BCrypt 검증 → JWT accessToken(1h) + refreshToken(7d) 발급

2. Authenticated Request
   Client → Authorization: Bearer <accessToken>
   JwtAuthenticationFilter → 토큰 검증 → SecurityContext에 userId 설정

3. Token Refresh
   Client → POST /api/auth/refresh { refreshToken }
   Server → refresh token 검증 → 기존 삭제 → 새 accessToken + refreshToken 발급

4. Logout
   Client → POST /api/auth/logout (with Bearer token)
   Server → 해당 사용자 refresh token 전체 삭제
```

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

---

## Configuration

### Environment Variables
| 변수 | 설명 | 기본값 |
|------|------|--------|
| DB_USERNAME | PostgreSQL 사용자명 | postgres |
| DB_PASSWORD | PostgreSQL 비밀번호 | postgres |
| JWT_SECRET | JWT 서명 키 (256bit+) | (개발용 기본값) |
| CORS_ORIGINS | 허용 Origin (쉼표 구분) | http://localhost:3000 |
| AI_API_KEY | Anthropic API 키 | (없음) |
| AI_API_URL | AI API URL | https://api.anthropic.com/v1/messages |
| AI_MODEL | AI 모델명 | claude-sonnet-4-20250514 |
| AI_MAX_TOKENS | AI 최대 토큰 수 | 1024 |

---

## MVP Progress

| Phase | Status | Features |
|-------|--------|----------|
| MVP-1 | ✅ Done | JWT 로그인/회원가입, 사용자 CRUD, 에러 핸들링 |
| MVP-2 | ✅ Done | Refresh Token, CORS, AI 서비스, 헬스체크, 테스트 |
| MVP-3 | Planned | Redis 캐시/큐, 비동기 AI 생성, 관리자 API, Rate Limiting |
| MVP-4 | Planned | Kafka, OAuth2 소셜 로그인, 알림, 모니터링 |
