package dev.wido;

import org.microhttp.*;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.Logger.Level.INFO;

public class Main {
    public static void main(String[] args) throws Exception {
        var port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;

        var dataResponse = new Response(
            200,
            "OK",
            List.of(new Header("Content-Type", "text/plain")),
            ("Hello World! " + port + "\n").getBytes()
        );

        var statusResponse = new Response(
            200,
            "OK",
            List.of(new Header("Content-Type", "text/plain")),
            "I am alive\n".getBytes()
        );

        var notFoundResponse = new Response(
            404,
            "Not found",
            List.of(new Header("Content-Type", "text/plain")),
            "Not found\n".getBytes()
        );

        var options = new Options()
            .withHost("localhost")
            .withPort(port);

        var logger = System.getLogger("Target " + port);
        var stubLogger = new DebugLogger() {
            @Override
            public boolean enabled() {
                return false;
            }
        };

        var currentRequests = new AtomicInteger(0);

        Handler handler = (request, callback) -> Thread.startVirtualThread(() -> {
            switch (request.uri()) {
                case "/status" -> callback.accept(statusResponse);
                case "/" -> {
                    currentRequests.getAndIncrement();
                    try {
                        Thread.sleep(15);
                        callback.accept(dataResponse);
                    } catch (InterruptedException e) { throw new RuntimeException(e); }
                    currentRequests.decrementAndGet();
                }
                default -> callback.accept(notFoundResponse);
            }
        });

        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()).scheduleAtFixedRate(
            () -> logger.log(INFO, "Current connections: " + currentRequests.get()),
            10, 10, TimeUnit.SECONDS
        );

        EventLoop ev = new EventLoop(options, stubLogger, handler);
        ev.start();
        ev.join();
    }
}