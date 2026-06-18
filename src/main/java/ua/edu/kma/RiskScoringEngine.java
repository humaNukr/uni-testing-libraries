package ua.edu.kma;

import java.math.BigDecimal;

public interface RiskScoringEngine {
    BigDecimal calculateRisk(Transaction tx);
}
