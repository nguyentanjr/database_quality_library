package org.example.enums;

public enum Severity {
    CRITICAL(15),
    HIGH(8),
    MEDIUM(3),
    LOW(1);

    private final int score;

    Severity(int score) {
        this.score = score;
    }

    public int getScore() {
        return this.score;
    }
}
