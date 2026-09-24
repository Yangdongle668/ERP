package com.erp.common.result;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** 分页结果。 */
public record PageResult<T>(List<T> list, long total) {

    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(list.stream().<R>map(mapper).toList(), total);
    }
}
