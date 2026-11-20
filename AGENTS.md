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

## 3. Audit Columns & Soft Delete Standards

### 3.1. Audit Columns Specification
Setiap tabel entitas utama wajib memiliki 6 kolom audit standar berikut:

| Kolom | Tipe Data | Nullable | Default DB | Keterangan |
|---|---|---|---|---|
| `created_at` | `TIMESTAMPTZ` | `YES` (NULL) | *Tidak ada* | Waktu pembuatan record |
| `created_by` | `BIGINT` | `YES` (NULL) | *Tidak ada* | ID user pembuat (`id` internal) |
| `updated_at` | `TIMESTAMPTZ` | `YES` (NULL) | *Tidak ada* | Waktu update terakhir |
| `updated_by` | `BIGINT` | `YES` (NULL) | *Tidak ada* | ID user pengubah (`id` internal) |
| `deleted_at` | `TIMESTAMPTZ` | `YES` (NULL) | *Tidak ada* | Waktu soft delete |
| `deleted_by` | `BIGINT` | `YES` (NULL) | *Tidak ada* | ID user penghapus (`id` internal) |

### 3.2. Aturan Penting Audit Columns
1. **Semua Kolom Nullable**: Kolom audit dibuat tanpa `NOT NULL`.
2. **Tanpa Nilai Default DB**:
   - **DILARANG** menambahkan `DEFAULT CURRENT_TIMESTAMP` atau default trigger di database.
   - Seluruh nilai audit **wajib diisi oleh aplikasi** (Spring Boot service layer / auditing listener / context).
   - *Tujuan*: Memudahkan audit internal, aman ketika import data massal / migrasi data historis tanpa tertimpa default clock database.
3. **Information Hiding untuk `*_by`**:
   - Kolom `created_by`, `updated_by`, dan `deleted_by` menyimpan `BIGINT` (merujuk ke `id` internal user).
   - Jika informasi pembuat/pengubah perlu dikirim ke Frontend / API, **DILARANG** mengekspos bigint tersebut. Wajib di-resolve dan diekspos sebagai `created_by_uuid`, `updated_by_uuid`, atau `deleted_by_uuid`.

### 3.3. Soft Delete & Partial Unique Index
Karena sistem menggunakan **Soft Delete** (`deleted_at IS NULL` menandakan data aktif):
- **DILARANG** menggunakan unique constraint standar tabel (`CONSTRAINT uq_name UNIQUE (column)`).
  *Alasan*: Di Postgres, unique constraint standar akan memicu error konflik jika record yang sudah dihapus (soft-deleted) dibuat kembali dengan nilai kolom yang sama.
- **WAJIB menggunakan Partial Unique Index**:
  Gunakan filtered index dengan klausa `WHERE deleted_at IS NULL` untuk setiap kolom yang memerlukan keunikan data bisnis:
  ```sql
  CREATE UNIQUE INDEX idx_<table_name>_<column>_unique_active 
  ON <table_name> (<column>) 
  WHERE deleted_at IS NULL;
  ```
- **Manfaat**:
  - Hanya data yang aktif yang dicek keunikannya.
  - Data yang sudah di-soft delete (`deleted_at IS NOT NULL`) tidak menghalangi penambahan data baru dengan nilai identik.
  - Mempercepat query query filter aktif (`WHERE deleted_at IS NULL AND <column> = :val`).

---

## 4. Schema DDL Pattern Example

Berikut adalah contoh standar DDL lengkap untuk tabel baru yang mengimplementasikan seluruh standar di atas:

```sql
-- 1. Tabel Utama (contoh: categories)
CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,

    -- Kolom Audit (semua nullable, tanpa default DB)
    created_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_at TIMESTAMPTZ,
    updated_by BIGINT,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

-- Hash index pada uuid untuk equality lookup cepat O(1)
CREATE INDEX idx_categories_uuid_hash ON categories USING HASH (uuid);

-- Partial Unique Index untuk kolom unik pada data aktif (soft delete)
CREATE UNIQUE INDEX idx_categories_slug_unique_active 
ON categories (slug) 
WHERE deleted_at IS NULL;


-- 2. Tabel Berelasi (contoh: products)
CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    category_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(15, 2) NOT NULL,

    -- Kolom Audit (semua nullable, tanpa default DB)
    created_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_at TIMESTAMPTZ,
    updated_by BIGINT,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,

    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);

-- Hash index pada uuid produk
CREATE INDEX idx_products_uuid_hash ON products USING HASH (uuid);

-- B-Tree index pada foreign key internal untuk optimasi JOIN
CREATE INDEX idx_products_category_id ON products (category_id);

-- Partial Unique Index untuk SKU unik pada produk yang aktif
CREATE UNIQUE INDEX idx_products_sku_unique_active 
ON products (sku) 
WHERE deleted_at IS NULL;
```

---

## 5. DTO & Model Code Patterns

### 5.1. Request DTO
```java
public record CreateProductRequest(
    @NotBlank
    String sku,

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

### 5.2. Response DTO
```java
public record ProductResponse(
    // Menggunakan uuid, id bigint internal TIDAK BOLEH diekspos
    UUID uuid,
    String sku,
    String name,
    BigDecimal price,

    // Relasi diekspos sebagai *_uuid
    UUID categoryUuid,

    // Audit fields ke FE (hanya timestamp & uuid user bila diperlukan)
    Instant createdAt,
    UUID createdByUuid,
    Instant updatedAt,
    UUID updatedByUuid
) {}
```

### 5.3. Service Layer Pattern (Create, Update & Soft Delete)
```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, Long currentUserId) {
        // 1. Resolve categoryUuid ke categoryId internal
        Long categoryId = categoryRepository.findIdByUuid(request.categoryUuid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori tidak ditemukan"));

        Instant now = Instant.now();

        // 2. Simpan produk (nilai audit diisi oleh aplikasi)
        Product product = Product.builder()
                .uuid(UUID.randomUUID())
                .categoryId(categoryId)
                .sku(request.sku())
                .name(request.name())
                .price(request.price())
                .createdAt(now)
                .createdBy(currentUserId)
                .updatedAt(now)
                .updatedBy(currentUserId)
                .build();
        productRepository.save(product);

        // 3. Mapping ke Response DTO (hanya UUID yang keluar)
        return toResponse(product, request.categoryUuid(), null);
    }

    @Transactional
    public void softDeleteProduct(UUID productUuid, Long currentUserId) {
        Product product = productRepository.findByUuidAndDeletedAtIsNull(productUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produk tidak ditemukan"));

        // Soft delete diisi oleh aplikasi
        product.setDeletedAt(Instant.now());
        product.setDeletedBy(currentUserId);
        productRepository.save(product);
    }
}
```

---

## 6. Lombok Standards & Best Practices

Proyek ini menggunakan Lombok untuk mengeliminasi boilerplate code. Seluruh agent **wajib memanfaatkan Lombok** secara optimal dengan batasan hanya menggunakan fitur yang stabil.

### 6.1. Fitur Stabil yang WAJIB Digunakan
1. **Dependency Injection**:
   - Gunakan `@RequiredArgsConstructor` pada `@Service`, `@RestController`, `@Component`, dan `@Configuration` untuk otomatis menghasilkan constructor bagi semua field `private final`.
   - **Dilarang** menulis constructor injection boilerplate manual.
   - **Dilarang** menggunakan `@Autowired` pada field injection.
2. **Logging**:
   - Gunakan anotasi `@Slf4j` pada setiap class yang membutuhkan logging.
   - **Dilarang** membuat logger manual seperti `LoggerFactory.getLogger(...)`.
3. **Entities & Models**:
   - Gunakan `@Getter` dan `@Setter`.
   - Gunakan `@Builder` untuk konstruksi objek.
   - Gunakan `@NoArgsConstructor` dan `@AllArgsConstructor`.
   - Gunakan `@ToString(exclude = ...)` untuk mencegah circular reference pada relasi.
   - Gunakan `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` dengan `@EqualsAndHashCode.Include` hanya pada field `uuid`.
4. **DTO**:
   - Java `record` tetap menjadi pilihan utama untuk immutable request/response DTO.
   - Jika membuat class DTO non-record, gunakan `@Getter`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`.

### 6.2. Batasan: DILARANG Menggunakan Fitur Experimental
- **DILARANG KERAS** menggunakan anotasi dari package `lombok.experimental.*` (seperti `@UtilityClass`, `@FieldDefaults`, `@ExtensionMethod`, `@Helper`, dll.).
- Hanya gunakan fitur inti yang stabil dan didukung secara universal oleh compiler Java 25 dan IDE plugin.

---

## 7. Development & Environment Workflows

### 6.1. Docker Services (`compose.yml`)
- Jalankan semua service pendukung (Postgres, Valkey, MinIO, Adminer, Swagger UI):
  ```bash
  docker compose up -d
  ```
- Cek status container & healthcheck:
  ```bash
  docker compose ps
  ```

### 6.2. Port Referensi
| Service | Host Port | Internal Port | Keterangan |
|---|---|---|---|
| Spring Boot App | `8080` | `8080` | Aplikasi backend utama |
| PostgreSQL | `5432` | `5432` | User: `admin`, Pass: `admin`, DB: `crud` |
| Valkey | `6379` | `6379` | Pass: `admin` |
| MinIO S3 API | `9000` | `9000` | User: `admin`, Pass: `admin123` |
| MinIO Console | `9001` | `9001` | Web UI MinIO |
| Adminer | `8083` | `8080` | Web UI PostgreSQL |
| Swagger UI | `8085` | `8080` | OpenAPI spec viewer |

### 6.3. OpenAPI Specification
- Lokasi spec: `openapi/openapi.yaml`
- Sinkronisasi otomatis ke Swagger UI:
  ```bash
  make openapi-watch
  ```
- Sinkronisasi manual sekali jalan:
  ```bash
  make openapi-sync
  ```

### 6.4. Build & Testing
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

## 8. Coding & Git Rules for Agents
1. **No ID Leakage**: Selalu review seluruh DTO dan Controller agar tidak ada `Long id` atau `id` internal (termasuk `created_by`, `updated_by`, `deleted_by`) yang bocor keluar API. Selalu gunakan `*_uuid`.
2. **Audit Columns Handled by App**: Semua kolom audit (`created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `deleted_by`) harus nullable, tanpa DB default, dan nilainya diisi eksplisit oleh aplikasi.
3. **Soft Delete & Partial Unique Indexes**: Setiap constraint unique wajib memakai partial index `WHERE deleted_at IS NULL`.
4. **Consistent Lombok Usage**: Wajib manfaatkan fitur Lombok yang stabil (`@RequiredArgsConstructor`, `@Slf4j`, `@Getter`, `@Setter`, `@Builder`, dll.) untuk mereduksi boilerplate. Dilarang memakai package `lombok.experimental.*`.
5. **Consistent Symlinks**: File `CLAUDE.md` dan `GEMINI.md` adalah symlink yang menunjuk ke `AGENTS.md`. Jangan ubah symlink menjadi file terpisah.
6. **OpenAPI First / Synced**: Setiap perubahan endpoint API wajib dicerminkan pada `openapi/openapi.yaml`.
7. **Clean Git Commits**: Buat commit message yang jelas, deskriptif, dan fokus pada satu lingkup perubahan.
