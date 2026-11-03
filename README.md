# ATDigital Report

Dashboard b├ío c├ío trß║íng th├íi t├ái sß║ún ─æiß╗çn lß╗▒c, gß╗ôm **backend Spring Boot 3.5 / Java 21** v├á **frontend Vue.js 3 + Vite**, kß║┐t nß╗æi PostgreSQL.

## Cß║Ñu tr├║c

```
.
Γö£ΓöÇΓöÇ backend/     # API Spring Boot (cß╗òng 8090)
ΓööΓöÇΓöÇ frontend/    # Giao diß╗çn Vue.js + Vite (cß╗òng 5173)
```

## Y├¬u cß║ºu

- Java 21
- Maven 3.9+ (hoß║╖c d├╣ng `mvnw`)
- Node.js 20+
- PostgreSQL

## Chß║íy backend

```bash
cd backend
# Cß║Ñu h├¼nh datasource trong src/main/resources/application.yml
./mvnw spring-boot:run
```

Backend chß║íy ß╗ƒ `http://localhost:8090`.

## Chß║íy frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend chß║íy ß╗ƒ `http://localhost:5173` v├á proxy `/api` sang backend `8090`.

Mß╗ƒ: http://localhost:5173/asset-overview

## Cß║Ñu h├¼nh datasource

Sß╗¡a `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://HOST:PORT/DB
    username: YOUR_USER
    password: YOUR_PASSWORD
```

> N├¬n d├╣ng biß║┐n m├┤i tr╞░ß╗¥ng (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`) thay v├¼ ghi cß╗⌐ng mß║¡t khß║⌐u.

## API ch├¡nh

| Method | Endpoint | M├┤ tß║ú |
|---|---|---|
| GET | `/api/report/health` | Kiß╗âm tra trß║íng th├íi |
| GET | `/api/report/filters` | Danh s├ích lß╗▒a chß╗ìn bß╗Ö lß╗ìc |
| GET | `/api/report/asset-overview` | Tß╗òng quan t├ái sß║ún |
| GET | `/api/report/transformer-fleet` | ─Éß╗Öi h├¼nh m├íy biß║┐n ├íp |
| GET | `/api/report/transformer-fleet/parallel-pairs` | S├áng lß╗ìc vß║¡n h├ánh song song |
| GET | `/api/report/data-quality` | Chß║Ñt l╞░ß╗úng dß╗» liß╗çu |

C├íc endpoint nhß║¡n query param lß╗ìc: `orgId`, `city`, `ward`, `voltageLevel`, `manufacturerId`, `assetClass`, `status`.

## Giao diß╗çn

- **Asset Overview** ΓÇö tß╗òng quan t├ái sß║ún, c├┤ng suß║Ñt theo cß║Ñp ─æiß╗çn ├íp, c╞í cß║Ñu theo tß╗ò chß╗⌐c
- **Transformer Fleet** ΓÇö ─æß╗Öi h├¼nh m├íy biß║┐n ├íp, s├áng lß╗ìc vß║¡n h├ánh song song
- **Asset Data Quality** ΓÇö ─æß╗Ö ─æß║ºy ─æß╗º dß╗» liß╗çu, c├ón bß║▒ng pha

## Chß║íy bß║▒ng Docker (2 image: backend + frontend)

Y├¬u cß║ºu: Docker Desktop ─æang chß║íy.

```bash
docker compose -f docker-compose.report.yml up -d --build
```

Hoß║╖c tr├¬n Windows chß╗ë cß║ºn **bß║Ñm ─æ├║p**:

- `run-docker.bat` ΓÇö build + chß║íy + mß╗ƒ tr├¼nh duyß╗çt
- `stop-docker.bat` ΓÇö dß╗½ng

Sau khi chß║íy:

| Th├ánh phß║ºn | URL |
|---|---|
| Web (nginx) | http://localhost:8081/asset-overview |
| API (Spring Boot) | http://localhost:8091/api/report |

Image ─æ╞░ß╗úc build:
- `asset-report-backend:latest` ΓÇö Maven build ΓåÆ JRE 17 Alpine
- `asset-report-frontend:latest` ΓÇö Node build ΓåÆ nginx Alpine (proxy `/api` sang backend)

> ─Éß╗òi cß╗òng: sß╗¡a `ports` trong `docker-compose.report.yml`.
> Cß║Ñu h├¼nh DB: sß╗¡a biß║┐n `SPRING_DATASOURCE_*` trong `docker-compose.report.yml` (n├¬n d├╣ng file `.env`).
