# Setup Guide

## Prerequisites
- Java 17+
- PostgreSQL
- (Optional) Anthropic API Key

## 1. Database Setup
```bash
# PostgreSQL에 데이터베이스 생성
createdb im_alive

# 또는 psql에서
psql -U postgres -c "CREATE DATABASE im_alive;"
```

## 2. Environment Variables
```bash
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-256-bit-secret-key-change-this-in-production
export AI_API_KEY=sk-ant-xxxxx  # Anthropic API key (optional)
```

## 3. Build & Run
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

## 4. Verify
```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password123","name":"테스트"}'

# 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password123"}'

# 프로필 조회 (토큰 필요)
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"
```
