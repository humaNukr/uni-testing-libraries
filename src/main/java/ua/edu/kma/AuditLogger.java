package ua.edu.kma;

public interface AuditLogger {
    void logFraudAttempt(String txId, String reason);

    void logApproval(String txId);
}
