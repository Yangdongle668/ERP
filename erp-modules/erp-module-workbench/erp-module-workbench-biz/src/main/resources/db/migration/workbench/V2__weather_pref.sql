-- 顶部天气（需求 02-01）：每个用户的天气位置设置。AUTO 自动定位（浏览器定位，失败时用下面的城市）/ MANUAL 手选城市；没有记录时默认东莞
CREATE TABLE wb_weather_pref (
    id          BIGINT        NOT NULL PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    mode        VARCHAR(8)    NOT NULL DEFAULT 'MANUAL' COMMENT 'AUTO 自动定位 / MANUAL 手选城市',
    city_name   VARCHAR(64)   NOT NULL,
    latitude    DECIMAL(9, 4) NOT NULL,
    longitude   DECIMAL(9, 4) NOT NULL,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_wb_weather_pref_user UNIQUE (user_id)
) COMMENT '用户天气设置';
