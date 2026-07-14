# DQL — Database Quality Library

Thư viện Java giám sát chất lượng cơ sở dữ liệu **runtime** cho ứng dụng JDBC/Spring Boot. DQL bọc `DataSource`, thu thập SQL thực thi, đọc metadata schema, chạy bộ rule phân tích và xuất báo cáo — **không cần sửa business logic** của ứng dụng.

```
App (Spring Boot / Plain Java)
        │
        ▼
  dql-spring-boot-starter  │  dql-jdbc
        │                  │
        └────────┬─────────┘
                 ▼
             dql-core        ← logic thuần Java, không phụ thuộc Spring
```

## Tính năng chính

- **Capture SQL runtime** qua `datasource-proxy` — overhead hot-path ~25 µs/query
- **10 rule phân tích** (schema + workload): thiếu PK, kiểu dữ liệu nghi ngờ, N+1, slow query, full table scan, v.v.
- **Tích hợp Spring Boot** bằng auto-configuration — chỉ cần thêm dependency
- **Tích hợp Plain Java** qua `Dql.attach(dataSource)` — core độc lập framework
- **REST API** báo cáo, metrics, transparency (collected queries, schema snapshot)
- **Hỗ trợ đa DB**: PostgreSQL, MySQL, MariaDB, SQL Server (auto-detect `ExplainAdapter`)
- **AI review** (tùy chọn): gửi context tóm tắt lên LLM API

## Yêu cầu

| Thành phần | Phiên bản |
|------------|-----------|
| Java       | 21        |
| Maven      | 3.8+      |
| Spring Boot| 4.1.0 (starter) |

## Cấu trúc module

| Module | Mô tả |
|--------|-------|
| `dql-core` | Model, collector, fingerprint, rule engine, report — **không import Spring** |
| `dql-spring-boot-starter` | Auto-config, properties, scheduler, REST controllers, ExplainAdapter |
| `dql-jdbc` | Facade Plain Java: `Dql.attach()` thay cho Spring wiring |
| `dql-demo` | Ứng dụng demo nhỏ với PostgreSQL |
| `spring-petclinic-rest` | Testbed tích hợp thực tế (app Spring Boot/JPA không sửa code) |

## Bắt đầu nhanh — Spring Boot

### 1. Thêm dependency

```xml
<dependency>
    <groupId>com.example.dql</groupId>
    <artifactId>dql-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Cấu hình `application.yml`

```yaml
dql:
  enabled: true
  slow-queries:
    threshold-ms: 1000
  n-plus-one:
    min-count: 5
    min-distinct-params: 3

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dqldemo
    username: dql
    password: dql
```

### 3. Build & chạy

```powershell
mvn -N install
mvn -pl dql-core,dql-spring-boot-starter install -DskipTests
mvn -pl dql-demo spring-boot:run
```

Starter tự động wrap `DataSource` bean và đăng ký scheduler phân tích định kỳ.

## Bắt đầu nhanh — Plain Java

Không dùng Spring? Gắn DQL trực tiếp vào `DataSource`:

```java
try (DqlRuntime dql = Dql.attach(dataSource)) {
    DataSource monitored = dql.dataSource();

    // Dùng monitored thay dataSource gốc
    try (Connection conn = monitored.getConnection()) { /* ... */ }

    QualityReport report = dql.analyzeNow();
}
```

Xem demo đầy đủ tại `dql-jdbc/src/test/java/io/demo/plain/PlainJavaDemo.java`.

## REST API

Các endpoint mặc định dưới prefix `/dql` (bật/tắt qua `dql.endpoints.enabled`):

| Endpoint | Mô tả |
|----------|-------|
| `GET /dql/findings` | Báo cáo chất lượng (findings + score) |
| `GET /dql/metrics` | Slow query count, N+1, top tables/fingerprints |
| `GET /dql/ai-context` | Context tóm tắt cho AI review |
| `GET /dql/ai-review` | Gọi LLM review (cần `dql.ai.enabled=true`) |
| `GET /dql/last-run` | Thời điểm phân tích gần nhất |
| `GET /dql/collected-queries` | Danh sách query đã capture |
| `GET /dql/schema-snapshot` | Metadata schema hiện tại |
| `GET /dql/status` | Trạng thái runtime |
| `POST /dql/enable` / `/disable` | Bật/tắt capture runtime |

## Bộ rule (R01–R10)

| ID | Rule | Mức độ | Mô tả |
|----|------|--------|-------|
| R01 | Missing Primary Key | HIGH | Bảng không có primary key |
| R02 | Suspicious Type | MEDIUM | Kiểu cột không phù hợp (vd tiền lưu `float`, ngày lưu `varchar`) |
| R03 | Nullable Risk | HIGH | Cột nullable có rủi ro (vd FK nullable) |
| R04 | Select Star | MEDIUM | Query dùng `SELECT *` |
| R05 | Slow Query | MEDIUM | Query vượt ngưỡng thời gian |
| R06 | Unindexed FK | HIGH | Foreign key chưa có index |
| R07 | Missing Index | HIGH | Cột filter nhiều nhưng thiếu index |
| R08 | Unused Index | LOW | Index không được query sử dụng |
| R09 | N+1 | HIGH | Cùng fingerprint lặp nhiều lần với param khác nhau trong 1 request |
| R10 | Full Scan | HIGH | EXPLAIN cho thấy full table scan trên bảng lớn |

Bật/tắt từng rule qua config:

```yaml
dql:
  rules:
    R04_SelectStar: false
    R08_UnusedIndex: false
```

## Cấu hình đầy đủ

```yaml
dql:
  enabled: true                          # Bật/tắt toàn bộ DQL

  analysis:
    enabled: true                        # Scheduler phân tích định kỳ
    delay-seconds: 5                     # Chu kỳ giữa các lần chạy

  endpoints:
    enabled: true                        # REST API /dql/*

  sink:
    ring-capacity: 5000                  # Số execution gần nhất giữ trong ring buffer
    max-fingerprints: 2000               # Giới hạn fingerprint tracked

  slow-queries:
    threshold-ms: 1000

  n-plus-one:
    min-count: 5
    min-distinct-params: 3

  missing-index:
    threshold-count: 10

  full-scan:
    min-estimated-rows: 1000
    max-explain-candidates: 20

  source-location:
    enabled: true                        # Gắn stack trace vào query execution
    application-prefixes: []             # Package prefix của app (vd com.mycompany)
    max-depth: 30

  ai:
    enabled: false
    api-key: ${DQL_AI_API_KEY:}
    endpoint: https://api.openai.com/v1/chat/completions
    model: gpt-4o-mini
    timeout-ms: 20000

  score:
    weights:
      HIGH: 10
      MEDIUM: 5
      LOW: 1
```

## Demo với Docker

```powershell
# Khởi động PostgreSQL + seed schema anti-pattern
docker compose up -d

# Build và chạy demo app
mvn -N install
mvn -pl dql-core,dql-spring-boot-starter,dql-demo install -DskipTests
mvn -pl dql-demo spring-boot:run
```

Gọi các endpoint demo để kích hoạt anti-pattern:

```powershell
curl.exe http://localhost:8080/demo-query          # R04: SELECT *
curl.exe http://localhost:8080/demo-n-plus-one     # R09: N+1
curl.exe http://localhost:8080/demo-n-plus-one-5
```

Sau vài giây (scheduler), kiểm tra kết quả:

```powershell
curl.exe http://localhost:8080/dql/findings
curl.exe http://localhost:8080/dql/metrics
```

Schema demo (`init.sql`) cố ý chứa anti-pattern cho từng rule — xem comment trong file để hiểu từng case.

## Demo PetClinic (tích hợp thực tế)

Ứng dụng Spring PetClinic REST là testbed chính để chứng minh DQL tích hợp vào app production-like mà không sửa business code:

```powershell
mvn -N install
mvn -pl dql-core,dql-spring-boot-starter install -DskipTests
docker compose -f spring-petclinic-rest/docker-compose.yml --profile postgres up -d
mvn -pl spring-petclinic-rest spring-boot:run "-Dspring-boot.run.arguments=--dql.ai.enabled=false"
```

Chi tiết kịch bản demo 10 bước: [`docs/demo-flow.md`](docs/demo-flow.md).

## Chạy tests

```powershell
# Toàn bộ tests DQL
mvn -pl dql-core,dql-spring-boot-starter,dql-jdbc test

# Micro-benchmark rule engine (không chạy trong mvn test mặc định)
mvn -pl dql-core test -Dtest=RuleEngineBenchmark
```

## Kiến trúc

```
┌─────────────────────────────────────────────────────────────┐
│  HTTP Request                                               │
│       │                                                     │
│       ▼                                                     │
│  RequestContextInterceptor  →  contextId (cho R09 N+1)     │
│       │                                                     │
│       ▼                                                     │
│  DqlBeanPostProcessor  →  wrap DataSource (datasource-proxy)│
│       │                                                     │
│       ▼                                                     │
│  ExecutionSink (ring buffer)  →  fingerprint + stats        │
│       │                                                     │
│       ▼  (scheduler, mỗi N giây)                           │
│  SchemaSnapshotProvider  +  RuleEngine (R01–R10)            │
│       │                                                     │
│       ▼                                                     │
│  JsonReporter  →  QualityReport  →  GET /dql/findings      │
└─────────────────────────────────────────────────────────────┘
```

- **Hot-path** (mỗi query): chỉ ghi vào ring buffer — không chạy rule, không EXPLAIN
- **Cold-path** (scheduler): chạy toàn bộ rule engine + EXPLAIN (R10) ngoài request thread

## Tài liệu bổ sung

| Tài liệu | Nội dung |
|----------|----------|
| [`docs/architecture-deep-dive.md`](docs/architecture-deep-dive.md) | Kiến trúc chi tiết, data flow |
| [`docs/demo-flow.md`](docs/demo-flow.md) | Kịch bản demo PetClinic |
| [`docs/limitations.md`](docs/limitations.md) | Hạn chế và giả định thiết kế |
| [`docs/benchmark-analysis.md`](docs/benchmark-analysis.md) | Kết quả benchmark analysis time |
| [`docs/test-matrix.md`](docs/test-matrix.md) | Ma trận verify 10 rule |

## Hạn chế đã biết

- Rule dựa trên **heuristic** — không thay thế DBA review thủ công
- R09 (N+1) cần `contextId` từ servlet interceptor — Plain Java phải gọi `beginUnitOfWork()` thủ công
- R10 (Full Scan) cache EXPLAIN — giả định schema tĩnh trong một chu kỳ phân tích
- Ring buffer bounded — workload rất lớn có thể mất execution cũ

Xem đầy đủ tại [`docs/limitations.md`](docs/limitations.md).

## License

Dự án nội bộ — phiên bản `0.1.0-SNAPSHOT`.
