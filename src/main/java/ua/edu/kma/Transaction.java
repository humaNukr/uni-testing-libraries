package ua.edu.kma;

import java.math.BigDecimal;

public record Transaction(String txId, String userId, BigDecimal amount, String countryCode) {
}
