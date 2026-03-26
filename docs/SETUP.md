# Setup Guide

## Prerequisites
- Java 17+
- PostgreSQL
- Redis
- (Optional) Anthropic API Key

## 1. Database Setup
```bash
# PostgreSQL에 데이터베이스 생성
createdb im_alive

# 또는 psql에서
psql -U postgres -c "CREATE DATABASE im_alive;"
```

## 2. Redis Setup
```bash
# macOS
brew install redis
brew services start redis

# Docker
docker run -d --name redis -p 6379:6379 redis:alpine

# 확인
redis-cli ping  # → PONG
```

## 3. Environment Variables
```bash
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-256-bit-secret-key-change-this-in-production
export REDIS_HOST=localhost
export REDIS_PORT=6379
export AI_API_KEY=sk-ant-xxxxx   # Anthropic API key (optional)
export AI_RATE_LIMIT=10          # AI 분당 요청 제한 (기본 10)
```

## 4. Build & Run
```bash
# Gradle wrapper 생성 (최초 1회)
gradle wrapper

# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 테스트
./gradlew test
```

## 5. Verify
```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password123","name":"테스트"}'

# 로그인 (accessToken + refreshToken 반환)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password123"}'

# 프로필 조회
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"

# AI 동기 생성
curl -X POST http://localhost:8080/api/ai/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"prompt":"Hello, what is Spring Boot?"}'

# AI 비동기 생성
curl -X POST http://localhost:8080/api/ai/generate-async \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"prompt":"대용량 프롬프트..."}'

# 비동기 작업 상태 확인
curl http://localhost:8080/api/ai/jobs/<jobId> \
  -H "Authorization: Bearer <accessToken>"

# AI 생성 이력
curl http://localhost:8080/api/ai/history \
  -H "Authorization: Bearer <accessToken>"

# 토큰 갱신
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

## 6. Docker Compose (선택)
```yaml
# docker-compose.yml
version: '3.8'
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: im_alive
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"

  redis:
    image: redis:alpine
    ports:
      - "6379:6379"
```

```bash
docker-compose up -d
```
