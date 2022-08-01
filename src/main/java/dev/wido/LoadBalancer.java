package dev.wido;

import org.microhttp.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static java.lang.System.Logger.Level.INFO;

public class LoadBalancer {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final System.Logger logger = System.getLogger("Balancer");

    public void run(List<String> targets) {
        var checkAliveRequests = targets.stream()
            .map(t -> HttpRequest.newBuilder()
                .uri(URI.create(t + "/status"))
                .timeout(Duration.ofMillis(100))
                .build())
            .toList();

        var dataRequests = targets.stream()
            .map(t -> HttpRequest.newBuilder()
                .uri(URI.create(t + "/"))
                .build())
            .toList();

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

        var multiThreadedCachedExecutor = Executors.newCachedThreadPool();

        Handler handler = (request, callback) ->
            anyOfIgnoreExceptions(getCheckAliveFutures(checkAliveRequests), multiThreadedCachedExecutor)
            .thenCompose(r -> {
                var nth = r.request().uri().getPort() % 10 - 1;
                return httpClient.sendAsync(dataRequests.get(nth), BodyHandlers.ofString());
            })
            .thenAcceptAsync(r -> callback.accept(constructMicrohttpResponse(r)), multiThreadedCachedExecutor)
            .whenComplete((r, ex) -> {
                if (ex != null) {
                    logger.log(INFO, "No alive targets");
                    callback.accept(errorResponse);
                }
            });

        try {
            var ev = new EventLoop(options, stubLogger, handler);
            ev.start();
            ev.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public List<CompletableFuture<HttpResponse<String>>> getCheckAliveFutures(List<HttpRequest> checkAliveRequests) {
        return IntStream.range(0, checkAliveRequests.size())
            .boxed()
            .map(i -> httpClient.sendAsync(checkAliveRequests.get(i), BodyHandlers.ofString()))
            .toList();
    }

    // https://stackoverflow.com/questions/33913193/completablefuture-waiting-for-first-one-normally-return
    @SuppressWarnings("SuspiciousToArrayCall")
    public static <T> CompletableFuture<T> anyOfIgnoreExceptions(
            List<? extends CompletionStage<? extends T>> l,
            ExecutorService exec)
    {
        CompletableFuture<T> f = new CompletableFuture<>();
        CompletableFuture.allOf(
                    l.stream()
                    .map(s -> s.thenAcceptAsync(f::complete, exec))
                    .toArray(CompletableFuture<?>[]::new))
            // allOf's exceptionally handler is only called
            // if there was no successful completion already
            .exceptionally(ex -> {
                f.completeExceptionally(ex);
                return null;
            });
        return f;
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
