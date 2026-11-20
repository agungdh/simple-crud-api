MINIO_SERVICE        ?= minio
MINIO_ALIAS          ?= local
MINIO_ENDPOINT       ?= http://localhost:9000
MINIO_ROOT_USER      ?= admin
MINIO_ROOT_PASSWORD  ?= admin123
S3_BUCKET_PRIV       ?= priv
S3_BUCKET_PUB        ?= pub

MCLI := docker compose exec -T $(MINIO_SERVICE) mcli

.DEFAULT_GOAL := help

.PHONY: help minio-alias minio-init minio-ls openapi-up openapi-sync openapi-watch

help:
	@echo "minio-init    buat bucket priv & pub beserta policy-nya (idempotent)"
	@echo "minio-ls      list bucket di MinIO"
	@echo "minio-alias   set alias mcli di dalam container"
	@echo "openapi-up    jalankan Swagger UI + copy spec awal"
	@echo "openapi-sync  copy ulang spec ke container (tanpa rebuild)"
	@echo "openapi-watch jalankan Swagger UI + auto-sync spec saat file berubah"

minio-alias:
	$(MCLI) alias set $(MINIO_ALIAS) $(MINIO_ENDPOINT) $(MINIO_ROOT_USER) $(MINIO_ROOT_PASSWORD)

minio-init: minio-alias
	$(MCLI) mb --ignore-existing $(MINIO_ALIAS)/$(S3_BUCKET_PRIV) $(MINIO_ALIAS)/$(S3_BUCKET_PUB)
	$(MCLI) anonymous set private $(MINIO_ALIAS)/$(S3_BUCKET_PRIV)
	$(MCLI) anonymous set download $(MINIO_ALIAS)/$(S3_BUCKET_PUB)

minio-ls: minio-alias
	$(MCLI) ls $(MINIO_ALIAS)

openapi-up:
	docker compose up -d openapi
	docker compose cp openapi/openapi.yaml openapi:/usr/share/nginx/html/spec/openapi.yaml

openapi-sync:
	docker compose cp openapi/openapi.yaml openapi:/usr/share/nginx/html/spec/openapi.yaml

openapi-watch: openapi-up
	docker compose watch openapi
