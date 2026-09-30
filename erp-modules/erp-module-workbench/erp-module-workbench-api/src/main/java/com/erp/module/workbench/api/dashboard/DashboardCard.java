package com.erp.module.workbench.api.dashboard;

import java.util.function.Supplier;

/**
 * 首页看板卡片（需求 02-工作台/01-首页 第 3 节）。各业务模块在自己的 biz 中实现并注册为 Spring Bean，只统计本模块的表；
 * 工作台按当前用户权限过滤、并行加载、按用户缓存（参数 wb.dashboard.refresh-minutes）。
 *
 * <p>{@link #load()} 在当前用户的请求上下文中执行，Mapper 上的数据权限（{@code @DataScope}）自动生效（WB-HOME-R01）。
 */
public interface DashboardCard {

    /** 卡片编码，全局唯一，建议以模块前缀开头，如 SAL_ORDER_MONTH */
    String code();

    String name();

    /** 所需权限；多个用 | 分隔表示拥有任一即可 */
    String permission();

    /** 默认顺序（越小越靠前） */
    int sort();

    /** METRIC 指标卡 / CHART 折线图 */
    default String type() {
        return "METRIC";
    }

    /** 点击下钻的前端路由 */
    default String route() {
        return null;
    }

    CardData load();

    /** 指标卡 */
    static DashboardCard of(String code, String name, String permission, int sort, String route, Supplier<CardData> loader) {
        return simple(code, name, permission, sort, "METRIC", route, loader);
    }

    /** 折线图卡 */
    static DashboardCard chart(String code, String name, String permission, int sort, String route, Supplier<CardData> loader) {
        return simple(code, name, permission, sort, "CHART", route, loader);
    }

    private static DashboardCard simple(String code, String name, String permission, int sort, String type, String route, Supplier<CardData> loader) {
        return new DashboardCard() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String permission() {
                return permission;
            }

            @Override
            public int sort() {
                return sort;
            }

            @Override
            public String type() {
                return type;
            }

            @Override
            public String route() {
                return route;
            }

            @Override
            public CardData load() {
                return loader.get();
            }
        };
    }
}
