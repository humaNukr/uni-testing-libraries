package ua.edu.kma;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RiskScoringEngine riskEngine;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private AlertNotifier alertNotifier;

    @InjectMocks
    private FraudDetectionService service;

    @Test
    void shouldRejectTransaction_whenUserIsSuspended() {
        Transaction tx = new Transaction("tx-1", "user-1", new BigDecimal("500.0"), "UA");
        when(userRepository.findById("user-1")).thenReturn(new User("user-1", true));

        AnalysisResult result = service.analyze(tx);

        assertThat(result.status()).isEqualTo(Status.REJECTED);
        
        verify(auditLogger, times(1)).logFraudAttempt("tx-1", "Account is suspended");
        verify(alertNotifier, never()).sendAdminAlert(any());
        verifyNoInteractions(riskEngine);
    }

    @Test
    void shouldApproveWithRiskFactors_whenAmountIsHighAndForeign() {
        Transaction tx = new Transaction("tx-2", "user-2", new BigDecimal("15000.0"), "PL");
        when(userRepository.findById("user-2")).thenReturn(new User("user-2", false));
        when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("45.0"));

        AnalysisResult result = service.analyze(tx);

        assertThat(result.status()).isEqualTo(Status.APPROVED);
        verify(auditLogger).logApproval("tx-2");

        assertThat(result.triggeredRules())
                .isNotEmpty()
                .hasSize(2)
                .containsExactlyInAnyOrder("FOREIGN_TX", "HIGH_AMOUNT")
                .doesNotContain("CRITICAL_RISK_SCORE");
    }

    @Test
    void shouldReturnDetailedResult_whenTransactionIsApproved() {
        Transaction tx = new Transaction("tx-3", "user-3", new BigDecimal("100.0"), "UA");
        when(userRepository.findById("user-3")).thenReturn(new User("user-3", false));
        when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("10.0"));

        AnalysisResult result = service.analyze(tx);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.txId()).isEqualTo("tx-3");
        softly.assertThat(result.status()).isEqualTo(Status.APPROVED);
        softly.assertThat(result.riskScore()).isEqualByComparingTo("10.0");
        softly.assertThat(result.triggeredRules()).isEmpty();
        softly.assertAll();
    }

    @Test
    void WEAK_TEST_shouldRequireManualReview_whenRiskIsHigh() {
        Transaction tx = new Transaction("tx-4", "user-4", new BigDecimal("500.0"), "UA");
        when(userRepository.findById("user-4")).thenReturn(new User("user-4", false));
        
        when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("90.0")); 

        AnalysisResult result = service.analyze(tx);

        assertThat(result.status()).isEqualTo(Status.MANUAL_REVIEW);
    }

    @Test
    void STRONG_TEST_shouldRequireManualReview_onExactBoundaryRiskScore() {
        Transaction tx = new Transaction("tx-4", "user-4", new BigDecimal("500.0"), "UA");
        when(userRepository.findById("user-4")).thenReturn(new User("user-4", false));
        
        when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("80.0")); 

        AnalysisResult result = service.analyze(tx);

        assertThat(result.status()).isEqualTo(Status.MANUAL_REVIEW);
        verify(alertNotifier).sendAdminAlert("tx-4");
    }
}
