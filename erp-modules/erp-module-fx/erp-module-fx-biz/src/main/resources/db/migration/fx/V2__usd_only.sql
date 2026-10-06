-- 只保存美元的数据（需求 16-实时汇率）：删除之前保存的其他汇率对（EUR_CNY、EUR_USD）；其他币别改为仅实时报价
DELETE FROM fx_quote WHERE pair <> 'USD_CNY';
DELETE FROM fx_daily_rate WHERE pair <> 'USD_CNY';
DELETE FROM fx_monthly_rate WHERE pair <> 'USD_CNY';
