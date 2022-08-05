package dev.wido;

import org.microhttp.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static dev.wido.Utils.uncheckedLift;

public class LoadBalancer {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void run(List<String> targets) {
        var statusRequests = targets.stream()
            .map(t -> HttpRequest.newBuilder()
                .uri(URI.create(t + "/status"))
                .timeout(Duration.ofMillis(100))
                .build())
            .toList();

        var dataRequestsMap = statusRequests.stream().collect(Collectors.toMap(
            statusRequest -> statusRequest,
            statusRequest -> HttpRequest.newBuilder()
                .uri(uncheckedLift(() -> new URI(
                        statusRequest.uri().getScheme(),
                        null,
                        statusRequest.uri().getHost(),
                        statusRequest.uri().getPort(),
                        null, null, null))
                    .get())
                .timeout(Duration.ofMillis(100))
                .build()
        ));

        var errorResponse = new Response(
            500,
            "Internal Server Error",
            List.of(new Header("Content-Type", "text/plain")),
            "Target proxy failed\n".getBytes()
        );

        var options = new Options()
            .withHost("localhost")
            .withPort(8080);

        var stubLogger = new DebugLogger() {
            @Override
            public boolean enabled() {
                return false;
            }
        };

        var aliveDataRequests = new AtomicReference<List<HttpRequest>>();
        aliveDataRequests.set(List.of());

        var executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        executor.scheduleAtFixedRate(
            () -> aliveDataRequests.lazySet(getDataRequestsOfAliveTargets(statusRequests, dataRequestsMap)),
            0, 1, TimeUnit.MILLISECONDS
        );

        var roundRobinCounter = new AtomicInteger(0);
        Handler handler = (request, consumer) -> Thread.startVirtualThread(() -> {

            var startTime = Instant.now();
            while (Duration.between(startTime, Instant.now()).toSeconds() < 5) {
                var adr = aliveDataRequests.get();
                if (adr.size() == 0) continue;

                var i = roundRobinCounter.getAndIncrement() % adr.size();
                var req =
                    uncheckedLift(() -> httpClient.send(adr.get(i), BodyHandlers.ofString()));
                if (req.isEmpty() || req.get().statusCode() != 200) continue;

                roundRobinCounter.getAndUpdate(rr -> rr % adr.size());
                consumer.accept(constructMicrohttpResponse(req.get()));
                return;
            }

            consumer.accept(errorResponse);
        });

        try {
            var ev = new EventLoop(options, stubLogger, handler);
            ev.start();
            ev.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public List<HttpRequest> getDataRequestsOfAliveTargets(
        List<HttpRequest> aliveRequests, Map<HttpRequest, HttpRequest> dataRequests)
    {
        return aliveRequests.stream()
            .map(httpRequest -> uncheckedLift(() -> httpClient.send(httpRequest, BodyHandlers.ofString())))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(r -> r.statusCode() == 200)
            .map(HttpResponse::request)
            .map(dataRequests::get)
            .toList();
    }

    public Response constructMicrohttpResponse(HttpResponse<String> r) {
        return new Response(
            r.statusCode(),
            "",
            r.headers().map().entrySet().stream()
                .map(entry -> new Header(entry.getKey(), String.join(", ", entry.getValue())))
                .toList(),
            r.body().getBytes()
        );
    }
}
