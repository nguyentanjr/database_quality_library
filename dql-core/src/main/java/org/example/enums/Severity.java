package org.example.enums;

public enum Severity {
    HIGH(20),
    MEDIUM(10),
    LOW(2);

    private final int score;

    Severity(int score) {
        this.score = score;
    }

    public int getScore() {
        return this.score;
    }
}
