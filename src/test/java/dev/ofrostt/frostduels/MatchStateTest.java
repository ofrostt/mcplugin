package dev.ofrostt.frostduels;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchStateTest {
    @Test void scoresFirstToAndRejectsDuplicateDeaths() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        MatchState match = new MatchState(first, second, 2);
        assertFalse(match.point(first));
        match.fight(); assertTrue(match.point(first)); assertFalse(match.point(second));
        assertEquals(1, match.firstScore()); assertEquals(0, match.secondScore());
        match.nextRound(); match.fight(); match.point(second);
        match.nextRound(); match.fight(); match.point(first);
        assertEquals(MatchState.Phase.ENDED, match.phase()); assertEquals(2, match.firstScore());
        assertFalse(match.point(second)); assertThrows(IllegalStateException.class, match::nextRound);
    }
    @Test void validatesParticipantsAndTransitions() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new MatchState(a, a, 1));
        assertThrows(IllegalArgumentException.class, () -> new MatchState(a, b, 0));
        MatchState match = new MatchState(a, b, 1);
        assertEquals(b, match.opponent(a)); assertEquals(a, match.opponent(b));
        assertThrows(IllegalArgumentException.class, () -> match.point(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, match::nextRound);
        match.end(); assertFalse(match.point(a)); assertThrows(IllegalStateException.class, match::fight);
    }
}
