# Grafana — Samopisec (CSV)

Локальная Grafana читает `datapoints.csv` напрямую (без бекенда).

## Запуск

```sh
# 1. Синхронизировать CSV с устройства в /tmp/samopisec.csv
./scripts/sync-csv.sh            # один раз
./scripts/sync-csv.sh --watch    # следить (fswatch/poll 2s)

# 2. Поднять Grafana
cd grafana && docker compose up -d
# -> http://localhost:3000  admin/admin

# Datasource: CSV (marcusolsson-csv-datasource) -> /tmp/samopisec.csv (header id,button_id,ts)
# Dashboard: Samopisec (4 панели: Raw, Cumulative, Totals per button, Per-hour)
```

Альтернатива HTTP (если file-драйвер не завёлся): `csv-server` отдаёт `/tmp/samopisec.csv` на http://localhost:8001/samopisec.csv — переключатель datasource на `grafana-infinity-datasource` (URL http://csv-server:8000/samopisec.csv, parser CSV).

Сброс: `docker compose down`.
