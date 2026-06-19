package ua.edu.kma;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Nested
    @DisplayName("1. Business Scenarios Logic")
    class BusinessScenarios {

        @Test
        void shouldRejectTransaction_whenUserIsSuspended() {
            Transaction tx = new Transaction("tx-1", "user-1", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-1")).thenReturn(new User("user-1", true));

            AnalysisResult result = service.analyze(tx);

            assertThat(result.status()).isEqualTo(Status.REJECTED);
        }

        @Test
        void shouldApproveTransaction_whenAllChecksPass() {
            Transaction tx = new Transaction("tx-2", "user-2", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-2")).thenReturn(new User("user-2", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("10.0"));

            AnalysisResult result = service.analyze(tx);

            assertThat(result.status()).isEqualTo(Status.APPROVED);
        }

        @Test
        void shouldThrowException_whenAmountIsNegativeOrZero() {
            Transaction tx = new Transaction("tx-3", "user-3", new BigDecimal("-10.0"), "UA");

            assertThatThrownBy(() -> service.analyze(tx))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be positive");
        }
    }

    @Nested
    @DisplayName("2. Void Methods Verification (verify, times, never)")
    class VoidMethodsVerification {

        @Test
        void verify_shouldCallAuditLogger_whenTransactionIsApproved() {
            Transaction tx = new Transaction("tx-4", "user-4", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-4")).thenReturn(new User("user-4", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("10.0"));

            service.analyze(tx);

            verify(auditLogger).logApproval("tx-4");
        }

        @Test
        void times_shouldLogFraudAttemptExactlyOnce_whenUserSuspended() {
            Transaction tx = new Transaction("tx-5", "user-5", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-5")).thenReturn(new User("user-5", true));

            service.analyze(tx);

            verify(auditLogger, times(1)).logFraudAttempt(eq("tx-5"), anyString());
        }

        @Test
        void never_shouldNotSendAdminAlert_whenRiskIsLow() {
            Transaction tx = new Transaction("tx-6", "user-6", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-6")).thenReturn(new User("user-6", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("10.0"));

            service.analyze(tx);

            verify(alertNotifier, never()).sendAdminAlert(anyString());
        }
    }

    @Nested
    @DisplayName("3 & 4. AssertJ State Verification")
    class AssertJVerifications {

        @Test
        void shouldVerifyMultipleFields_usingSoftAssertions() {
            Transaction tx = new Transaction("tx-7", "user-7", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-7")).thenReturn(new User("user-7", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("10.0"));

            AnalysisResult result = service.analyze(tx);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(result.txId()).isEqualTo("tx-7");
            softly.assertThat(result.status()).isEqualTo(Status.APPROVED);
            softly.assertThat(result.riskScore()).isEqualByComparingTo("10.0");
            softly.assertAll();
        }

        @Test
        void shouldVerifyListSize_whenMultipleRulesTriggered() {
            Transaction tx = new Transaction("tx-8", "user-8", new BigDecimal("15000.0"), "PL");
            when(userRepository.findById("user-8")).thenReturn(new User("user-8", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("45.0"));

            AnalysisResult result = service.analyze(tx);

            assertThat(result.triggeredRules())
                    .isNotEmpty()
                    .hasSize(2);
        }

        @Test
        void shouldVerifyExactListContent_regardlessOfOrder() {
            Transaction tx = new Transaction("tx-9", "user-9", new BigDecimal("15000.0"), "PL");
            when(userRepository.findById("user-9")).thenReturn(new User("user-9", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("45.0"));

            AnalysisResult result = service.analyze(tx);

            assertThat(result.triggeredRules())
                    .containsExactlyInAnyOrder("FOREIGN_TX", "HIGH_AMOUNT");
        }

        @Test
        void shouldVerifyRuleIsAbsent_whenConditionNotMet() {
            Transaction tx = new Transaction("tx-10", "user-10", new BigDecimal("15000.0"), "PL");
            when(userRepository.findById("user-10")).thenReturn(new User("user-10", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("45.0"));

            AnalysisResult result = service.analyze(tx);

            assertThat(result.triggeredRules())
                    .doesNotContain("CRITICAL_RISK_SCORE");
        }
    }

    @Nested
    @DisplayName("5. Mutation Testing Boundaries")
    class MutationTesting {

        @Test
        void WEAK_TEST_shouldRequireManualReview_whenRiskIsHigh() {
            Transaction tx = new Transaction("tx-11", "user-11", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-11")).thenReturn(new User("user-11", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("90.0"));

            AnalysisResult result = service.analyze(tx);

            assertThat(result.status()).isEqualTo(Status.MANUAL_REVIEW);
        }

        @Test
        void STRONG_TEST_shouldRequireManualReview_onExactBoundaryRiskScore() {
            Transaction tx = new Transaction("tx-12", "user-12", new BigDecimal("500.0"), "UA");
            when(userRepository.findById("user-12")).thenReturn(new User("user-12", false));
            when(riskEngine.calculateRisk(tx)).thenReturn(new BigDecimal("80.0"));

            AnalysisResult result = service.analyze(tx);

            assertThat(result.status()).isEqualTo(Status.MANUAL_REVIEW);
        }
    }
}
