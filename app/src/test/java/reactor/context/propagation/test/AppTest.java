package reactor.context.propagation.test;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import reactor.util.Loggers;
import reactor.util.context.Context;
import reactor.test.StepVerifier;

import java.time.Duration;

class AppTest {
  private ContextRegistry registry;

  private static final ThreadLocal<String> MY_THREADLOCALSTORAGE = new ThreadLocal<>();

  @BeforeEach void setup() {
    registry = ContextRegistry.getInstance();
  }

  @Test void contextSnapshotWithinSameThread() {
    var accessor = new ObservationThreadLocalAccessor();
    registry.registerThreadLocalAccessor(accessor);
    final var FIRST = "g'day!!!";
    final var SECOND = "ٱلسَّلَامُ عَلَيْكُمْ,";
    ObservationThreadLocalHolder.setValue(FIRST);
    // capture!
    ContextSnapshot snapshot = ContextSnapshot.captureAllUsing(key -> true, registry);
    // change ThreadLocal
    ObservationThreadLocalHolder.setValue(SECOND);
    try {
      try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        assertEquals(ObservationThreadLocalHolder.getValue(), FIRST);
      }
      assertEquals(ObservationThreadLocalHolder.getValue(), SECOND);
    } finally {
      // prevent polluting the thread
      ObservationThreadLocalHolder.reset();
    }
  }

  @Test void worksBetweenThreads() {
    Loggers.useConsoleLoggers();
    final var CONTEXT_KEY = "كيف حالك";
    final var ALPHANUMERIC_ID = "THIS IS MY ID!!!1234-567-ABC";
    final var HELLO = "HELLO!";
    registry.registerThreadLocalAccessor(CONTEXT_KEY,
      MY_THREADLOCALSTORAGE::get,
      MY_THREADLOCALSTORAGE::set,
      MY_THREADLOCALSTORAGE::remove);
    Hooks.enableAutomaticContextPropagation();
    Mono<String> handler = Mono.defer(() -> Mono.just(HELLO)
      .delayElement(Duration.ofSeconds(2))
      .map(v -> v + MY_THREADLOCALSTORAGE.get())
      .log()
      .contextWrite(Context.of(CONTEXT_KEY, ALPHANUMERIC_ID)));

    assertNull(MY_THREADLOCALSTORAGE.get());
    final var BOO = "BOO!";
    MY_THREADLOCALSTORAGE.set(BOO);
    assertEquals(MY_THREADLOCALSTORAGE.get(), BOO);

    StepVerifier.create(handler)
      .expectNext(HELLO + ALPHANUMERIC_ID)
      .expectComplete()
      .verify();

    MY_THREADLOCALSTORAGE.remove();
  }
}
