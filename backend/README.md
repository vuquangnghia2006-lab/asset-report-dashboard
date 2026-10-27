# Report Backend (Spring Boot)

Backend API cho dashboard **Asset & Mining Management**, dựng trên Spring Boot 3.5 / Java 21 và PostgreSQL.

## Yêu cầu

- Java 21
- Maven 3.9+
- PostgreSQL (datasource cấu hình trong `src/main/resources/application.yml`)

## Chạy

```bash
mvn spring-boot:run
```

Hoặc build jar:

```bash
mvn clean package
java -jar target/report-backend-1.0.0.jar
```

Mặc định chạy ở cổng **8090**.

## Cấu hình datasource

Sửa `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://HOST:PORT/DB
    username: YOUR_USER
    password: YOUR_PASSWORD
```

> Khuyến nghị dùng biến môi trường thay vì ghi cứng mật khẩu.

## API chính

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/report/health` | Kiểm tra trạng thái |
| GET | `/api/report/filters` | Danh sách lựa chọn bộ lọc |
| GET | `/api/report/asset-overview` | Tổng quan tài sản |
| GET | `/api/report/transformer-fleet` | Đội hình máy biến áp |
| GET | `/api/report/transformer-fleet/parallel-pairs` | Sàng lọc vận hành song song |
| GET | `/api/report/data-quality` | Chất lượng dữ liệu |

Tất cả endpoint nhận các query param lọc: `orgId`, `city`, `ward`, `voltageLevel`, `manufacturerId`, `assetClass`, `status`.

## Cấu trúc

```
src/main/java/com/atdigital/report/
├── report/          # controller, service, DTO cho báo cáo
├── mine/            # module mine
└── config/
```
