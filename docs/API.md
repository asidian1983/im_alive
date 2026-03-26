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
  "timestamp": "2026-03-27T10:00:00"
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
  "createdAt": "2026-03-27T10:00:00"
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
  "createdAt": "2026-03-27T10:00:00"
}
```

---

### DELETE /api/users/me
계정 삭제

**Headers** `Authorization: Bearer <accessToken>`

**Response** `204 No Content`

---

## AI API (인증 필요 + Rate Limit)

모든 AI API는 분당 10회 요청 제한이 적용됩니다. 초과 시 `429 Too Many Requests` 반환.

### POST /api/ai/generate
AI 텍스트 생성 (동기)

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

생성 결과는 자동으로 이력에 저장됩니다.

---

### POST /api/ai/generate-async
AI 텍스트 생성 (비동기)

대용량 프롬프트 처리에 적합합니다. Redis Queue를 통해 Worker가 백그라운드에서 처리합니다.

**Headers** `Authorization: Bearer <accessToken>`

**Request**
```json
{
  "prompt": "대용량 프롬프트...",
  "maxTokens": 4096
}
```

**Validation**
- prompt: 필수, 최대 50,000자
- maxTokens: 선택, 최대 4,096

**Response** `202 Accepted`
```json
{
  "jobId": 1,
  "status": "QUEUED",
  "result": null,
  "errorMessage": null,
  "createdAt": "2026-03-27T10:00:00",
  "updatedAt": "2026-03-27T10:00:00"
}
```

---

### GET /api/ai/jobs/{jobId}
비동기 작업 상태 조회 (폴링)

**Headers** `Authorization: Bearer <accessToken>`

**Response** `200 OK`

처리중:
```json
{
  "jobId": 1,
  "status": "PROCESSING",
  "result": null,
  "errorMessage": null,
  "createdAt": "2026-03-27T10:00:00",
  "updatedAt": "2026-03-27T10:00:05"
}
```

완료:
```json
{
  "jobId": 1,
  "status": "COMPLETED",
  "result": "AI 생성 결과...",
  "errorMessage": null,
  "createdAt": "2026-03-27T10:00:00",
  "updatedAt": "2026-03-27T10:00:15"
}
```

실패 (3회 재시도 후):
```json
{
  "jobId": 1,
  "status": "FAILED",
  "result": null,
  "errorMessage": "AI service call failed",
  "createdAt": "2026-03-27T10:00:00",
  "updatedAt": "2026-03-27T10:01:00"
}
```

**Status Values:** `QUEUED` → `PROCESSING` → `COMPLETED` / `FAILED`

---

### GET /api/ai/history
AI 생성 이력 목록 (페이징)

**Headers** `Authorization: Bearer <accessToken>`

**Query Parameters**
- `page`: 페이지 번호 (기본 0)
- `size`: 페이지 크기 (기본 20)

**Response** `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "prompt": "자바 Spring Boot의 장점을 설명해줘",
      "result": "Spring Boot는 ...",
      "model": "claude-sonnet-4-20250514",
      "tokensUsed": 256,
      "createdAt": "2026-03-27T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### GET /api/ai/history/{id}
AI 생성 이력 상세

**Headers** `Authorization: Bearer <accessToken>`

**Response** `200 OK`
```json
{
  "id": 1,
  "prompt": "자바 Spring Boot의 장점을 설명해줘",
  "result": "Spring Boot는 ...",
  "model": "claude-sonnet-4-20250514",
  "tokensUsed": 256,
  "createdAt": "2026-03-27T10:00:00"
}
```

**Error** `404 Not Found` — AI generation not found
**Error** `403 Forbidden` — 다른 사용자의 이력 접근 시

---

### DELETE /api/ai/history/{id}
AI 생성 이력 삭제

**Headers** `Authorization: Bearer <accessToken>`

**Response** `204 No Content`

---

## Admin API (ROLE_ADMIN 전용)

일반 사용자 접근 시 `403 Forbidden` 반환. 모든 변경 행동은 감사 로그에 자동 기록됩니다.

### GET /api/admin/users
사용자 목록 (페이징)

**Headers** `Authorization: Bearer <accessToken>` (ADMIN)

**Query Parameters**
- `page`: 페이지 번호 (기본 0)
- `size`: 페이지 크기 (기본 20)

**Response** `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "email": "user@example.com",
      "name": "홍길동",
      "role": "USER",
      "createdAt": "2026-03-27T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

---

### GET /api/admin/users/{id}
사용자 상세

**Response** `200 OK` — UserResponse 동일 포맷

---

### PATCH /api/admin/users/{id}
사용자 역할/이름 변경

**Request**
```json
{
  "name": "변경된이름",
  "role": "ADMIN"
}
```

**Response** `200 OK` — UserResponse 동일 포맷

---

### DELETE /api/admin/users/{id}
사용자 삭제

**Response** `204 No Content`

---

### GET /api/admin/stats
시스템 통계

**Response** `200 OK`
```json
{
  "totalUsers": 150,
  "totalGenerations": 3200,
  "totalTokensUsed": 850000,
  "activeJobCount": 3
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
  "timestamp": "2026-03-27T10:00:00"
}
```

| HTTP Status | 설명 |
|-------------|------|
| 400 | 잘못된 요청 (유효성 검증 실패, 비즈니스 에러) |
| 401 | 인증 실패 (토큰 없음/만료) |
| 403 | 권한 없음 (Forbidden) |
| 404 | 리소스 없음 (Not Found) |
| 409 | 중복 (Conflict) |
| 429 | 요청 제한 초과 (Rate Limit) |
| 502 | AI 서비스 오류 (Bad Gateway) |
| 500 | 서버 내부 오류 |
