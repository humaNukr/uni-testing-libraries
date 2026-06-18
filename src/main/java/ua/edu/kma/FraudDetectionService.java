package ua.edu.kma;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class FraudDetectionService {
    private final UserRepository userRepository;
    private final RiskScoringEngine riskEngine;
    private final AuditLogger auditLogger;
    private final AlertNotifier alertNotifier;

    public FraudDetectionService(UserRepository userRepository, RiskScoringEngine riskEngine,
                                 AuditLogger auditLogger, AlertNotifier alertNotifier) {
        this.userRepository = userRepository;
        this.riskEngine = riskEngine;
        this.auditLogger = auditLogger;
        this.alertNotifier = alertNotifier;
    }

    public AnalysisResult analyze(Transaction tx) {
        if (tx.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        User user = userRepository.findById(tx.userId());
        if (user == null || user.suspended()) {
            auditLogger.logFraudAttempt(tx.txId(), "Account is suspended");
            return new AnalysisResult(tx.txId(), Status.REJECTED, BigDecimal.ZERO, List.of("SUSPENDED_ACCOUNT"));
        }

        BigDecimal score = riskEngine.calculateRisk(tx);
        List<String> rules = new ArrayList<>();

        if (score.compareTo(new BigDecimal("80.0")) >= 0) {
            alertNotifier.sendAdminAlert(tx.txId());
            rules.add("CRITICAL_RISK_SCORE");
            return new AnalysisResult(tx.txId(), Status.MANUAL_REVIEW, score, rules);
        }

        if (tx.amount().compareTo(new BigDecimal("10000.0")) > 0) {
            rules.add("HIGH_AMOUNT");
        }
        if (!"UA".equals(tx.countryCode())) {
            rules.add("FOREIGN_TX");
        }

        auditLogger.logApproval(tx.txId());
        return new AnalysisResult(tx.txId(), Status.APPROVED, score, rules);
    }
}
