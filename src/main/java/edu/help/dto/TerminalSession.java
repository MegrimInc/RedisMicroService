package edu.help.dto;
import lombok.Data;
import java.io.Serializable;

@Data
public class TerminalSession implements Serializable {
    private int merchantId;
    private int employeeId;
    private String sessionId;

    // Default constructor
    public TerminalSession() {
    }

    // Constructor with parameters
    public TerminalSession(int merchantId, int employeeId, String sessionId) {
        this.merchantId = merchantId;
        this.employeeId = employeeId;
        this.sessionId = sessionId;
    }

    @Override
    public String toString() {
        return "TerminalSession{" +
                "merchantId=" + merchantId +
                ", employeeId='" + employeeId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}