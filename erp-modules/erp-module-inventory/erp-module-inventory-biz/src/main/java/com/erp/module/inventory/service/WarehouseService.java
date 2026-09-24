package com.erp.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.controller.vo.WarehouseVOs.CategoryWarehouseRow;
import com.erp.module.inventory.controller.vo.WarehouseVOs.CategoryWarehouseSave;
import com.erp.module.inventory.controller.vo.WarehouseVOs.GenerateResult;
import com.erp.module.inventory.controller.vo.WarehouseVOs.LocationGenerate;
import com.erp.module.inventory.controller.vo.WarehouseVOs.LocationRow;
import com.erp.module.inventory.controller.vo.WarehouseVOs.LocationSave;
import com.erp.module.inventory.controller.vo.WarehouseVOs.WarehouseRow;
import com.erp.module.inventory.controller.vo.WarehouseVOs.WarehouseSave;
import com.erp.module.inventory.controller.vo.WarehouseVOs.WarehouseSimple;
import com.erp.module.inventory.dal.dataobject.CategoryWarehouseDO;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockInDO;
import com.erp.module.inventory.dal.dataobject.StockOutDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.TransferDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.dataobject.WarehouseUserRow;
import com.erp.module.inventory.dal.mapper.CategoryWarehouseMapper;
import com.erp.module.inventory.dal.mapper.LocationMapper;
import com.erp.module.inventory.dal.mapper.StockInMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockOutMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.dal.mapper.TransferMapper;
import com.erp.module.inventory.dal.mapper.WarehouseMapper;
import com.erp.module.inventory.dal.mapper.WarehouseUserMapper;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仓库与库位（需求 08-01），同时是 {@link WarehouseApi} 的实现。
 *
 * <p>仓库数据权限（INV-WH-R09）：拥有“全部”数据范围的用户看到全部仓库；其他用户只能看到自己是操作人员或主管的仓库。
 */
@Service
public class WarehouseService implements WarehouseApi {

    static final int MAX_GENERATE = 2000;
    private static final List<DocStatus> OPEN_STATUSES = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED);

    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;
    private final WarehouseUserMapper warehouseUserMapper;
    private final CategoryWarehouseMapper categoryWarehouseMapper;
    private final StockMapper stockMapper;
    private final StockTxnMapper txnMapper;
    private final StockInMapper stockInMapper;
    private final StockOutMapper stockOutMapper;
    private final TransferMapper transferMapper;
    private final UserApi userApi;
    private final MaterialApi materialApi;
    private final MaterialCategoryApi categoryApi;

    public WarehouseService(WarehouseMapper warehouseMapper, LocationMapper locationMapper, WarehouseUserMapper warehouseUserMapper,
                            CategoryWarehouseMapper categoryWarehouseMapper, StockMapper stockMapper, StockTxnMapper txnMapper,
                            StockInMapper stockInMapper, StockOutMapper stockOutMapper, TransferMapper transferMapper, UserApi userApi,
                            MaterialApi materialApi, MaterialCategoryApi categoryApi) {
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.warehouseUserMapper = warehouseUserMapper;
        this.categoryWarehouseMapper = categoryWarehouseMapper;
        this.stockMapper = stockMapper;
        this.txnMapper = txnMapper;
        this.stockInMapper = stockInMapper;
        this.stockOutMapper = stockOutMapper;
        this.transferMapper = transferMapper;
        this.userApi = userApi;
        this.materialApi = materialApi;
        this.categoryApi = categoryApi;
    }

    // ==================== 数据权限 ====================

    /** 当前用户可操作的仓库 ID；null 表示全部（全部数据范围、超级管理员、后台任务） */
    public Set<Long> accessibleIds() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null || SecurityUtils.currentDataScope().all()) return null;
        Set<Long> ids = new HashSet<>(warehouseUserMapper.selectWarehouseIds(user.id()));
        warehouseMapper.selectList(new LambdaQueryWrapper<WarehouseDO>().select(WarehouseDO::getId).eq(WarehouseDO::getManagerId, user.id()))
                .forEach(w -> ids.add(w.getId()));
        return ids;
    }

    /** 校验当前用户有该仓库的操作权限 */
    public void checkAccess(Long warehouseId) {
        Set<Long> ids = accessibleIds();
        if (ids != null && !ids.contains(warehouseId)) {
            WarehouseDO w = warehouseMapper.selectById(warehouseId);
            throw BizException.of(InventoryErrorCodes.WAREHOUSE_NO_PERMISSION, w == null ? String.valueOf(warehouseId) : w.getName());
        }
    }

    // ==================== 仓库 ====================

    public List<WarehouseRow> list(String keyword, WarehouseType type, String status) {
        String k = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<WarehouseDO> list = warehouseMapper.selectList(new LambdaQueryWrapper<WarehouseDO>()
                .eq(type != null, WarehouseDO::getWarehouseType, type)
                .eq(StringUtils.hasText(status), WarehouseDO::getStatus, StringUtils.hasText(status) ? EnableStatus.valueOf(status) : null)
                .and(k != null, x -> x.like(WarehouseDO::getCode, k).or().like(WarehouseDO::getName, k))
                .orderByAsc(WarehouseDO::getWarehouseType).orderByAsc(WarehouseDO::getCode));
        Map<Long, List<Long>> users = new HashMap<>();
        for (WarehouseUserRow r : warehouseUserMapper.selectAll()) users.computeIfAbsent(r.getWarehouseId(), x -> new ArrayList<>()).add(r.getUserId());
        Set<Long> userIds = new HashSet<>();
        users.values().forEach(userIds::addAll);
        list.forEach(w -> {
            if (w.getManagerId() != null) userIds.add(w.getManagerId());
        });
        Map<Long, UserDTO> names = userIds.isEmpty() ? Map.of() : userApi.list(userIds);
        Map<Long, Long> locCounts = locationMapper.selectList(new LambdaQueryWrapper<LocationDO>().select(LocationDO::getWarehouseId))
                .stream().collect(Collectors.groupingBy(LocationDO::getWarehouseId, Collectors.counting()));
        return list.stream().map(w -> {
            List<Long> uids = users.getOrDefault(w.getId(), List.of());
            return new WarehouseRow(w.getId(), w.getCode(), w.getName(), w.getWarehouseType(), w.getWarehouseType().available(),
                    Boolean.TRUE.equals(w.getIsDefault()), Boolean.TRUE.equals(w.getLocationEnabled()), Boolean.TRUE.equals(w.getAllowNegative()),
                    w.getManagerId(), name(names, w.getManagerId()), w.getAddress(), uids,
                    uids.stream().map(u -> name(names, u)).filter(Objects::nonNull).toList(),
                    locCounts.getOrDefault(w.getId(), 0L).intValue(), w.getStatus().name(), w.getRemark(), w.getVersion());
        }).toList();
    }

    /** WarehouseSelect：启用仓库；onlyMine 时按仓库数据权限过滤 */
    public List<WarehouseSimple> simple(String types, boolean onlyMine) {
        Set<WarehouseType> ts = StringUtils.hasText(types)
                ? java.util.Arrays.stream(types.split(",")).map(String::trim).filter(StringUtils::hasText).map(WarehouseType::valueOf).collect(Collectors.toSet())
                : Set.of();
        Set<Long> allowed = onlyMine ? accessibleIds() : null;
        return warehouseMapper.selectList(new LambdaQueryWrapper<WarehouseDO>().eq(WarehouseDO::getStatus, EnableStatus.ENABLED)
                        .in(!ts.isEmpty(), WarehouseDO::getWarehouseType, ts).orderByAsc(WarehouseDO::getWarehouseType).orderByAsc(WarehouseDO::getCode))
                .stream().filter(w -> allowed == null || allowed.contains(w.getId()))
                .map(w -> new WarehouseSimple(w.getId(), w.getCode(), w.getName(), w.getWarehouseType(), w.getWarehouseType().available(),
                        Boolean.TRUE.equals(w.getLocationEnabled()), Boolean.TRUE.equals(w.getIsDefault())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(WarehouseSave req) {
        String code = req.code().trim().toUpperCase();
        if (warehouseMapper.selectCount(new LambdaQueryWrapper<WarehouseDO>().eq(WarehouseDO::getCode, code)) > 0) {
            throw BizException.of(InventoryErrorCodes.WAREHOUSE_CODE_DUPLICATE, code);
        }
        WarehouseDO w = new WarehouseDO();
        w.setCode(code);
        w.setWarehouseType(req.warehouseType());
        w.setStatus(EnableStatus.ENABLED);
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        w.setOrgId(user == null ? null : user.orgId());
        fill(w, req);
        w.setIsDefault(false);
        try {
            warehouseMapper.insert(w);
        } catch (DuplicateKeyException e) {
            throw BizException.of(InventoryErrorCodes.WAREHOUSE_CODE_DUPLICATE, code);
        }
        if (Boolean.TRUE.equals(req.isDefault())) makeDefault(w);
        return w.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WarehouseSave req) {
        WarehouseDO w = getOrThrow(id);
        boolean wasLocation = Boolean.TRUE.equals(w.getLocationEnabled());
        fill(w, req);
        boolean nowLocation = Boolean.TRUE.equals(w.getLocationEnabled());
        if (wasLocation && !nowLocation && hasStock(id)) throw new BizException(InventoryErrorCodes.WAREHOUSE_LOCATION_STOCK);
        if (req.version() != null) w.setVersion(req.version());
        warehouseMapper.updateByIdOrFail(w);
        if (!wasLocation && nowLocation) moveStockToDefaultLocation(w);
        if (Boolean.TRUE.equals(req.isDefault()) && !Boolean.TRUE.equals(w.getIsDefault())) makeDefault(w);
    }

    private void fill(WarehouseDO w, WarehouseSave req) {
        w.setName(req.name().trim());
        w.setManagerId(req.managerId());
        w.setAddress(StringUtils.hasText(req.address()) ? req.address().trim() : null);
        w.setLocationEnabled(Boolean.TRUE.equals(req.locationEnabled()));
        // 不可用仓不允许负库存
        w.setAllowNegative(w.getWarehouseType().available() && Boolean.TRUE.equals(req.allowNegative()));
        w.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
    }

    /** R03：同类型只有一个默认仓 */
    private void makeDefault(WarehouseDO w) {
        warehouseMapper.update(null, new LambdaUpdateWrapper<WarehouseDO>().set(WarehouseDO::getIsDefault, false)
                .eq(WarehouseDO::getWarehouseType, w.getWarehouseType()).ne(WarehouseDO::getId, w.getId()));
        warehouseMapper.update(null, new LambdaUpdateWrapper<WarehouseDO>().set(WarehouseDO::getIsDefault, true).eq(WarehouseDO::getId, w.getId()));
    }

    /** R06：开启库位管理时，已有库存全部放入自动创建的库位 DEFAULT */
    private void moveStockToDefaultLocation(WarehouseDO w) {
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getWarehouseId, w.getId()).eq(StockDO::getLocationId, 0L));
        if (stocks.isEmpty()) return;
        LocationDO loc = locationMapper.selectOne(new LambdaQueryWrapper<LocationDO>().eq(LocationDO::getWarehouseId, w.getId()).eq(LocationDO::getCode, "DEFAULT"));
        if (loc == null) {
            loc = new LocationDO();
            loc.setWarehouseId(w.getId());
            loc.setCode("DEFAULT");
            loc.setName("默认库位");
            loc.setStatus(EnableStatus.ENABLED);
            locationMapper.insert(loc);
        }
        Long locId = loc.getId();
        stockMapper.update(null, new LambdaUpdateWrapper<StockDO>().set(StockDO::getLocationId, locId)
                .eq(StockDO::getWarehouseId, w.getId()).eq(StockDO::getLocationId, 0L));
    }

    /** R04：有库存或有未确认单据时不能停用 */
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        WarehouseDO w = getOrThrow(id);
        if (hasStock(id) || hasOpenDocs(id)) throw new BizException(InventoryErrorCodes.WAREHOUSE_IN_USE);
        w.setStatus(EnableStatus.DISABLED);
        warehouseMapper.updateByIdOrFail(w);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        WarehouseDO w = getOrThrow(id);
        w.setStatus(EnableStatus.ENABLED);
        warehouseMapper.updateByIdOrFail(w);
    }

    /** R05：从未发生过出入库的仓库才能删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOrThrow(id);
        if (txnMapper.selectCount(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getWarehouseId, id)) > 0 || hasOpenDocs(id)) {
            throw new BizException(InventoryErrorCodes.WAREHOUSE_HAS_TXN);
        }
        warehouseMapper.deleteById(id);
        warehouseUserMapper.deleteByWarehouse(id);
        locationMapper.delete(new LambdaQueryWrapper<LocationDO>().eq(LocationDO::getWarehouseId, id));
        categoryWarehouseMapper.delete(new LambdaQueryWrapper<CategoryWarehouseDO>().eq(CategoryWarehouseDO::getWarehouseId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setUsers(Long id, List<Long> userIds) {
        getOrThrow(id);
        warehouseUserMapper.deleteByWarehouse(id);
        for (Long u : new LinkedHashSet<>(userIds == null ? List.<Long>of() : userIds)) warehouseUserMapper.insert(id, u);
    }

    boolean hasStock(Long warehouseId) {
        return stockMapper.selectCount(new LambdaQueryWrapper<StockDO>().eq(StockDO::getWarehouseId, warehouseId).ne(StockDO::getQty, 0)) > 0;
    }

    private boolean hasOpenDocs(Long warehouseId) {
        return stockInMapper.selectCount(new LambdaQueryWrapper<StockInDO>().eq(StockInDO::getWarehouseId, warehouseId).in(StockInDO::getStatus, OPEN_STATUSES)) > 0
                || stockOutMapper.selectCount(new LambdaQueryWrapper<StockOutDO>().eq(StockOutDO::getWarehouseId, warehouseId).in(StockOutDO::getStatus, OPEN_STATUSES)) > 0
                || transferMapper.selectCount(new LambdaQueryWrapper<TransferDO>().in(TransferDO::getStatus, OPEN_STATUSES)
                .and(x -> x.eq(TransferDO::getFromWarehouseId, warehouseId).or().eq(TransferDO::getToWarehouseId, warehouseId))) > 0;
    }

    public WarehouseDO getOrThrow(Long id) {
        WarehouseDO w = id == null ? null : warehouseMapper.selectById(id);
        if (w == null) throw new BizException(InventoryErrorCodes.WAREHOUSE_NOT_EXISTS);
        return w;
    }

    public Map<Long, WarehouseDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        return warehouseMapper.selectBatchIds(set).stream().collect(Collectors.toMap(WarehouseDO::getId, w -> w));
    }

    // ==================== 库位 ====================

    public List<LocationRow> locations(Long warehouseId) {
        getOrThrow(warehouseId);
        Set<Long> withStock = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().select(StockDO::getLocationId)
                        .eq(StockDO::getWarehouseId, warehouseId).ne(StockDO::getQty, 0))
                .stream().map(StockDO::getLocationId).collect(Collectors.toSet());
        return locationMapper.selectList(new LambdaQueryWrapper<LocationDO>().eq(LocationDO::getWarehouseId, warehouseId).orderByAsc(LocationDO::getCode))
                .stream().map(l -> new LocationRow(l.getId(), l.getWarehouseId(), l.getCode(), l.getName(), l.getStatus().name(), l.getRemark(),
                        withStock.contains(l.getId()), l.getVersion())).toList();
    }

    /** LocationSelect：启用库位 */
    public List<LocationRow> simpleLocations(Long warehouseId) {
        return locationMapper.selectList(new LambdaQueryWrapper<LocationDO>().eq(LocationDO::getWarehouseId, warehouseId)
                        .eq(LocationDO::getStatus, EnableStatus.ENABLED).orderByAsc(LocationDO::getCode))
                .stream().map(l -> new LocationRow(l.getId(), l.getWarehouseId(), l.getCode(), l.getName(), l.getStatus().name(), null, false, l.getVersion()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createLocation(Long warehouseId, LocationSave req) {
        getOrThrow(warehouseId);
        String code = req.code().trim().toUpperCase();
        checkLocationCode(warehouseId, code, null);
        LocationDO l = new LocationDO();
        l.setWarehouseId(warehouseId);
        l.setCode(code);
        l.setName(StringUtils.hasText(req.name()) ? req.name().trim() : null);
        l.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
        l.setStatus(EnableStatus.ENABLED);
        locationMapper.insert(l);
        return l.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateLocation(Long warehouseId, Long locId, LocationSave req) {
        LocationDO l = getLocation(warehouseId, locId);
        String code = req.code().trim().toUpperCase();
        if (!code.equals(l.getCode())) {
            checkLocationCode(warehouseId, code, locId);
            if (locationHasStock(locId)) throw BizException.of(InventoryErrorCodes.LOCATION_HAS_STOCK, l.getCode());
        }
        l.setCode(code);
        l.setName(StringUtils.hasText(req.name()) ? req.name().trim() : null);
        l.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
        if (req.version() != null) l.setVersion(req.version());
        locationMapper.updateByIdOrFail(l);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setLocationStatus(Long warehouseId, Long locId, boolean enabled) {
        LocationDO l = getLocation(warehouseId, locId);
        if (!enabled && locationHasStock(locId)) throw BizException.of(InventoryErrorCodes.LOCATION_HAS_STOCK, l.getCode());
        l.setStatus(enabled ? EnableStatus.ENABLED : EnableStatus.DISABLED);
        locationMapper.updateByIdOrFail(l);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteLocation(Long warehouseId, Long locId) {
        LocationDO l = getLocation(warehouseId, locId);
        if (locationHasStock(locId) || txnMapper.selectCount(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getLocationId, locId)) > 0) {
            throw BizException.of(InventoryErrorCodes.LOCATION_HAS_STOCK, l.getCode());
        }
        locationMapper.deleteById(locId);
    }

    /** 批量生成库位（区-排-层-位），已存在的编码跳过 */
    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generateLocations(Long warehouseId, LocationGenerate req) {
        getOrThrow(warehouseId);
        char z1 = Character.toUpperCase(req.zoneFrom().charAt(0));
        char z2 = Character.toUpperCase(req.zoneTo().charAt(0));
        List<String> codes = new ArrayList<>();
        for (char z = (char) Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int r = Math.min(req.rowFrom(), req.rowTo()); r <= Math.max(req.rowFrom(), req.rowTo()); r++) {
                for (int lv = Math.min(req.levelFrom(), req.levelTo()); lv <= Math.max(req.levelFrom(), req.levelTo()); lv++) {
                    for (int p = Math.min(req.posFrom(), req.posTo()); p <= Math.max(req.posFrom(), req.posTo()); p++) {
                        codes.add(String.format("%c-%02d-%02d-%02d", z, r, lv, p));
                        if (codes.size() > MAX_GENERATE) throw BizException.of(InventoryErrorCodes.LOCATION_GENERATE_TOO_MANY, MAX_GENERATE);
                    }
                }
            }
        }
        Set<String> existing = locationMapper.selectList(new LambdaQueryWrapper<LocationDO>().select(LocationDO::getCode)
                .eq(LocationDO::getWarehouseId, warehouseId)).stream().map(LocationDO::getCode).collect(Collectors.toSet());
        List<String> fresh = codes.stream().filter(c -> !existing.contains(c)).toList();
        List<String> skipped = codes.stream().filter(existing::contains).toList();
        if (!req.preview()) {
            for (String c : fresh) {
                LocationDO l = new LocationDO();
                l.setWarehouseId(warehouseId);
                l.setCode(c);
                l.setStatus(EnableStatus.ENABLED);
                locationMapper.insert(l);
            }
        }
        return new GenerateResult(fresh, skipped);
    }

    private void checkLocationCode(Long warehouseId, String code, Long excludeId) {
        LocationDO dup = locationMapper.selectOne(new LambdaQueryWrapper<LocationDO>().eq(LocationDO::getWarehouseId, warehouseId).eq(LocationDO::getCode, code));
        if (dup != null && !dup.getId().equals(excludeId)) throw BizException.of(InventoryErrorCodes.LOCATION_CODE_DUPLICATE, code);
    }

    private boolean locationHasStock(Long locId) {
        return stockMapper.selectCount(new LambdaQueryWrapper<StockDO>().eq(StockDO::getLocationId, locId).ne(StockDO::getQty, 0)) > 0;
    }

    public LocationDO getLocation(Long warehouseId, Long locId) {
        LocationDO l = locId == null ? null : locationMapper.selectById(locId);
        if (l == null || (warehouseId != null && !l.getWarehouseId().equals(warehouseId))) throw new BizException(InventoryErrorCodes.LOCATION_NOT_EXISTS);
        return l;
    }

    public Map<Long, LocationDO> locationsByIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).filter(i -> i > 0).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        return locationMapper.selectBatchIds(set).stream().collect(Collectors.toMap(LocationDO::getId, l -> l));
    }

    // ==================== 类别默认仓 ====================

    public List<CategoryWarehouseRow> categoryWarehouses() {
        List<CategoryWarehouseDO> list = categoryWarehouseMapper.selectList(null);
        Map<Long, MaterialCategoryDTO> cats = new HashMap<>();
        categoryApi.listAll().forEach(c -> cats.put(c.id(), c));
        Map<Long, WarehouseDO> whs = byIds(list.stream().map(CategoryWarehouseDO::getWarehouseId).toList());
        return list.stream().map(r -> {
            WarehouseDO w = whs.get(r.getWarehouseId());
            return new CategoryWarehouseRow(r.getId(), r.getCategoryId(), categoryPath(cats, r.getCategoryId()), r.getWarehouseId(),
                    w == null ? null : w.getName(), w == null ? null : w.getWarehouseType());
        }).sorted(Comparator.comparing(r -> Objects.toString(r.categoryPath(), ""))).toList();
    }

    private static String categoryPath(Map<Long, MaterialCategoryDTO> cats, Long id) {
        List<String> names = new ArrayList<>();
        MaterialCategoryDTO c = cats.get(id);
        int guard = 0;
        while (c != null && guard++ < 10) {
            names.add(0, c.name());
            c = c.parentId() == null ? null : cats.get(c.parentId());
        }
        return names.isEmpty() ? String.valueOf(id) : String.join(" / ", names);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveCategoryWarehouse(Long id, CategoryWarehouseSave req) {
        WarehouseDO w = getOrThrow(req.warehouseId());
        if (!w.getWarehouseType().available()) throw new BizException(InventoryErrorCodes.CATEGORY_WAREHOUSE_UNAVAILABLE);
        CategoryWarehouseDO dup = categoryWarehouseMapper.selectOne(new LambdaQueryWrapper<CategoryWarehouseDO>().eq(CategoryWarehouseDO::getCategoryId, req.categoryId()));
        if (dup != null && !dup.getId().equals(id)) throw new BizException(InventoryErrorCodes.CATEGORY_WAREHOUSE_DUPLICATE);
        CategoryWarehouseDO r = id == null ? new CategoryWarehouseDO() : categoryWarehouseMapper.selectById(id);
        if (r == null) throw new BizException(InventoryErrorCodes.WAREHOUSE_NOT_EXISTS);
        r.setCategoryId(req.categoryId());
        r.setWarehouseId(req.warehouseId());
        if (id == null) categoryWarehouseMapper.insert(r);
        else categoryWarehouseMapper.updateByIdOrFail(r);
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCategoryWarehouse(Long id) {
        categoryWarehouseMapper.deleteById(id);
    }

    // ==================== WarehouseApi ====================

    @Override
    public Optional<WarehouseDTO> get(Long id) {
        return Optional.ofNullable(id == null ? null : warehouseMapper.selectById(id)).map(WarehouseService::toDto);
    }

    @Override
    public WarehouseDTO getDefaultWarehouse(Long materialId, WarehouseType warehouseType) {
        if (warehouseType != null) return defaultOfType(warehouseType).map(WarehouseService::toDto)
                .orElseThrow(() -> BizException.of(InventoryErrorCodes.NO_DEFAULT_WAREHOUSE, String.valueOf(materialId)));
        MaterialDTO m = materialApi.getMaterial(materialId).orElseThrow(() -> BizException.of(InventoryErrorCodes.NO_DEFAULT_WAREHOUSE, String.valueOf(materialId)));
        Map<Long, Long> mapping = categoryWarehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(CategoryWarehouseDO::getCategoryId, CategoryWarehouseDO::getWarehouseId, (a, b) -> a));
        Long cid = m.categoryId();
        int guard = 0;
        while (cid != null && guard++ < 10) {
            Long wid = mapping.get(cid);
            if (wid != null) {
                WarehouseDO w = warehouseMapper.selectById(wid);
                if (w != null && w.getStatus() == EnableStatus.ENABLED) return toDto(w);
            }
            cid = categoryApi.get(cid).map(MaterialCategoryDTO::parentId).orElse(null);
        }
        WarehouseType byType = typeOf(m.materialType());
        return (byType == null ? Optional.<WarehouseDO>empty() : defaultOfType(byType)).map(WarehouseService::toDto)
                .orElseThrow(() -> BizException.of(InventoryErrorCodes.NO_DEFAULT_WAREHOUSE, m.code()));
    }

    private static WarehouseType typeOf(MaterialType t) {
        if (t == null) return null;
        return switch (t) {
            case RAW -> WarehouseType.RAW;
            case SEMI_FINISHED -> WarehouseType.SEMI;
            case FINISHED -> WarehouseType.FG;
            case PACKAGING -> WarehouseType.PKG;
            case AUXILIARY -> WarehouseType.AUX;
            case PHANTOM -> null;
        };
    }

    public Optional<WarehouseDO> defaultOfType(WarehouseType type) {
        List<WarehouseDO> list = warehouseMapper.selectList(new LambdaQueryWrapper<WarehouseDO>().eq(WarehouseDO::getWarehouseType, type)
                .eq(WarehouseDO::getStatus, EnableStatus.ENABLED).orderByDesc(WarehouseDO::getIsDefault).orderByAsc(WarehouseDO::getCode));
        return list.stream().findFirst();
    }

    @Override
    public List<WarehouseDTO> listByType(WarehouseType type) {
        return warehouseMapper.selectList(new LambdaQueryWrapper<WarehouseDO>().eq(WarehouseDO::getWarehouseType, type)
                .eq(WarehouseDO::getStatus, EnableStatus.ENABLED).orderByAsc(WarehouseDO::getCode)).stream().map(WarehouseService::toDto).toList();
    }

    static WarehouseDTO toDto(WarehouseDO w) {
        return new WarehouseDTO(w.getId(), w.getCode(), w.getName(), w.getWarehouseType(), w.getWarehouseType().available(),
                Boolean.TRUE.equals(w.getLocationEnabled()), w.getStatus() == EnableStatus.ENABLED, Boolean.TRUE.equals(w.getIsDefault()));
    }

    private static String name(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }
}
