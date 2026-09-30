-- 多张出货单合并一张 Packing List：shipment_id 为主出货单，shipment_ids 为全部出货单 ID（逗号分隔）；单张 PL 该列为空
ALTER TABLE shp_packing_list ADD COLUMN shipment_ids VARCHAR(512) NULL;
