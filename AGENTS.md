# Agent Guidelines & Engineering Standards

Dokumen ini adalah pedoman dan instruksi utama bagi seluruh AI Agent (Claude, Gemini, Antigravity, Copilot, dll.) saat mengembangkan kode, skema basis data, dan API di dalam repositori `simple-crud-api`.

---

## 1. Project Overview & Tech Stack

- **Platform / Runtime**: Java 25
- **Framework**: Spring Boot 4.1.2 (WebMvc, Validation, Lombok)
- **Database**: PostgreSQL 18
- **Cache / In-Memory Store**: Valkey 9 (Redis compatible)
- **Object Storage**: MinIO S3 (AWS SDK for Java v2)
- **API Documentation**: OpenAPI 3.0.3 (`openapi/openapi.yaml`) disajikan via Swagger UI di port `8085`
- **Database GUI**: Adminer di port `8083`
- **Build Tool**: Gradle (`build.gradle.kts`)

---

## 2. Core Architecture: ID & UUID Standards

### 2.1. Primary Key (`id`)
- **Tipe**: `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- **Tujuan**:
  - Digunakan sebagai identitas internal tabel untuk performa tinggi (B-Tree clustering, sekuensial, memory-efficient).
  - Seluruh relasi Foreign Key (FK) fisik antar tabel di database **wajib** mereferensikan kolom `id` (bigint) ini.
- **Batasan Keamanan (Information Hiding)**:
  - Nilai `id` bertipe bigint ini bersifat **INTERNAL DATABASE ONLY**.
  - **DILARANG KERAS** mengekspos kolom `id` ke lapisan luar (Frontend / Mobile / Public API / DTO / URL path / Query parameter).

### 2.2. Public Identifier (`uuid`)
- **Tipe**: `UUID NOT NULL DEFAULT gen_random_uuid()`
- **Indexing**:
  - Wajib menggunakan **Hash Index** tanpa constraint `UNIQUE`:
    ```sql
    CREATE INDEX idx_<table_name>_uuid_hash ON <table_name> USING HASH (uuid);
    ```
  - *Catatan teknis PostgreSQL*: Indeks `HASH` di PostgreSQL secara native tidak mendukung constraint `UNIQUE`. Indeks ini memberikan performa $O(1)$ untuk equality query (`WHERE uuid = :uuid`).
- **Eksposur Frontend**:
  - Kolom `uuid` inilah satu-satunya identifier entitas yang diekspos ke Frontend / API consumer.
  - Semua endpoint CRUD menggunakan `uuid` sebagai identifier (contoh: `/api/v1/items/{uuid}`).

### 2.3. Foreign Key & DTO Mapping Rule
- **Di Database (Physical DB Level)**:
  - Relasi fisik tetap menggunakan foreign key bertipe `BIGINT` yang mereferensikan `id` tabel induk (contoh: `category_id BIGINT REFERENCES categories(id)` atau nama umum `fk_id`).
  - Tambahkan B-Tree index pada kolom foreign key untuk performa query JOIN.
- **Di DTO / API Level (Request & Response)**:
  - Field foreign key diubah dari format `*_id` menjadi `*_uuid` (contoh: `category_uuid`, `user_uuid`, atau `fk_uuid`).
  - Frontend **hanya** mengetahui dan mengirimkan `*_uuid`.
- **Backend Responsibility (Resolution Layer)**:
  - **Saat Create / Update (Write)**:
    Backend service menerima `*_uuid` dari DTO, melakukan lookup internal untuk mendapatkan `id` (bigint) tabel relasi melalui hash index:
    ```sql
    SELECT id FROM categories WHERE uuid = :category_uuid
    ```
    Lalu menyimpan `category_id` (bigint) tersebut ke database. Jika record tidak ditemukan, kembalikan HTTP 400 / 404 (Entity Not Found).
  - **Saat Fetch / Query (Read)**:
    Backend melakukan JOIN atau mapping dari entitas ke DTO, mengubah relasi menjadi `*_uuid` (misal mengambil `category.getUuid()`) untuk dikirimkan ke client.

---

## 3. Schema DDL Pattern Example

Berikut adalah contoh standar DDL untuk setiap tabel baru:

```sql
-- 1. Tabel Utama (contoh: categories)
CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Hash index pada uuid tanpa unique constraint
CREATE INDEX idx_categories_uuid_hash ON categories USING HASH (uuid);


-- 2. Tabel Berelasi (contoh: products)
CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    category_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(15, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);

-- Hash index pada uuid produk
CREATE INDEX idx_products_uuid_hash ON products USING HASH (uuid);

-- B-Tree index pada foreign key internal untuk optimasi JOIN
CREATE INDEX idx_products_category_id ON products (category_id);
```

---

## 4. DTO & Model Code Patterns

### 4.1. Request DTO
```java
public record CreateProductRequest(
    @NotBlank
    String name,

    @NotNull
    @Positive
    BigDecimal price,

    // Menggunakan *_uuid, BUKAN *_id
    @NotNull
    UUID categoryUuid
) {}
```

### 4.2. Response DTO
```java
public record ProductResponse(
    // Menggunakan uuid, id bigint TIDAK BOLEH diekspos
    UUID uuid,
    String name,
    BigDecimal price,

    // Relasi diekspos sebagai *_uuid
    UUID categoryUuid,

    Instant createdAt,
    Instant updatedAt
) {}
```

### 4.3. Resolution Logic di Service Layer
```java
@Transactional
public ProductResponse createProduct(CreateProductRequest request) {
    // 1. Resolve categoryUuid ke categoryId internal
    Long categoryId = categoryRepository.findIdByUuid(request.categoryUuid())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori tidak ditemukan"));

    // 2. Simpan produk menggunakan categoryId internal
    Product product = Product.builder()
            .uuid(UUID.randomUUID())
            .categoryId(categoryId)
            .name(request.name())
            .price(request.price())
            .build();
    productRepository.save(product);

    // 3. Mapping kembali ke Response DTO dengan UUID
    return new ProductResponse(
            product.getUuid(),
            product.getName(),
            product.getPrice(),
            request.categoryUuid(),
            product.getCreatedAt(),
            product.getUpdatedAt()
    );
}
```

---

## 5. Development & Environment Workflows

### 5.1. Docker Services (`compose.yml`)
- Jalankan semua service pendukung (Postgres, Valkey, MinIO, Adminer, Swagger UI):
  ```bash
  docker compose up -d
  ```
- Cek status container & healthcheck:
  ```bash
  docker compose ps
  ```

### 5.2. Port Referensi
| Service | Host Port | Internal Port | Keterangan |
|---|---|---|---|
| Spring Boot App | `8080` | `8080` | Aplikasi backend utama |
| PostgreSQL | `5432` | `5432` | User: `admin`, Pass: `admin`, DB: `crud` |
| Valkey | `6379` | `6379` | Pass: `admin` |
| MinIO S3 API | `9000` | `9000` | User: `admin`, Pass: `admin123` |
| MinIO Console | `9001` | `9001` | Web UI MinIO |
| Adminer | `8083` | `8080` | Web UI PostgreSQL |
| Swagger UI | `8085` | `8080` | OpenAPI spec viewer |

### 5.3. OpenAPI Specification
- Lokasi spec: `openapi/openapi.yaml`
- Sinkronisasi otomatis ke Swagger UI:
  ```bash
  make openapi-watch
  ```
- Sinkronisasi manual sekali jalan:
  ```bash
  make openapi-sync
  ```

### 5.4. Build & Testing
- Menjalankan seluruh test:
  ```bash
  ./gradlew test
  ```
- Menjalankan aplikasi secara lokal:
  ```bash
  ./gradlew bootRun
  ```
- Build jar:
  ```bash
  ./gradlew build
  ```

---

## 6. Coding & Git Rules for Agents
1. **No ID Leakage**: Selalu review seluruh DTO dan Controller agar tidak ada `Long id` atau `id` internal yang bocor keluar API.
2. **Consistent Symlinks**: File `CLAUDE.md` dan `GEMINI.md` adalah symlink yang menunjuk ke `AGENTS.md`. Jangan ubah symlink menjadi file terpisah.
3. **OpenAPI First / Synced**: Setiap perubahan endpoint API wajib dicerminkan pada `openapi/openapi.yaml`.
4. **Clean Git Commits**: Buat commit message yang jelas, deskriptif, dan fokus pada satu lingkup perubahan.
