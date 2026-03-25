# API Documentation

## Base URL
```
http://localhost:8080
```

## Authentication
모든 인증 필요 API는 `Authorization: Bearer <accessToken>` 헤더를 포함해야 합니다.

---

## Auth API

### POST /api/auth/signup
회원가입

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

**Validation**
- email: 필수, 이메일 형식
- password: 필수, 최소 8자
- name: 필수

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "홍길동"
}
```

**Error** `400 Bad Request`
```json
{
  "status": 400,
  "message": "Email already in use",
  "errors": null,
  "timestamp": "2026-03-25T10:00:00"
}
```

---

### POST /api/auth/login
로그인

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "홍길동"
}
```

**Error** `400 Bad Request` — Invalid email or password

---

### POST /api/auth/refresh
토큰 갱신 (인증 불필요)

**Request**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...(new)",
  "refreshToken": "new-uuid-token",
  "email": "user@example.com",
  "name": "홍길동"
}
```

**Error** `400 Bad Request` — Invalid refresh token / Refresh token expired

---

### POST /api/auth/logout
로그아웃 (인증 필요)

**Headers** `Authorization: Bearer <accessToken>`

**Response** `204 No Content`

---

## User API (인증 필요)

### GET /api/users/me
내 프로필 조회

**Headers** `Authorization: Bearer <accessToken>`

**Response** `200 OK`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "USER",
  "createdAt": "2026-03-25T10:00:00"
}
```

---

### PATCH /api/users/me
프로필 수정

**Headers** `Authorization: Bearer <accessToken>`

**Request** (모든 필드 선택)
```json
{
  "name": "새이름",
  "password": "newpassword123"
}
```

**Response** `200 OK`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "새이름",
  "role": "USER",
  "createdAt": "2026-03-25T10:00:00"
}
```

---

### DELETE /api/users/me
계정 삭제

**Headers** `Authorization: Bearer <accessToken>`

**Response** `204 No Content`

---

## AI API (인증 필요)

### POST /api/ai/generate
AI 텍스트 생성

**Headers** `Authorization: Bearer <accessToken>`

**Request**
```json
{
  "prompt": "자바 Spring Boot의 장점을 설명해줘"
}
```

**Validation**
- prompt: 필수, 최대 10,000자

**Response** `200 OK`
```json
{
  "content": "Spring Boot는 ...",
  "model": "claude-sonnet-4-20250514",
  "tokensUsed": 256
}
```

---

## System API (인증 불필요)

### GET /actuator/health
서버 상태 확인

**Response** `200 OK`
```json
{
  "status": "UP"
}
```

### GET /actuator/info
서버 정보

**Response** `200 OK`
```json
{}
```

---

## Error Response Format

모든 에러는 동일한 포맷으로 반환됩니다.

```json
{
  "status": 400,
  "message": "에러 메시지",
  "errors": {
    "field": "validation message"
  },
  "timestamp": "2026-03-25T10:00:00"
}
```

| HTTP Status | 설명 |
|-------------|------|
| 400 | 잘못된 요청 (유효성 검증 실패, 비즈니스 에러) |
| 401 | 인증 실패 (토큰 없음/만료) |
| 403 | 권한 없음 |
