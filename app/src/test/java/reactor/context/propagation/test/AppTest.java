package reactor.context.propagation.test;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.Loggers;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppTest {
  private ContextRegistry registry;

  private static final ThreadLocal<String> MY_THREADLOCALSTORAGE = new ThreadLocal<>();

  @BeforeEach void setup() {
    registry = ContextRegistry.getInstance();
  }

  @Test void contextSnapshotWithinSameThread() {
    ObservationThreadLocalAccessor accessor = new ObservationThreadLocalAccessor();
    registry.registerThreadLocalAccessor(accessor);
    final String FIRST = "g'day!!!";
    final String SECOND = "ٱلسَّلَامُ عَلَيْكُمْ,";
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

  @Test void worksBetweenThreads() throws InterruptedException {

    class PrivateThread implements Runnable {
      private static final ThreadLocal<String> MY_ID = new ThreadLocal<>();
      private final String CONTEXT_KEY = "CONTEXT_KEY:";
      private final String PREFIX = "HELLO-PREFIX-كيف حالك-";
      private final String GREETING = "HI!";
      private final String ITERATION_ID;

      PrivateThread(String iterationId) {
        this.ITERATION_ID = PREFIX + iterationId;
      }

      @Override public void run() {
        registry.registerThreadLocalAccessor(CONTEXT_KEY + ITERATION_ID,
          MY_ID::get,
          MY_ID::set,
          MY_ID::remove);
        Hooks.enableAutomaticContextPropagation();
        Mono<String> handler = Mono.defer(() -> Mono.just(GREETING)
          .delayElement(Duration.ofSeconds(2))
          .map(v -> v + MY_ID.get())
          .log()
          .contextWrite(Context.of(CONTEXT_KEY + ITERATION_ID, ITERATION_ID)));

        assertNull(MY_ID.get());
        final String BOO = "BOO!";
        MY_ID.set(BOO);
        assertEquals(MY_ID.get(), BOO);

        StepVerifier.create(handler)
          .expectNext(GREETING + ITERATION_ID)
          .expectComplete()
          .verify();

        MY_ID.remove();
      }
    }

    Loggers.useConsoleLoggers();
    ExecutorService service = Executors.newCachedThreadPool();
    final var MAX_TASKS = 50;
    for (int i = 0; i < MAX_TASKS; i++) {
      System.out.println("Iteration #: " + i);
      PrivateThread privateThread = new PrivateThread("TASK ID: " + i);
      service.submit(privateThread);
      Thread.sleep(new Random().nextInt(1000));
    }
    service.shutdown();
  }
}
