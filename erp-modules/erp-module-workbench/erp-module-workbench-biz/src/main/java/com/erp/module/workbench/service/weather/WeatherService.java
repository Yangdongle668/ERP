package com.erp.module.workbench.service.weather;

import com.erp.module.system.api.param.ParamApi;
import com.erp.module.workbench.config.WorkbenchModuleConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页天气预报（需求 02-工作台 首页）：Open-Meteo 免费接口，无需密钥。
 * 结果缓存 30 分钟；获取失败时返回上次成功的结果（stale=true），失败后 5 分钟内不再重试，避免拖慢首页。
 */
@Slf4j
@Service
public class WeatherService {

    static final Duration CACHE = Duration.ofMinutes(30);
    static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(5);

    public record Day(String date, int code, String text, String icon, BigDecimal min, BigDecimal max, Integer rainProbability) {
    }

    /** available=false 时 current / days 为空；stale=true 表示获取失败、显示的是上次成功的数据 */
    public record Weather(boolean enabled, boolean available, boolean stale, String city, BigDecimal temperature, Integer humidity,
                          BigDecimal windSpeed, int code, String text, String icon, List<Day> days, LocalDateTime updatedAt) {
        static Weather disabled(String city) {
            return new Weather(false, false, false, city, null, null, null, 0, null, null, List.of(), null);
        }

        static Weather unavailable(String city) {
            return new Weather(true, false, false, city, null, null, null, 0, null, null, List.of(), null);
        }
    }

    private final ParamApi paramApi;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final HttpClient client;
    private final Clock clock;

    private volatile Weather cached;
    private volatile String cachedKey;
    private volatile Instant cachedAt = Instant.EPOCH;
    private volatile Instant failedAt = Instant.EPOCH;

    public WeatherService(ParamApi paramApi, ObjectMapper objectMapper,
                          @Value("${erp.weather.url:https://api.open-meteo.com/v1/forecast}") String baseUrl) {
        this.paramApi = paramApi;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.clock = Clock.systemDefaultZone();
    }

    public Weather current() {
        String city = paramApi.getString(WorkbenchModuleConfig.P_WEATHER_CITY);
        if (!paramApi.getBool(WorkbenchModuleConfig.P_WEATHER_ENABLED)) return Weather.disabled(city);
        BigDecimal lat = paramApi.getDecimal(WorkbenchModuleConfig.P_WEATHER_LAT);
        BigDecimal lon = paramApi.getDecimal(WorkbenchModuleConfig.P_WEATHER_LON);
        String key = lat.toPlainString() + "," + lon.toPlainString();
        Instant now = clock.instant();
        Weather c = cached;
        boolean sameKey = key.equals(cachedKey);
        if (c != null && sameKey && now.isBefore(cachedAt.plus(CACHE))) return withCity(c, city, false);
        if (now.isBefore(failedAt.plus(RETRY_AFTER_FAILURE))) return c != null && sameKey ? withCity(c, city, true) : Weather.unavailable(city);
        synchronized (this) {
            if (cached != null && key.equals(cachedKey) && clock.instant().isBefore(cachedAt.plus(CACHE))) return withCity(cached, city, false);
            try {
                Weather w = parse(fetch(lat, lon), city);
                cached = w;
                cachedKey = key;
                cachedAt = clock.instant();
                return w;
            } catch (Exception e) {
                failedAt = clock.instant();
                log.warn("[天气] 获取失败：{}", e.toString());
                return c != null && sameKey ? withCity(c, city, true) : Weather.unavailable(city);
            }
        }
    }

    private String fetch(BigDecimal lat, BigDecimal lon) throws Exception {
        String url = baseUrl + "?latitude=" + lat.toPlainString() + "&longitude=" + lon.toPlainString()
                + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=Asia%2FShanghai&forecast_days=3";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
        return resp.body();
    }

    /** 解析 Open-Meteo 响应（单元测试直接调用） */
    Weather parse(String json, String city) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode cur = root.path("current");
        if (cur.isMissingNode()) throw new IllegalStateException("响应缺少 current");
        int code = cur.path("weather_code").asInt();
        JsonNode daily = root.path("daily");
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < daily.path("time").size(); i++) {
            int dc = daily.path("weather_code").path(i).asInt();
            JsonNode rain = daily.path("precipitation_probability_max").path(i);
            days.add(new Day(daily.path("time").path(i).asText(), dc, text(dc), icon(dc), dec(daily.path("temperature_2m_min").path(i)),
                    dec(daily.path("temperature_2m_max").path(i)), rain.isNumber() ? rain.asInt() : null));
        }
        return new Weather(true, true, false, city, dec(cur.path("temperature_2m")),
                cur.path("relative_humidity_2m").isNumber() ? cur.path("relative_humidity_2m").asInt() : null, dec(cur.path("wind_speed_10m")),
                code, text(code), icon(code), days, LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()));
    }

    private static Weather withCity(Weather w, String city, boolean stale) {
        return new Weather(w.enabled(), w.available(), stale, city, w.temperature(), w.humidity(), w.windSpeed(), w.code(), w.text(), w.icon(),
                w.days(), w.updatedAt());
    }

    private static BigDecimal dec(JsonNode n) {
        return n.isNumber() ? n.decimalValue().setScale(0, java.math.RoundingMode.HALF_UP) : null;
    }

    /** WMO 天气代码 → 中文 */
    static String text(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "晴间多云";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 80 -> "小雨";
            case 63, 81 -> "中雨";
            case 65, 82 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71, 77, 85 -> "小雪";
            case 73 -> "中雪";
            case 75, 86 -> "大雪";
            case 95 -> "雷阵雨";
            case 96, 99 -> "雷阵雨伴冰雹";
            default -> "未知";
        };
    }

    /** WMO 天气代码 → 前端图标名（components/icons.ts） */
    static String icon(int code) {
        if (code == 0) return "WeatherSun";
        if (code <= 2) return "WeatherSunCloud";
        if (code == 3) return "WeatherCloud";
        if (code == 45 || code == 48) return "WeatherFog";
        if (code >= 51 && code <= 57) return "WeatherDrizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "WeatherRain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "WeatherSnow";
        if (code >= 95) return "WeatherStorm";
        return "WeatherCloud";
    }

}
