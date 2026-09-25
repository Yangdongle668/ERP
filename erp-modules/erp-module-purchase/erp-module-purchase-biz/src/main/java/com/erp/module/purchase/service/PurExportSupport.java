package com.erp.module.purchase.service;

import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.task.AsyncTaskApi;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

/**
 * 导出（UI 设计规范 9.2）：结果不超过参数 sys.export.sync-max-rows 时直接下载，超过时转为后台任务（任务中心下载）。
 */
@Component
public class PurExportSupport {

    private final ParamApi paramApi;
    private final AsyncTaskApi asyncTaskApi;

    public PurExportSupport(ParamApi paramApi, AsyncTaskApi asyncTaskApi) {
        this.paramApi = paramApi;
        this.asyncTaskApi = asyncTaskApi;
    }

    /**
     * @param columns 前端列设置中的可见列（逗号分隔），为空导出全部列
     * @param loader  按最大行数加载数据
     */
    public <T> void export(HttpServletResponse response, String name, List<ExcelColumn<T>> excelColumns, String columns,
                           IntFunction<List<T>> loader) throws IOException {
        int max = paramApi.getInt("sys.export.sync-max-rows");
        List<String> keys = columns == null || columns.isBlank() ? null : Arrays.asList(columns.split(","));
        List<T> rows = loader.apply(max + 1);
        if (rows.size() <= max) {
            ExcelSupport.export(response, name, excelColumns, rows, keys);
            return;
        }
        Long taskId = asyncTaskApi.submit(AsyncTaskApi.TYPE_EXPORT, "导出" + name, PurchaseModuleConfig.MODULE, ctx -> {
            List<T> all = loader.apply(Integer.MAX_VALUE);
            ctx.progress(50);
            ctx.resultFile(ExcelSupport.fileName(name), ExcelSupport.XLSX, ExcelSupport.toBytes(name, excelColumns, all, keys));
            ctx.resultMessage(String.format("成功导出 %,d 行", all.size()));
        });
        ExcelSupport.writeAsyncAccepted(response, taskId);
    }
}
