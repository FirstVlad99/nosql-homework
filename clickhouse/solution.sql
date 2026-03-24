-- Решение заданий по ClickHouse

-- 1. Создание таблицы
CREATE TABLE IF NOT EXISTS server_logs
(
    user_id UInt32,
    timestamp DateTime,
    endpoint String,
    response_time_ms UInt32,
    status_code UInt16
) ENGINE = MergeTree()
    ORDER BY (timestamp,endpoint);
--DROP TABLE server_logs;
-- 2. Загрузка данных из CSV
-- Подсказка: можно использовать clickhouse-client с параметром --query
-- Пример команды (выполняется в терминале):
-- cat server_logs.csv | clickhouse-client --query="INSERT INTO server_logs FORMAT CSVWithNames"


-- 3. Запрос: Топ-5 самых медленных endpoint'ов (по среднему времени ответа)
SELECT endpoint "Эндпоинт",
       round(avg(response_time_ms),2) "Среднее время ответа в мс"
FROM server_logs
GROUP BY endpoint
ORDER BY "Среднее время ответа в мс" DESC
LIMIT 5;

-- 4. Запрос: Количество запросов по часам за весь период в логах
SELECT toHour(timestamp) "Час", count(*) "Количество запросов"
FROM server_logs
GROUP BY toHour(timestamp);

-- 5. Запрос: Процент ошибок (status_code >= 400) для каждого endpoint'а
SELECT endpoint "Эндпоинт", concat(round( (countIf(status_code >= 400) / count(*) ) * 100,2),'%') "Процент ошибок"
FROM server_logs
GROUP BY endpoint;
