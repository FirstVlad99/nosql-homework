CREATE KEYSPACE posts_ks
            WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1};

use posts_ks;


CREATE TABLE users
(
    user_id  INT PRIMARY KEY ,
    username TEXT,
    email    TEXT
);

DROP TABLE IF EXISTS posts;

CREATE TABLE posts
(
    post_id    INT PRIMARY KEY,
    user_id    INT,
    topic_id   INT,
    content    TEXT,
    created_at TIMESTAMP
);

CREATE TABLE post_to_user (
    post_id    INT,
    user_id    INT,
    username   TEXT,
    content    TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at desc);
//DROP TABLE IF EXISTS post_to_user;

INSERT INTO post_to_user (user_id, created_at, content, post_id, username)
VALUES (1,toTimestamp(now()),'123',22,'Vlad');

INSERT INTO post_to_user (user_id, created_at, content, post_id, username)
VALUES (1,'2025-06-01 00:00:00','123',12,'Vlad');

INSERT INTO post_to_user (user_id, created_at, content, post_id, username)
VALUES (1,'2025-06-01 00:00:00','456',14,'Vlad');

CREATE TABLE post_to_user_with_topic (
    post_id    INT,
    user_id    INT,
    username   TEXT,
    topic_id   INT,
    content    TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (topic_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at desc);
//DROP TABLE IF EXISTS post_to_user_with_topic;

INSERT INTO posts (post_id, user_id, topic_id, content, created_at)
VALUES (1, 1, 1, 'Первый пост', toTimestamp(now()));

INSERT INTO post_to_user_with_topic (user_id, created_at, content, post_id, username, topic_id)
VALUES (1,toTimestamp(now()),'123',22,'Vlad',1);

INSERT INTO post_to_user_with_topic (user_id, created_at, content, post_id, username,topic_id)
VALUES (1,'2025-06-01 00:00:00','123',12,'Vlad',2);

INSERT INTO posts_ks.post_to_user_with_topic (user_id, created_at, content, post_id, username,topic_id)
VALUES (1,'2025-06-01 00:00:00','456',14,'Vlad',20);

/*
 Запрос, который бы доставал посты заданного пользователя,
 упорядоченные по времени создания поста (сначала новые).
 Должен возвращать: post_id, content, дату создания поста.
 */
SELECT post_id, content, created_at
FROM post_to_user
WHERE user_id = 1;

/*
 Запрос, который бы доставал последний пост заданного пользователя.
 Должен возвращать: post_id, content, дату создания поста, username.
 */
SELECT post_id, content, created_at, username
FROM post_to_user
WHERE user_id = 1
LIMIT 1;

/*
 Запрос, который бы доставал посты заданного пользователя после заданной даты.
 Должен возвращать: post_id, content, дату создания поста.
 */
SELECT post_id, content, created_at
FROM post_to_user
WHERE user_id = 1 AND created_at > '2025-09-01 00:00:00';

/*
 Запрос, который бы доставал пользователей, сделавших пост в заданный день по заданной теме,
 упорядоченные по времени (сначала новые).
 Должен возвращать: user_id, username, post_id, время создания поста.
 */
 SELECT user_id, username, post_id, created_at
 FROM post_to_user_with_topic
 WHERE topic_id = 2 AND created_at = '2025-06-01 00:00:00' AND created_at = '2025-06-02 00:00:00';
