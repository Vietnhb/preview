package com.example.backend.service.school;

import com.example.backend.repository.school.SchoolPaymentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolPaymentRevenueSummaryTest {
    @Test
    void mapsDatabaseAggregatesWithoutLoadingPaymentRows() {
        SchoolPaymentRepository payments = mock(SchoolPaymentRepository.class);
        SchoolPaymentRepository.RevenueTotals totals = mock(SchoolPaymentRepository.RevenueTotals.class);
        when(payments.summarizeRevenue()).thenReturn(totals);
        when(totals.getPaidTransactions()).thenReturn(4L);
        when(totals.getPendingTransactions()).thenReturn(2L);
        when(totals.getReviewTransactions()).thenReturn(1L);
        when(totals.getGrossPaidVnd()).thenReturn(125_000L);

        SchoolPaymentService service = new SchoolPaymentService(null, payments, null, null, null, null,
                null, null, null, null, null);

        assertEquals(new SchoolPaymentService.RevenueSummary(4, 2, 1, 125_000), service.revenueSummary());
        verify(payments).summarizeRevenue();
        verify(payments, never()).findAll();
    }
}
