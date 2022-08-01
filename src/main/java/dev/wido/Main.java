package dev.wido;

import java.util.List;
import java.util.stream.IntStream;

public class Main {
    public static void main(String[] args) {
        var targets = (args.length == 0
                ? IntStream.rangeClosed(8081, 8085).boxed().map(String::valueOf).toList()
                : List.of(args))
            .stream()
            .map(s -> "http://localhost:" + s)
            .toList();

        new LoadBalancer().run(targets);
    }

}