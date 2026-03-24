-- Схема таблицы для ClickHouse
-- Создайте таблицу для хранения логов веб-сервера

-- Пример структуры (необходимо дополнить):
--CREATE DATABASE IF NOT EXISTS hh_logs;

CREATE TABLE IF NOT EXISTS server_logs
(
    u
    -- TODO: определите поля таблицы на основе файла server_logs.csv
    -- Используйте подходящие типы данных ClickHouse
    -- Выберите подходящий движок (например, MergeTree)
    -- Укажите ORDER BY для оптимизации запросов
) ENGINE = MergeTree()
ORDER BY (); -- TODO: выберите подходящий порядок сортировки
