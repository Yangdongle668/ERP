package com.erp.module.engineering.api.tooling;

/**
 * 工装。usable 为可用于生产报工（未报废、非维修中、未超寿命或允许超寿命使用）。
 *
 * @param toolingStatus IN_STOCK/IN_USE/LENT/REPAIRING/SCRAPPED
 */
public record ToolingDTO(Long id, String code, String name, String toolingType, int cavity, Integer designLife, int usedCount,
                         String toolingStatus, boolean usable) {
}
