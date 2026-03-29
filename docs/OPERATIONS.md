# Operations Guide

## 1. 로깅 전략 (Logback)

### 구조
```
logback-spring.xml
├── CONSOLE          → 개발 환경 (컬러, 읽기 편한 포맷)
├── CONSOLE_JSON     → 운영 환경 (JSON, 로그 수집기 호환)
├── FILE_APP         → 애플리케이션 로그 (100MB × 30일, 최대 3GB)
├── FILE_ERROR       → 에러 전용 로그 (50MB × 90일, 최대 2GB)
└── ASYNC_FILE       → 비동기 파일 쓰기 (queueSize: 1024)
```

### 프로필별 동작
| 프로필 | Appender | Root Level |
|--------|----------|------------|
| default (dev) | CONSOLE | INFO |
| prod | CONSOLE + ASYNC_FILE + FILE_ERROR | WARN (com.project: INFO) |

### Request Logging
- `RequestLoggingFilter`: 모든 요청에 `X-Request-Id` 헤더 자동 부여
- MDC를 통한 요청 추적 (`requestId`)
- Slow Request 자동 감지: 3초 이상 → WARN 로그
- 5xx → ERROR, 4xx → WARN, 정상 → DEBUG
- Actuator 경로는 로깅 제외

### 로그 조회 예시
```bash
# 에러 로그만 실시간 확인
tail -f logs/im-alive-error.log

# 특정 요청 추적
grep "abc12345" logs/im-alive.log

# Slow request 찾기
grep "SLOW" logs/im-alive.log
```

---

## 2. 모니터링 (Prometheus + Grafana)

### 아키텍처
```
App (:8080/actuator/prometheus)
  → Prometheus (:9090, 10초 간격 scrape)
    → Grafana (:3001, 대시보드)
```

### 수집 메트릭
| 카테고리 | 메트릭 | 설명 |
|----------|--------|------|
| HTTP | `http_server_requests_seconds` | 요청 수, 응답 시간 (p50/p95/p99) |
| JVM | `jvm_memory_used_bytes` | 힙/논힙 메모리 사용량 |
| JVM | `jvm_gc_pause_seconds` | GC 정지 시간 |
| JVM | `jvm_threads_live_threads` | 활성 스레드 수 |
| DB | `hikaricp_connections_active` | 활성 DB 커넥션 수 |
| DB | `hikaricp_connections_pending` | 대기 중 커넥션 수 |
| Redis | health indicator | AI 큐 사이즈 |
| System | `system_cpu_usage` | CPU 사용률 |
| System | `disk_free_bytes` | 디스크 여유 공간 |

### Grafana 대시보드 패널
1. **Request Rate** — 초당 요청 수 (method/uri/status)
2. **Response Time (p95)** — 95퍼센타일 응답 시간
3. **Error Rate (5xx)** — 서버 에러 발생률
4. **Active DB Connections** — HikariCP 활성 커넥션
5. **JVM Heap Used** — 힙 메모리 사용량
6. **AI Queue Size** — Redis 비동기 작업 큐 크기
7. **JVM GC Pause** — GC 정지 시간
8. **Connection Pool** — active/idle/pending 커넥션

### 실행 방법
```bash
# 메인 서비스 실행 후
docker-compose -f monitoring/docker-compose.monitoring.yml up -d

# Grafana 접속: http://localhost:3001 (admin/admin)
# Prometheus 접속: http://localhost:9090
```

### 알림 설정 권장
| 조건 | 심각도 | 설명 |
|------|--------|------|
| 5xx rate > 1% (5분) | Critical | 서버 에러 급증 |
| p95 응답 > 3s (5분) | Warning | 응답 지연 |
| Heap 사용 > 80% | Warning | 메모리 부족 위험 |
| DB 활성 커넥션 > 15 | Warning | 커넥션 풀 고갈 위험 |
| AI 큐 사이즈 > 50 | Warning | 비동기 작업 적체 |
| AI 큐 사이즈 > 100 | Critical | 큐 과부하 (헬스체크 DOWN) |

---

## 3. 헬스 체크 (Actuator)

### 엔드포인트
| 경로 | 권한 | 용도 |
|------|------|------|
| `/actuator/health` | Public | K8s liveness/readiness probe |
| `/actuator/health/liveness` | Public | 살아있는지 확인 |
| `/actuator/health/readiness` | Public | 트래픽 수신 가능한지 확인 |
| `/actuator/info` | Public | 앱 정보 |
| `/actuator/prometheus` | Public | Prometheus 메트릭 |
| `/actuator/metrics` | Authenticated | 상세 메트릭 |

### 헬스 체크 구성 요소
| 컴포넌트 | 체크 대상 | 설명 |
|----------|----------|------|
| `db` | PostgreSQL | JDBC 커넥션 확인 |
| `redis` | Redis | 연결 상태 확인 |
| `diskSpace` | 로컬 디스크 | 500MB 미만 시 DOWN |
| `redisQueue` (custom) | AI 작업 큐 | 큐 사이즈 100 초과 시 DOWN |

### K8s Probe 설정 (app.yaml)
```yaml
startupProbe:     # 초기 기동 (최대 100초)
  initialDelaySeconds: 10, periodSeconds: 5, failureThreshold: 20
livenessProbe:    # 살아있는지 (죽으면 재시작)
  initialDelaySeconds: 60, periodSeconds: 15, failureThreshold: 3
readinessProbe:   # 트래픽 받을 수 있는지 (실패 시 트래픽 차단)
  initialDelaySeconds: 30, periodSeconds: 10, failureThreshold: 3
```

---

## 4. 장애 대응 전략

### 4.1 Graceful Shutdown
```yaml
server.shutdown: graceful
spring.lifecycle.timeout-per-shutdown-phase: 30s
```
- SIGTERM 수신 → 새 요청 거부 → 진행 중 요청 30초 내 완료 → 종료
- K8s rolling update 시 무중단 배포 보장

### 4.2 커넥션 풀 장애 방어
```yaml
hikari:
  connection-timeout: 5s    # 커넥션 획득 대기 최대 5초
  max-lifetime: 30min       # 커넥션 최대 수명 (DB 재시작 대응)
  leak-detection: 30s       # 30초 이상 반환 안 된 커넥션 감지
```

### 4.3 AI 서비스 장애 대응
| 계층 | 전략 | 설정 |
|------|------|------|
| API 호출 | Timeout | WebClient 30초 |
| 동기 요청 | 즉시 에러 반환 | 502 Bad Gateway |
| 비동기 요청 | 자동 재시도 | 최대 3회, 실패 시 FAILED |
| Rate Limit | Redis 카운터 | 분당 10회/사용자 |
| 큐 과부하 | 헬스체크 DOWN | 큐 100 초과 시 readiness 실패 → 트래픽 차단 |

### 4.4 장애 시나리오별 대응
| 장애 | 영향 | 자동 복구 | 수동 조치 |
|------|------|----------|----------|
| DB 다운 | 전체 서비스 중단 | HikariCP 재연결 | DB 복구 후 자동 회복 |
| Redis 다운 | 큐/Rate Limit 불가 | Redis 복구 시 자동 | 큐 데이터 유실 가능, Job 상태 확인 |
| AI API 다운 | AI 기능 불가 | 비동기: 3회 재시도 | 동기 요청은 502 반환 |
| 메모리 부족 | OOM Kill | K8s 자동 재시작 | 메모리 limit 조정 |
| 디스크 부족 | 로그 중단 | 자동 로그 rotation | 아카이브 정리 |
| Pod Crash | 일시적 장애 | K8s restart + replica 2 | - |

---

## 5. 성능 개선 포인트

### 5.1 DB 최적화
```yaml
# JPA Batch Insert/Update
hibernate.jdbc.batch_size: 20
hibernate.order_inserts: true
hibernate.order_updates: true
```
- Batch 처리로 N+1 쿼리 방지
- `@Index` 설정: `user_id`, `created_at DESC`

### 5.2 커넥션 풀 튜닝
```yaml
hikari:
  maximum-pool-size: 20     # 최대 동시 DB 연결
  minimum-idle: 5           # 최소 유휴 연결 유지
  idle-timeout: 5min        # 유휴 연결 제거 시간
```
- 공식: pool_size = (core_count * 2) + effective_spindle_count
- 2 vCPU 기준: 5~20 사이 권장

### 5.3 Redis 커넥션 풀 (Lettuce)
```yaml
lettuce.pool:
  max-active: 16     # 최대 활성 커넥션
  max-idle: 8        # 최대 유휴 커넥션
  min-idle: 4        # 최소 유휴 커넥션 유지
  max-wait: 3s       # 커넥션 대기 최대 시간
```

### 5.4 Tomcat 스레드 풀
```yaml
server.tomcat:
  max-threads: 200         # 최대 워커 스레드
  min-spare-threads: 20    # 최소 대기 스레드
  accept-count: 100        # 큐 대기 최대 요청
  max-connections: 8192    # 최대 동시 커넥션
```

### 5.5 비동기 로깅
- `AsyncAppender` (queueSize: 1024) — 파일 I/O가 요청 처리를 블로킹하지 않음
- `discardingThreshold: 0` — 큐 가득 차도 로그 유실 없음

### 5.6 JVM 옵션 (Dockerfile)
```
-XX:+UseContainerSupport      # 컨테이너 메모리 인식
-XX:MaxRAMPercentage=75.0     # 힙 = 컨테이너 메모리의 75%
```

### 5.7 향후 개선 사항
| 항목 | 설명 | 우선순위 |
|------|------|---------|
| Redis Cache | 자주 조회되는 데이터 캐싱 (`@Cacheable`) | High |
| DB Read Replica | 읽기 트래픽 분산 | Medium |
| CDN | 정적 리소스 (프론트엔드) | Medium |
| Connection Pooling (PgBouncer) | DB 커넥션 효율화 | Medium |
| Async Generate API | WebFlux 완전 비동기 전환 | Low |
