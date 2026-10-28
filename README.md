# Asset & Mining Management Dashboard

Dashboard báo cáo trạng thái tài sản điện lực, gồm **backend Spring Boot 3.5 / Java 21** và **frontend Vue.js 3 + Vite**, kết nối PostgreSQL.

## Cấu trúc

```
.
├── backend/     # API Spring Boot (cổng 8090)
└── frontend/    # Giao diện Vue.js + Vite (cổng 5173)
```

## Yêu cầu

- Java 21
- Maven 3.9+ (hoặc dùng `mvnw`)
- Node.js 20+
- PostgreSQL

## Chạy backend

```bash
cd backend
# Cấu hình datasource trong src/main/resources/application.yml
./mvnw spring-boot:run
```

Backend chạy ở `http://localhost:8090`.

## Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend chạy ở `http://localhost:5173` và proxy `/api` sang backend `8090`.

Mở: http://localhost:5173/asset-overview

## Cấu hình datasource

Sửa `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://HOST:PORT/DB
    username: YOUR_USER
    password: YOUR_PASSWORD
```

> Nên dùng biến môi trường (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`) thay vì ghi cứng mật khẩu.

## API chính

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/report/health` | Kiểm tra trạng thái |
| GET | `/api/report/filters` | Danh sách lựa chọn bộ lọc |
| GET | `/api/report/asset-overview` | Tổng quan tài sản |
| GET | `/api/report/transformer-fleet` | Đội hình máy biến áp |
| GET | `/api/report/transformer-fleet/parallel-pairs` | Sàng lọc vận hành song song |
| GET | `/api/report/data-quality` | Chất lượng dữ liệu |

Các endpoint nhận query param lọc: `orgId`, `city`, `ward`, `voltageLevel`, `manufacturerId`, `assetClass`, `status`.

## Giao diện

- **Asset Overview** — tổng quan tài sản, công suất theo cấp điện áp, cơ cấu theo tổ chức
- **Transformer Fleet** — đội hình máy biến áp, sàng lọc vận hành song song
- **Asset Data Quality** — độ đầy đủ dữ liệu, cân bằng pha

## Chạy bằng Docker (2 image: backend + frontend)

Yêu cầu: Docker Desktop đang chạy.

```bash
docker compose -f docker-compose.report.yml up -d --build
```

Hoặc trên Windows chỉ cần **bấm đúp**:

- `run-docker.bat` — build + chạy + mở trình duyệt
- `stop-docker.bat` — dừng

Sau khi chạy:

| Thành phần | URL |
|---|---|
| Web (nginx) | http://localhost:8081/asset-overview |
| API (Spring Boot) | http://localhost:8091/api/report |

Image được build:
- `asset-report-backend:latest` — Maven build → JRE 17 Alpine
- `asset-report-frontend:latest` — Node build → nginx Alpine (proxy `/api` sang backend)

> Đổi cổng: sửa `ports` trong `docker-compose.report.yml`.
> Cấu hình DB: sửa biến `SPRING_DATASOURCE_*` trong `docker-compose.report.yml` (nên dùng file `.env`).
