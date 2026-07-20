package io.redlink.more.data.store.observationCallback;

import io.redlink.more.data.model.ActiveObservation;
import io.redlink.more.data.model.RoutingInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryObservationCallbackStoreTest {

    private InMemoryObservationCallbackStore store;
    private RoutingInfo routingInfo;
    private ActiveObservation activeObservation;

    @BeforeEach
    void setUp() {
        store = new InMemoryObservationCallbackStore();
        routingInfo = new RoutingInfo(1L, 1, java.util.OptionalInt.empty(), Set.of(), true, true);
        activeObservation = new ActiveObservation("1", Instant.now().truncatedTo(ChronoUnit.SECONDS), Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void testSaveAndPullRedirect() {
        String redirectUrl = "http://test.com/redirect";
        store.saveRedirect(routingInfo, activeObservation, redirectUrl);

        Optional<URI> pulled = store.pullRedirect(routingInfo, 1);
        assertTrue(pulled.isPresent());
        assertEquals(redirectUrl, pulled.get().toString());

        // Should be empty after pull
        assertTrue(store.pullRedirect(routingInfo, 1).isEmpty());
    }

    @Test
    void testMarkAndIsCompleted() {
        assertFalse(store.isCompleted(routingInfo, activeObservation));
        store.markCompleted(routingInfo, activeObservation);
        assertTrue(store.isCompleted(routingInfo, activeObservation));
    }

    @Test
    void testPullRedirectMarksAsCompleted() {
        String redirectUrl = "http://test.com/redirect";
        store.saveRedirect(routingInfo, activeObservation, redirectUrl);

        assertFalse(store.isCompleted(routingInfo, activeObservation));
        store.pullRedirect(routingInfo, 1);
        assertTrue(store.isCompleted(routingInfo, activeObservation));
    }

    @Test
    void testGetCompletedData() {
        store.markCompleted(routingInfo, activeObservation);
        var completed = store.getCompletedData(routingInfo);
        assertEquals(1, completed.size());
        assertEquals(activeObservation.observationId(), completed.get(0).observationId());
    }
}
