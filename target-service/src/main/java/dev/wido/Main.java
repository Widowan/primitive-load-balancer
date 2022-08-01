package dev.wido;

import org.microhttp.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
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

        var multiThreadedScheduledExecutor = Executors.newScheduledThreadPool(1);
        var multiThreadedCachedExecutor = Executors.newFixedThreadPool(1);
        var currentRequests = new AtomicInteger(0);

        Handler handler = (request, callback) -> {
            logger.log(INFO, "Connection: " + request);
            switch (request.uri()) {
                case "/status" -> callback.accept(statusResponse);
                case "/" -> {
                    currentRequests.getAndIncrement();
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    Thread.sleep(2);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                callback.accept(dataResponse);
                            },
                            multiThreadedCachedExecutor)
                        .thenRun(currentRequests::decrementAndGet);
                }
                default -> callback.accept(notFoundResponse);
            }
        };

        multiThreadedScheduledExecutor.scheduleAtFixedRate(
            () -> logger.log(INFO, "Current connections: " + currentRequests.get()),
            10, 10, TimeUnit.SECONDS
        );

        EventLoop ev = new EventLoop(options, stubLogger, handler);
        ev.start();
        ev.join();
    }
}