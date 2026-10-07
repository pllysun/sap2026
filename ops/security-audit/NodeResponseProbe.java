// java -Xmx256m ops/security-audit/NodeResponseProbe.java
// Reproduces JudgerNodeTransport's exact JDK response API, not the Spring bean.
// One random localhost server; fixed synthetic 41 MiB response, no secrets.
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

class NodeResponseProbe {
    public static void main(String[] args) throws Exception {
        final int limit = 40 * 1024 * 1024;
        final int length = 41 * 1024 * 1024;
        final AtomicLong sent = new AtomicLong();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/status", exchange -> {
            byte[] chunk = new byte[64 * 1024];
            Arrays.fill(chunk, (byte)'x');
            exchange.sendResponseHeaders(200, length);
            try (var body = exchange.getResponseBody()) {
                for (int remaining = length; remaining > 0; remaining -= chunk.length) {
                    int count = Math.min(remaining, chunk.length);
                    body.write(chunk, 0, count);
                    sent.addAndGet(count);
                }
            }
        });
        server.start();
        try {
            final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/status"))
                .timeout(Duration.ofSeconds(5)).build();
            // This is the response handling used at JudgerNodeTransport.java:22.
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // This is the later limit check used at JudgerNodeTransport.java:24.
            final boolean tooLarge = response.body().length() > limit;
            System.out.println("{\n" +
                "  \"scope\": \"JDK HttpClient API reproducer; actual transport bean not loaded; synthetic localhost only\",\n" +
                "  \"jdk\": \"" + System.getProperty("java.version") + "\",\n" +
                "  \"configured_response_limit_bytes\": " + limit + ",\n" +
                "  \"server_bytes_sent\": " + sent.get() + ",\n" +
                "  \"full_response_characters_materialized_before_size_check\": " + response.body().length() + ",\n" +
                "  \"late_size_check_rejected\": " + tooLarge + ",\n" +
                "  \"actual_oom_attempted\": false\n}");
        } finally {
            server.stop(0);
        }
    }
}
