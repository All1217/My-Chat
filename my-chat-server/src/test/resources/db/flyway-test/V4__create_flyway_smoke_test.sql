-- Flyway 冒烟迁移：仅在显式加入 classpath:db/flyway-test 时执行。

CREATE TABLE flyway_smoke_test
(
    id         INTEGER PRIMARY KEY,
    marker     VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO flyway_smoke_test (id, marker)
VALUES (1, 'flyway-v4-applied');
