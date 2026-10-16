package dev.ofrostt.frostduels;

import java.util.UUID;

/** Only this state machine awards points; repeated death events cannot score twice. */
final class MatchState {
    enum Phase { COUNTDOWN, FIGHTING, BETWEEN_ROUNDS, ENDED }
    final UUID first, second;
    final int required;
    private Phase phase = Phase.COUNTDOWN;
    private int firstScore, secondScore;

    MatchState(UUID first, UUID second, int required) {
        if (first.equals(second) || required < 1) throw new IllegalArgumentException("Invalid match");
        this.first = first; this.second = second; this.required = required;
    }
    Phase phase() { return phase; }
    int firstScore() { return firstScore; }
    int secondScore() { return secondScore; }
    boolean contains(UUID id) { return first.equals(id) || second.equals(id); }
    UUID opponent(UUID id) {
        if (!contains(id)) throw new IllegalArgumentException("Not a participant");
        return first.equals(id) ? second : first;
    }
    void fight() {
        if (phase != Phase.COUNTDOWN) throw new IllegalStateException("Not counting down");
        phase = Phase.FIGHTING;
    }
    boolean point(UUID winner) {
        if (!contains(winner)) throw new IllegalArgumentException("Not a participant");
        if (phase != Phase.FIGHTING) return false;
        if (first.equals(winner)) firstScore++; else secondScore++;
        phase = firstScore >= required || secondScore >= required ? Phase.ENDED : Phase.BETWEEN_ROUNDS;
        return true;
    }
    void nextRound() {
        if (phase != Phase.BETWEEN_ROUNDS) throw new IllegalStateException("No next round");
        phase = Phase.COUNTDOWN;
    }
    void end() { phase = Phase.ENDED; }
}
