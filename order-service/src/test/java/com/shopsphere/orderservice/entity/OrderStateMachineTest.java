package com.shopsphere.orderservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStateMachineTest {

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT),
                Arguments.of(OrderStatus.PENDING, OrderStatus.FAILED),
                Arguments.of(OrderStatus.PENDING, OrderStatus.CANCEL_REQUESTED),
                Arguments.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCEL_REQUESTED),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.CANCEL_REQUESTED),
                Arguments.of(OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCEL_FAILED),
                Arguments.of(OrderStatus.CANCEL_FAILED, OrderStatus.CANCEL_REQUESTED),
                Arguments.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED)
        );
    }

    static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.PENDING, OrderStatus.DELIVERED),
                Arguments.of(OrderStatus.PENDING, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.FAILED),
                Arguments.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.DELIVERED),
                Arguments.of(OrderStatus.CONFIRMED, OrderStatus.FAILED),
                Arguments.of(OrderStatus.SHIPPED, OrderStatus.CANCEL_REQUESTED),
                Arguments.of(OrderStatus.DELIVERED, OrderStatus.CANCEL_REQUESTED),
                Arguments.of(OrderStatus.CANCELLED, OrderStatus.CONFIRMED),
                Arguments.of(OrderStatus.FAILED, OrderStatus.PENDING)
        );
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void canTransition_allowsValidMoves(OrderStatus from, OrderStatus to) {
        assertThat(OrderStateMachine.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void canTransition_rejectsInvalidMoves(OrderStatus from, OrderStatus to) {
        assertThat(OrderStateMachine.canTransition(from, to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"FAILED", "CANCELLED", "DELIVERED"})
    void isTerminal_returnsTrueForTerminalStatuses(OrderStatus status) {
        assertThat(OrderStateMachine.isTerminal(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "AWAITING_PAYMENT", "CONFIRMED", "SHIPPED", "CANCEL_REQUESTED", "CANCEL_FAILED"})
    void isTerminal_returnsFalseForNonTerminalStatuses(OrderStatus status) {
        assertThat(OrderStateMachine.isTerminal(status)).isFalse();
    }
}
