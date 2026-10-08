package com.erp.module.workbench.service.weather;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** 内置城市（手选城市的快捷列表，自动定位时取最近的城市显示名称）：广东全部地级市 + 全国主要城市 */
public final class WeatherCities {

    private WeatherCities() {
    }

    public record City(String name, String province, BigDecimal latitude, BigDecimal longitude) {
        City(String name, String province, String lat, String lon) {
            this(name, province, new BigDecimal(lat), new BigDecimal(lon));
        }
    }

    /** 默认城市：东莞 */
    public static final City DEFAULT = new City("东莞", "广东", "23.0207", "113.7518");

    public static final List<City> ALL = List.of(
            DEFAULT,
            new City("深圳", "广东", "22.5431", "114.0579"), new City("广州", "广东", "23.1291", "113.2644"),
            new City("惠州", "广东", "23.1115", "114.4152"), new City("佛山", "广东", "23.0215", "113.1214"),
            new City("中山", "广东", "22.5176", "113.3926"), new City("珠海", "广东", "22.2710", "113.5767"),
            new City("江门", "广东", "22.5787", "113.0819"), new City("肇庆", "广东", "23.0469", "112.4651"),
            new City("清远", "广东", "23.6820", "113.0560"), new City("韶关", "广东", "24.8104", "113.5972"),
            new City("河源", "广东", "23.7463", "114.7004"), new City("梅州", "广东", "24.2886", "116.1226"),
            new City("汕头", "广东", "23.3535", "116.6822"), new City("潮州", "广东", "23.6567", "116.6226"),
            new City("揭阳", "广东", "23.5497", "116.3728"), new City("汕尾", "广东", "22.7862", "115.3751"),
            new City("阳江", "广东", "21.8579", "111.9822"), new City("茂名", "广东", "21.6630", "110.9254"),
            new City("湛江", "广东", "21.2707", "110.3594"), new City("云浮", "广东", "22.9152", "112.0444"),
            new City("香港", "香港", "22.3193", "114.1694"), new City("澳门", "澳门", "22.1987", "113.5439"),
            new City("北京", "北京", "39.9042", "116.4074"), new City("上海", "上海", "31.2304", "121.4737"),
            new City("天津", "天津", "39.0842", "117.2009"), new City("重庆", "重庆", "29.5630", "106.5516"),
            new City("杭州", "浙江", "30.2741", "120.1551"), new City("宁波", "浙江", "29.8683", "121.5440"),
            new City("苏州", "江苏", "31.2989", "120.5853"), new City("南京", "江苏", "32.0603", "118.7969"),
            new City("无锡", "江苏", "31.4912", "120.3119"), new City("厦门", "福建", "24.4798", "118.0894"),
            new City("福州", "福建", "26.0745", "119.2965"), new City("泉州", "福建", "24.8741", "118.6757"),
            new City("武汉", "湖北", "30.5928", "114.3055"), new City("长沙", "湖南", "28.2282", "112.9388"),
            new City("成都", "四川", "30.5728", "104.0668"), new City("西安", "陕西", "34.3416", "108.9398"),
            new City("郑州", "河南", "34.7466", "113.6253"), new City("合肥", "安徽", "31.8206", "117.2272"),
            new City("南昌", "江西", "28.6820", "115.8579"), new City("南宁", "广西", "22.8170", "108.3665"),
            new City("海口", "海南", "20.0440", "110.1999"), new City("昆明", "云南", "25.0389", "102.7183"),
            new City("贵阳", "贵州", "26.6470", "106.6302"), new City("青岛", "山东", "36.0671", "120.3826"),
            new City("济南", "山东", "36.6512", "117.1201"), new City("沈阳", "辽宁", "41.8057", "123.4315"),
            new City("大连", "辽宁", "38.9140", "121.6147"), new City("哈尔滨", "黑龙江", "45.8038", "126.5349"),
            new City("长春", "吉林", "43.8171", "125.3235"), new City("石家庄", "河北", "38.0428", "114.5149"),
            new City("太原", "山西", "37.8706", "112.5489"), new City("兰州", "甘肃", "36.0611", "103.8343"),
            new City("乌鲁木齐", "新疆", "43.8256", "87.6168"), new City("呼和浩特", "内蒙古", "40.8424", "111.7490"),
            new City("银川", "宁夏", "38.4872", "106.2309"), new City("西宁", "青海", "36.6171", "101.7782"),
            new City("拉萨", "西藏", "29.6520", "91.1721"), new City("台北", "台湾", "25.0330", "121.5654"));

    /** 最近的内置城市（自动定位时显示名称） */
    public static City nearest(BigDecimal lat, BigDecimal lon) {
        double la = lat.doubleValue();
        double lo = lon.doubleValue();
        return ALL.stream().min(Comparator.comparingDouble(c -> distanceKm(la, lo, c.latitude().doubleValue(), c.longitude().doubleValue())))
                .orElse(DEFAULT);
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
