import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthCheck {
    private static final String DEFAULT_URL =
            "http://127.0.0.1:8080/actuator/health";

    private HealthCheck() {
    }

    public static void main(String[] arguments) throws Exception {
        String mode = arguments.length > 0 ? arguments[0] : "--check";
        String url = arguments.length > 1 ? arguments[1] : DEFAULT_URL;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if ("--status".equals(mode)) {
            System.out.println(response.statusCode());
            return;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            System.err.println("Unhealthy HTTP status: " + response.statusCode());
            System.exit(1);
        }

        if ("--body".equals(mode)) {
            System.out.println(response.body());
        }
    }
}
