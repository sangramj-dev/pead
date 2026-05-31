package org.pead.common.exception;

public class RiskVetoException extends PeadBaseException {

    private final String ruleViolated;

    public RiskVetoException(String message, String ruleViolated, String correlationId) {
        super(message, correlationId, "RISK_VETO");
        this.ruleViolated = ruleViolated;
    }

    public String getRuleViolated() {
        return ruleViolated;
    }
}
