package com.erp.module.workbench.service.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 天气：Open-Meteo 响应解析、WMO 代码映射 */
class WeatherServiceTest {

    static final String SAMPLE = """
            {"current":{"time":"2026-10-08T10:00","temperature_2m":27.6,"relative_humidity_2m":78,"weather_code":2,"wind_speed_10m":11.3},
             "daily":{"time":["2026-10-08","2026-10-09","2026-10-10"],"weather_code":[2,95,61],
                      "temperature_2m_max":[30.4,29.0,27.2],"temperature_2m_min":[24.1,23.6,22.5],"precipitation_probability_max":[20,80,65]}}
            """;

    @Test
    void parseOpenMeteo() throws Exception {
        WeatherService s = new WeatherService(null, new ObjectMapper(), "http://localhost");
        WeatherService.Weather w = s.parse(SAMPLE, "深圳宝安");
        assertThat(w.available()).isTrue();
        assertThat(w.city()).isEqualTo("深圳宝安");
        assertThat(w.temperature()).isEqualByComparingTo("28");
        assertThat(w.humidity()).isEqualTo(78);
        assertThat(w.text()).isEqualTo("多云");
        assertThat(w.icon()).isEqualTo("WeatherSunCloud");
        assertThat(w.days()).hasSize(3);
        assertThat(w.days().get(1).text()).isEqualTo("雷阵雨");
        assertThat(w.days().get(1).icon()).isEqualTo("WeatherStorm");
        assertThat(w.days().get(1).max()).isEqualByComparingTo("29");
        assertThat(w.days().get(1).min()).isEqualByComparingTo("24");
        assertThat(w.days().get(2).rainProbability()).isEqualTo(65);
    }

    @Test
    void wmoCodes() {
        assertThat(WeatherService.text(0)).isEqualTo("晴");
        assertThat(WeatherService.text(65)).isEqualTo("大雨");
        assertThat(WeatherService.icon(45)).isEqualTo("WeatherFog");
        assertThat(WeatherService.icon(73)).isEqualTo("WeatherSnow");
        assertThat(WeatherService.icon(53)).isEqualTo("WeatherDrizzle");
    }
}
