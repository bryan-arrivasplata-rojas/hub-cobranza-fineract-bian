package com.bank.collectionhub;

import com.bank.collectionhub.adapter.FineractClient.InstallmentData;
import com.bank.collectionhub.model.OrderStatus;
import com.bank.collectionhub.service.CustomerBillingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CustomerBillingServiceTest {

    private final CustomerBillingService service = new CustomerBillingService(null, null, null, null);

    @Test
    void shouldIncludeInstallmentsWithinHorizon() {
        InstallmentData dueOct = new InstallmentData(
                1, LocalDate.of(2026, 10, 15),
                new BigDecimal("1120.00"), new BigDecimal("1000.00"), new BigDecimal("120.00"), new BigDecimal("1120.00")
        );
        InstallmentData dueDec = new InstallmentData(
                3, LocalDate.of(2026, 12, 15),
                new BigDecimal("1100.00"), new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("1100.00")
        );

        LocalDate horizon = LocalDate.of(2026, 11, 15);
        assertTrue(service.isEligible(dueOct, horizon, false));
        assertFalse(service.isEligible(dueDec, horizon, false));
        assertEquals(OrderStatus.PENDING, service.resolveStatus(dueOct));
    }

    @Test
    void shouldResolvePartiallyPaidAndPaidStates() {
        InstallmentData partial = new InstallmentData(
                1, LocalDate.of(2026, 10, 15),
                new BigDecimal("1120.00"), new BigDecimal("620.00"), BigDecimal.ZERO, new BigDecimal("620.00")
        );
        InstallmentData paid = new InstallmentData(
                1, LocalDate.of(2026, 10, 15),
                new BigDecimal("1120.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        assertEquals(OrderStatus.PARTIALLY_PAID, service.resolveStatus(partial));
        assertEquals(OrderStatus.PAID, service.resolveStatus(paid));
        assertTrue(service.isEligible(paid, LocalDate.of(2026, 11, 15), true));
    }
}
