package com.erp.common.statemachine;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 通用有限状态机：定义「状态 + 动作 → 新状态」，不可变、线程安全。
 *
 * <p>所有单据的状态变更都必须通过状态机计算目标状态，禁止在业务代码中直接 setStatus，
 * 这样非法流转（例如对已作废单据审核）会被统一拦截。模块特有的业务状态
 * （如检验单的“待检/检验中/已判定”）用同样方式定义自己的状态机。
 *
 * <pre>{@code
 * StateMachine<MyStatus, MyAction> sm = StateMachine.<MyStatus, MyAction>builder(MyStatus.class, MyAction.class)
 *         .transition(MyStatus.A, MyAction.GO, MyStatus.B)
 *         .build();
 * MyStatus next = sm.fire(MyStatus.A, MyAction.GO);
 * }</pre>
 */
public final class StateMachine<S extends Enum<S>, A extends Enum<A>> {

    private final Map<S, Map<A, S>> transitions;

    private StateMachine(Map<S, Map<A, S>> transitions) {
        this.transitions = transitions;
    }

    public static <S extends Enum<S>, A extends Enum<A>> Builder<S, A> builder(Class<S> stateType, Class<A> actionType) {
        return new Builder<>(stateType, actionType);
    }

    /** 计算目标状态；不允许的流转抛出 {@link BizException}。 */
    public S fire(S current, A action) {
        return next(current, action).orElseThrow(() -> BizException.of(
                GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, label(current), label(action)));
    }

    public Optional<S> next(S current, A action) {
        Objects.requireNonNull(current, "current state");
        Objects.requireNonNull(action, "action");
        return Optional.ofNullable(transitions.getOrDefault(current, Map.of()).get(action));
    }

    public boolean canFire(S current, A action) {
        return next(current, action).isPresent();
    }

    /** 当前状态下允许的动作，可用于前端控制按钮显示。 */
    public Set<A> allowedActions(S current) {
        return Collections.unmodifiableSet(transitions.getOrDefault(current, Map.of()).keySet());
    }

    private static String label(Enum<?> e) {
        return e instanceof Labeled labeled ? labeled.label() : e.name();
    }

    /** 枚举实现该接口后，错误提示使用中文标签。 */
    public interface Labeled {
        String label();
    }

    public static final class Builder<S extends Enum<S>, A extends Enum<A>> {
        private final Class<S> stateType;
        private final Class<A> actionType;
        private final Map<S, Map<A, S>> transitions;

        private Builder(Class<S> stateType, Class<A> actionType) {
            this.stateType = stateType;
            this.actionType = actionType;
            this.transitions = new EnumMap<>(stateType);
        }

        public Builder<S, A> transition(S from, A action, S to) {
            Map<A, S> byAction = transitions.computeIfAbsent(from, k -> new EnumMap<>(actionType));
            S existing = byAction.putIfAbsent(action, to);
            if (existing != null && existing != to) {
                throw new IllegalStateException("重复定义流转: " + from + " --" + action + "--> " + existing + " / " + to);
            }
            return this;
        }

        public StateMachine<S, A> build() {
            Map<S, Map<A, S>> copy = new EnumMap<>(stateType);
            transitions.forEach((k, v) -> copy.put(k, Collections.unmodifiableMap(new EnumMap<>(v))));
            return new StateMachine<>(Collections.unmodifiableMap(copy));
        }
    }
}
