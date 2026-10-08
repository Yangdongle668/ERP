package com.erp.module.workbench.service.weather;

import com.erp.common.exception.BizException;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.workbench.api.WorkbenchErrorCodes;
import com.erp.module.workbench.dal.dataobject.WbWeatherPrefDO;
import com.erp.module.workbench.dal.mapper.WbWeatherPrefMapper;
import com.erp.module.workbench.service.WbSupport;
import org.springframework.transaction.annotation.Transactional;
import com.erp.module.workbench.config.WorkbenchModuleConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 顶部天气（需求 02-01）：Open-Meteo 免费接口，无需密钥。位置跟随个人账号：自动定位（浏览器定位传入经纬度）或手选城市，默认东莞。
 * 按位置（经纬度保留 2 位）缓存 30 分钟；获取失败时返回上次成功的结果（stale=true），失败后 5 分钟内不再重试，避免拖慢页面。
 */
@Slf4j
@Service
public class WeatherService {

    static final Duration CACHE = Duration.ofMinutes(30);
    static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(5);
    static final int MAX_CACHED_LOCATIONS = 500;
    public static final String AUTO = "AUTO";
    public static final String MANUAL = "MANUAL";

    public record Day(String date, int code, String text, String icon, BigDecimal min, BigDecimal max, Integer rainProbability) {
    }

    /**
     * available=false 时 current / days 为空；stale=true 表示获取失败、显示的是上次成功的数据；
     * located=true 表示按浏览器定位的经纬度查询（city 为最近的城市）
     */
    public record Weather(boolean enabled, boolean available, boolean stale, boolean located, String city, BigDecimal temperature, Integer humidity,
                          BigDecimal windSpeed, int code, String text, String icon, List<Day> days, LocalDateTime updatedAt) {
        static Weather disabled(String city) {
            return new Weather(false, false, false, false, city, null, null, null, 0, null, null, List.of(), null);
        }

        static Weather unavailable(String city, boolean located) {
            return new Weather(true, false, false, located, city, null, null, null, 0, null, null, List.of(), null);
        }

        Weather at(String city, boolean located, boolean stale) {
            return new Weather(enabled, available, stale, located, city, temperature, humidity, windSpeed, code, text, icon, days, updatedAt);
        }
    }

    /** 用户天气设置：mode = AUTO 自动定位 / MANUAL 手选城市；city 为手选城市（自动定位失败时也用它） */
    public record Pref(String mode, String cityName, BigDecimal latitude, BigDecimal longitude) {
    }

    private record Cached(Weather weather, Instant at, Instant failedAt) {
    }

    private final ParamApi paramApi;
    private final ObjectMapper objectMapper;
    private final WbWeatherPrefMapper prefMapper;
    private final WbSupport support;
    private final String baseUrl;
    private final String geoUrl;
    private final HttpClient client;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public WeatherService(ParamApi paramApi, ObjectMapper objectMapper, WbWeatherPrefMapper prefMapper, WbSupport support,
                          @Value("${erp.weather.url:https://api.open-meteo.com/v1/forecast}") String baseUrl,
                          @Value("${erp.weather.geocoding-url:https://geocoding-api.open-meteo.com/v1/search}") String geoUrl) {
        this.paramApi = paramApi;
        this.objectMapper = objectMapper;
        this.prefMapper = prefMapper;
        this.support = support;
        this.baseUrl = baseUrl;
        this.geoUrl = geoUrl;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.clock = Clock.systemDefaultZone();
    }

    // ==================== 个人设置 ====================

    public Pref pref() {
        WbWeatherPrefDO p = support.currentUser() == null ? null : prefMapper.selectByUser(support.currentUser());
        if (p == null) return new Pref(MANUAL, WeatherCities.DEFAULT.name(), WeatherCities.DEFAULT.latitude(), WeatherCities.DEFAULT.longitude());
        return new Pref(p.getMode(), p.getCityName(), p.getLatitude(), p.getLongitude());
    }

    @Transactional(rollbackFor = Exception.class)
    public void savePref(Pref req) {
        String mode = AUTO.equals(req.mode()) ? AUTO : MANUAL;
        String name = req.cityName() == null ? "" : req.cityName().trim();
        if (name.isEmpty() || name.length() > 64) throw BizException.of(WorkbenchErrorCodes.WEATHER_CITY_INVALID, "请选择城市");
        checkLocation(req.latitude(), req.longitude());
        Long me = support.currentUser();
        WbWeatherPrefDO p = prefMapper.selectByUser(me);
        boolean creating = p == null;
        if (creating) {
            p = new WbWeatherPrefDO();
            p.setUserId(me);
        }
        p.setMode(mode);
        p.setCityName(name);
        p.setLatitude(req.latitude().setScale(4, RoundingMode.HALF_UP));
        p.setLongitude(req.longitude().setScale(4, RoundingMode.HALF_UP));
        if (creating) prefMapper.insert(p);
        else prefMapper.updateByIdOrFail(p);
    }

    private static void checkLocation(BigDecimal lat, BigDecimal lon) {
        if (lat == null || lon == null || lat.abs().compareTo(new BigDecimal("90")) > 0 || lon.abs().compareTo(new BigDecimal("180")) > 0) {
            throw BizException.of(WorkbenchErrorCodes.WEATHER_CITY_INVALID, "经纬度不正确");
        }
    }

    /** 城市列表：内置城市按名称 / 省份筛选；输入关键字时再用 Open-Meteo 地名搜索补充（取不到时只返回内置城市） */
    public List<WeatherCities.City> cities(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        List<WeatherCities.City> result = new ArrayList<>(WeatherCities.ALL.stream()
                .filter(c -> k.isEmpty() || c.name().contains(k) || c.province().contains(k)).toList());
        if (k.isEmpty() || result.size() >= 10) return result;
        try {
            String url = geoUrl + "?name=" + java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8) + "&count=10&language=zh&format=json";
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                for (JsonNode r : objectMapper.readTree(resp.body()).path("results")) {
                    String name = r.path("name").asText();
                    String admin = r.path("admin1").asText(r.path("country").asText(""));
                    BigDecimal lat = r.path("latitude").decimalValue().setScale(4, RoundingMode.HALF_UP);
                    BigDecimal lon = r.path("longitude").decimalValue().setScale(4, RoundingMode.HALF_UP);
                    boolean dup = result.stream().anyMatch(c -> c.name().equals(name) && WeatherCities.distanceKm(c.latitude().doubleValue(),
                            c.longitude().doubleValue(), lat.doubleValue(), lon.doubleValue()) < 20);
                    if (!dup && !name.isEmpty()) result.add(new WeatherCities.City(name, admin, lat, lon));
                }
            }
        } catch (Exception e) {
            log.debug("[天气] 地名搜索失败：{}", e.toString());
        }
        return result;
    }

    // ==================== 天气 ====================

    /**
     * 天气：传入经纬度（自动定位）时按该位置查询，城市名取最近的内置城市；否则按个人设置的城市（默认东莞）。
     */
    public Weather current(BigDecimal lat, BigDecimal lon) {
        boolean located = lat != null && lon != null;
        String city;
        if (located) {
            checkLocation(lat, lon);
            WeatherCities.City near = WeatherCities.nearest(lat, lon);
            double km = WeatherCities.distanceKm(lat.doubleValue(), lon.doubleValue(), near.latitude().doubleValue(), near.longitude().doubleValue());
            city = km <= 60 ? near.name() : "当前位置";
        } else {
            Pref p = pref();
            city = p.cityName();
            lat = p.latitude();
            lon = p.longitude();
        }
        if (!paramApi.getBool(WorkbenchModuleConfig.P_WEATHER_ENABLED)) return Weather.disabled(city);
        BigDecimal la = lat.setScale(2, RoundingMode.HALF_UP);
        BigDecimal lo = lon.setScale(2, RoundingMode.HALF_UP);
        String key = la.toPlainString() + "," + lo.toPlainString();
        Instant now = clock.instant();
        Cached c = cache.get(key);
        if (c != null && c.weather() != null && now.isBefore(c.at().plus(CACHE))) return c.weather().at(city, located, false);
        if (c != null && c.failedAt() != null && now.isBefore(c.failedAt().plus(RETRY_AFTER_FAILURE))) {
            return c.weather() != null ? c.weather().at(city, located, true) : Weather.unavailable(city, located);
        }
        try {
            Weather w = parse(fetch(la, lo), city);
            if (cache.size() >= MAX_CACHED_LOCATIONS) cache.clear();
            cache.put(key, new Cached(w, clock.instant(), null));
            return w.at(city, located, false);
        } catch (Exception e) {
            log.warn("[天气] 获取失败 {}：{}", key, e.toString());
            Weather last = c == null ? null : c.weather();
            cache.put(key, new Cached(last, c == null ? Instant.EPOCH : c.at(), clock.instant()));
            return last != null ? last.at(city, located, true) : Weather.unavailable(city, located);
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
        return new Weather(true, true, false, false, city, dec(cur.path("temperature_2m")),
                cur.path("relative_humidity_2m").isNumber() ? cur.path("relative_humidity_2m").asInt() : null, dec(cur.path("wind_speed_10m")),
                code, text(code), icon(code), days, LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()));
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
