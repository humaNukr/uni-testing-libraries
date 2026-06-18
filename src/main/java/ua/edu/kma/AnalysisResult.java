package ua.edu.kma;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisResult(String txId, Status status, BigDecimal riskScore, List<String> triggeredRules) {
}
