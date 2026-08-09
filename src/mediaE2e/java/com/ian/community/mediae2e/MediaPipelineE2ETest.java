package com.ian.community.mediae2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaPipelineE2ETest {
    private static final String LOCAL_BUCKET = "community-media-e2e";
    private static final String QUEUE = "community-media-e2e";
    private static final String DLQ = "community-media-e2e-dlq";
    private static final String LOCAL_REGION = "us-east-1";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<AutoCloseable> resources = new ArrayList<>();
    private final boolean awsMode = Boolean.getBoolean("mediaE2e.aws");
    private final String bucket = System.getProperty("mediaE2e.bucket", LOCAL_BUCKET);
    private final String region = System.getProperty("mediaE2e.region", LOCAL_REGION);
    private final String cdnBase = System.getProperty(
            "mediaE2e.cdnBase", "https://cdn.example.test"
    ).replaceAll("/+$", "");
    private Network network;
    private MySQLContainer mysql;
    private LocalStackContainer localStack;
    private GenericContainer<?> api;
    private GenericContainer<?> worker;
    private S3Client s3;
    private SqsClient sqs;
    private String queueUrl;
    private String dlqUrl;
    private HttpClient http;
    private CookieManager cookies;
    private URI apiBase;

    @BeforeAll
    void startEnvironment() {
        network = Network.newNetwork();
        resources.add(network);

        DockerImageName mysqlImage = DockerImageName.parse(System.getProperty(
                "mediaE2e.mysqlImage", "mysql:8.4.11"
        )).asCompatibleSubstituteFor("mysql");
        mysql = new MySQLContainer(mysqlImage)
                .withDatabaseName("community")
                .withUsername("community")
                .withPassword("community-password")
                .withNetwork(network)
                .withNetworkAliases("mysql");
        mysql.start();
        resources.add(mysql);

        if (!awsMode) {
            DockerImageName localStackImage = DockerImageName.parse(System.getProperty(
                    "mediaE2e.localstackImage",
                    "localstack/localstack:4.14.0@sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a"
            )).asCompatibleSubstituteFor("localstack/localstack");
            localStack = new LocalStackContainer(localStackImage)
                    .withServices("s3", "sqs")
                    .withEnv("SQS_ENDPOINT_STRATEGY", "path")
                    .withNetwork(network)
                    .withNetworkAliases("localstack");
            String localStackToken = System.getenv("LOCALSTACK_AUTH_TOKEN");
            if (localStackToken != null && !localStackToken.isBlank()) {
                localStack.withEnv("LOCALSTACK_AUTH_TOKEN", localStackToken);
            }
            localStack.start();
            resources.add(localStack);
        }

        configureAwsClients();
        if (awsMode) {
            queueUrl = requiredProperty("mediaE2e.queueUrl");
            dlqUrl = requiredProperty("mediaE2e.dlqUrl");
        } else {
            provisionMediaResources();
        }

        DockerImageName backendImage = DockerImageName.parse(System.getProperty(
                "mediaE2e.backendImage", "community-backend:media-e2e"
        ));
        api = serviceContainer(backendImage, "api")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        api.start();
        resources.add(api);

        worker = serviceContainer(backendImage, "worker")
                .withEnv("SPRING_MAIN_WEB_APPLICATION_TYPE", "none")
                .withEnv("MEDIA_WORKER_SCRATCH", "/var/lib/community/media-worker")
                .withCreateContainerCmdModifier(command -> command.getHostConfig()
                        .withReadonlyRootfs(true)
                        .withTmpFs(Map.of(
                                "/tmp", "rw,noexec,nosuid,nodev,size=32m,uid=10001,gid=10001",
                                "/var/lib/community/media-worker",
                                "rw,noexec,nosuid,nodev,size=128m,uid=10001,gid=10001"
                        )))
                .waitingFor(Wait.forLogMessage(".*Started CommunityApplication.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        worker.start();
        resources.add(worker);

        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        apiBase = URI.create("http://" + api.getHost() + ":" + api.getMappedPort(8080));
    }

    @AfterAll
    void stopEnvironment() {
        for (int index = resources.size() - 1; index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (Exception ignored) {
                // Preserve the original E2E failure while still removing every resource.
            }
        }
    }

    @Test
    void runsUploadResizeRevisionDuplicateFailureDlqAndCleanup() throws Exception {
        authenticate();
        byte[] source = landscapePng();

        JsonNode upload = mutate(
                "POST", "/api/v2/media/uploads",
                """
                        {
                          "purpose":"POST",
                          "fileName":"source.png",
                          "contentType":"image/png",
                          "fileSize":%d,
                          "frame":"POST_LANDSCAPE",
                          "rotation":0,
                          "crop":{"x":0,"y":0,"width":1,"height":1},
                          "zoom":1,
                          "position":{"x":0.5,"y":0.5}
                        }
                        """.formatted(source.length),
                201
        );
        UUID mediaId = UUID.fromString(upload.path("mediaId").asText());
        uploadPresignedPost(upload.path("upload"), source);
        mutate("POST", "/api/v2/media/" + mediaId + "/complete", null, 202);

        JsonNode ready = waitForMediaStatus(mediaId, "READY", Duration.ofMinutes(2));
        assertEquals(1, ready.path("variants").size());
        assertEquals(1344, ready.path("variants").get(0).path("width").asInt());
        assertEquals(864, ready.path("variants").get(0).path("height").asInt());
        assertWebpVariants(mediaId, 1, 1, 1344, 864);
        if (awsMode) assertCloudFrontDelivery(mediaId);
        assertSourceWasRemoved(mediaId);

        sendDuplicateS3Event(mediaId);
        await("duplicate S3 event consumption", Duration.ofSeconds(20), () ->
                approximateMessages(queueUrl) == 0
        );
        assertWebpVariants(mediaId, 1, 1, 1344, 864);

        JsonNode revision = mutate(
                "POST", "/api/v2/media/" + mediaId + "/revisions",
                """
                        {
                          "frame":"POST_LANDSCAPE",
                          "crop":{"x":0.02,"y":0.02,"width":0.96,"height":0.96},
                          "zoom":1.5,
                          "position":{"x":0.5,"y":0.5}
                        }
                        """,
                202
        );
        int revisionNumber = revision.path("revision").asInt();
        JsonNode readyRevision = waitForRevisionStatus(
                mediaId, revisionNumber, "READY", Duration.ofMinutes(2)
        );
        assertEquals(1.5, readyRevision.path("zoom").asDouble(), 0.0001);
        assertEquals(1, readyRevision.path("variants").size());
        assertEquals(1344, readyRevision.path("variants").get(0).path("width").asInt());
        assertEquals(864, readyRevision.path("variants").get(0).path("height").asInt());
        assertWebpVariants(mediaId, revisionNumber, 1, 1344, 864);
        assertEquals(1, getJson("/api/v2/media/" + mediaId, 200)
                .path("mediaRevision").asInt());
        List<String> revisionKeys = variantKeys(mediaId, revisionNumber);
        mutate(
                "DELETE", "/api/v2/media/" + mediaId + "/revisions/" + revisionNumber,
                null, 204
        );
        await("revision variant cleanup", Duration.ofSeconds(20), () ->
                revisionKeys.stream().noneMatch(this::objectExists)
        );

        String masterKey = "private/media/" + mediaId + "/master.r1.t1.webp";
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(masterKey).build());
        JsonNode failedRevision = mutate(
                "POST", "/api/v2/media/" + mediaId + "/revisions",
                """
                        {
                          "frame":"POST_LANDSCAPE",
                          "crop":{"x":0,"y":0,"width":1,"height":1},
                          "zoom":1,
                          "position":{"x":0.5,"y":0.5}
                        }
                        """,
                202
        );
        int failedRevisionNumber = failedRevision.path("revision").asInt();
        JsonNode failed = waitForRevisionStatus(
                mediaId, failedRevisionNumber, "FAILED", Duration.ofMinutes(2)
        );
        assertEquals("PROCESSING_RETRY_EXHAUSTED", failed.path("errorCode").asText());
        await("DLQ redrive", Duration.ofMinutes(1), () -> approximateMessages(dlqUrl) >= 1);

        mutate("DELETE", "/api/v2/media/" + mediaId, null, 204);
        await("media object cleanup", Duration.ofSeconds(20), () ->
                s3.listObjectsV2(ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix("private/media/" + mediaId + "/")
                                .build())
                        .contents().isEmpty()
                        && s3.listObjectsV2(ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix("public/media/" + mediaId + "/")
                                .build()).contents().isEmpty()
        );
    }

    private void configureAwsClients() {
        if (awsMode) {
            var credentials = DefaultCredentialsProvider.create();
            s3 = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    .build();
            sqs = SqsClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    .build();
        } else {
            var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    localStack.getAccessKey(), localStack.getSecretKey()
            ));
            s3 = S3Client.builder()
                    .endpointOverride(localStack.getEndpoint())
                    .region(Region.of(localStack.getRegion()))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true).build())
                    .build();
            sqs = SqsClient.builder()
                    .endpointOverride(localStack.getEndpoint())
                    .region(Region.of(localStack.getRegion()))
                    .credentialsProvider(credentials)
                    .build();
        }
        resources.add(s3);
        resources.add(sqs);
    }

    private void provisionMediaResources() {
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        dlqUrl = sqs.createQueue(CreateQueueRequest.builder()
                        .queueName(DLQ)
                        .attributes(Map.of(
                                QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "1"
                        )).build())
                .queueUrl();
        String dlqArn = queueArn(dlqUrl);
        queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                        .queueName(QUEUE)
                        .attributes(Map.of(
                                QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "1",
                                QueueAttributeName.REDRIVE_POLICY,
                                "{\"deadLetterTargetArn\":\"" + dlqArn
                                        + "\",\"maxReceiveCount\":\"3\"}"
                        )).build())
                .queueUrl();
        String queueArn = queueArn(queueUrl);
        String policy = """
                {"Version":"2012-10-17","Statement":[{
                  "Effect":"Allow","Principal":{"Service":"s3.amazonaws.com"},
                  "Action":"sqs:SendMessage","Resource":"%s",
                  "Condition":{"ArnEquals":{"aws:SourceArn":"arn:aws:s3:::%s"}}
                }]}
                """.formatted(queueArn, bucket).replaceAll("\\s+", "");
        sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, policy))
                .build());
        s3.putBucketNotificationConfiguration(PutBucketNotificationConfigurationRequest.builder()
                .bucket(bucket)
                .notificationConfiguration(NotificationConfiguration.builder()
                        .queueConfigurations(QueueConfiguration.builder()
                                .queueArn(queueArn)
                                .eventsWithStrings("s3:ObjectCreated:*")
                                .filter(NotificationConfigurationFilter.builder()
                                        .key(S3KeyFilter.builder()
                                                .filterRules(FilterRule.builder()
                                                        .name("prefix")
                                                        .value("private/uploads/")
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
    }

    private String queueArn(String url) {
        return sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(url)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private GenericContainer<?> serviceContainer(DockerImageName image, String runtime) {
        String runtimeQueueUrl = awsMode
                ? queueUrl
                : "http://localstack:4566" + URI.create(queueUrl).getRawPath();
        GenericContainer<?> container = new GenericContainer<>(image)
                .withNetwork(network)
                .withEnv("SPRING_PROFILES_ACTIVE", "aws")
                .withEnv("SERVER_ADDRESS", "0.0.0.0")
                .withEnv("DB_URL", "jdbc:mysql://mysql:3306/community"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC")
                .withEnv("DB_USERNAME", "community")
                .withEnv("DB_PASSWORD", "community-password")
                .withEnv("DB_POOL_MAX_SIZE", "4")
                .withEnv("DB_POOL_MIN_IDLE", "1")
                .withEnv("JWT_SECRET", "Y2ktb25seS1qd3Qtc2VjcmV0LTMyLWJ5dGVzISEhISE=")
                .withEnv("FRONTEND_ORIGIN", "http://127.0.0.1")
                .withEnv("COOKIE_SECURE", "false")
                .withEnv("APP_RUNTIME", runtime)
                .withEnv("MEDIA_V2_ENABLED", "true")
                .withEnv("MEDIA_BUCKET", bucket)
                .withEnv("MEDIA_QUEUE_URL", runtimeQueueUrl)
                .withEnv("MEDIA_ROLE_ARN", "")
                .withEnv("MEDIA_CDN_BASE_URL", cdnBase)
                .withEnv("MEDIA_DISTRIBUTION_ID", "")
                .withEnv("MEDIA_ENVIRONMENT", "e2e")
                .withEnv("MEDIA_TRANSFORM_VERSION", "1")
                .withEnv("MEDIA_WORKER_WAIT_SECONDS", "1")
                .withEnv("MEDIA_WORKER_VISIBILITY_SECONDS", "2")
                .withEnv("MEDIA_WORKER_MAX_RECEIVE_COUNT", "3")
                .withEnv("AWS_REGION", region)
                .withEnv("AWS_EC2_METADATA_DISABLED", "true")
                .withStartupTimeout(Duration.ofMinutes(3));
        if (awsMode) {
            container.withEnv("MEDIA_S3_ENDPOINT", "")
                    .withEnv("MEDIA_S3_PRESIGN_ENDPOINT", "")
                    .withEnv("MEDIA_SQS_ENDPOINT", "")
                    .withEnv("MEDIA_CLOUDWATCH_ENDPOINT", "")
                    .withEnv("MEDIA_S3_PATH_STYLE", "false")
                    .withEnv("AWS_ACCESS_KEY_ID", requiredEnvironment("AWS_ACCESS_KEY_ID"))
                    .withEnv("AWS_SECRET_ACCESS_KEY", requiredEnvironment("AWS_SECRET_ACCESS_KEY"))
                    .withEnv("AWS_SESSION_TOKEN", requiredEnvironment("AWS_SESSION_TOKEN"));
        } else {
            container.withEnv("MEDIA_S3_ENDPOINT", "http://localstack:4566")
                    .withEnv("MEDIA_S3_PRESIGN_ENDPOINT", localStack.getEndpoint().toString())
                    .withEnv("MEDIA_SQS_ENDPOINT", "http://localstack:4566")
                    .withEnv("MEDIA_CLOUDWATCH_ENDPOINT", "http://localstack:4566")
                    .withEnv("MEDIA_S3_PATH_STYLE", "true")
                    .withEnv("AWS_ACCESS_KEY_ID", localStack.getAccessKey())
                    .withEnv("AWS_SECRET_ACCESS_KEY", localStack.getSecretKey());
        }
        return container;
    }

    private void authenticate() throws Exception {
        getJson("/api/csrf", 200);
        mutate(
                "POST", "/api/users/signup",
                """
                        {
                          "email":"media-e2e@example.com",
                          "password":"MediaE2e1!",
                          "password_confirm":"MediaE2e1!",
                          "nickname":"미디어E2E"
                        }
                        """,
                200
        );
    }

    private JsonNode getJson(String path, int expectedStatus) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(apiBase.resolve(path))
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .build();
        return parse(http.send(request, HttpResponse.BodyHandlers.ofString()), expectedStatus);
    }

    private JsonNode mutate(String method, String path, String body, int expectedStatus)
            throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(apiBase.resolve(path))
                .method(method, publisher)
                .header("Accept", "application/json")
                .header("X-XSRF-TOKEN", csrfToken())
                .timeout(Duration.ofSeconds(30));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return parse(http.send(builder.build(), HttpResponse.BodyHandlers.ofString()), expectedStatus);
    }

    private JsonNode parse(HttpResponse<String> response, int expectedStatus) {
        assertEquals(
                expectedStatus,
                response.statusCode(),
                () -> response.body() + apiDiagnostics()
        );
        return response.body() == null || response.body().isBlank()
                ? jsonMapper.createObjectNode()
                : jsonMapper.readTree(response.body());
    }

    private String apiDiagnostics() {
        if (api == null || !api.isRunning()) {
            return "";
        }
        return "\n--- API container logs ---\n" + api.getLogs();
    }

    private String csrfToken() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing XSRF-TOKEN cookie"));
    }

    private void uploadPresignedPost(JsonNode upload, byte[] source) throws Exception {
        String boundary = "----community-media-e2e-" + UUID.randomUUID();
        Map<String, String> fields = new LinkedHashMap<>();
        upload.path("fields").properties().forEach(entry ->
                fields.put(entry.getKey(), entry.getValue().asText())
        );
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            writePart(body, boundary, field.getKey(), null, null,
                    field.getValue().getBytes(StandardCharsets.UTF_8));
        }
        writePart(body, boundary, "file", "source.png", "image/png", source);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(upload.path("url").asText()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200 || response.statusCode() == 204, response.body());
    }

    private void writePart(
            ByteArrayOutputStream body,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] value
    ) throws Exception {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) disposition += "; filename=\"" + filename + "\"";
        body.write((disposition + "\r\n").getBytes(StandardCharsets.UTF_8));
        if (contentType != null) {
            body.write(("Content-Type: " + contentType + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(value);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode waitForMediaStatus(UUID mediaId, String status, Duration timeout) {
        return awaitJson("media " + status, timeout, () -> {
            try {
                JsonNode value = getJson("/api/v2/media/" + mediaId, 200);
                return status.equals(value.path("status").asText()) ? value : null;
            } catch (Exception ignored) {
                return null;
            }
        });
    }

    private JsonNode waitForRevisionStatus(
            UUID mediaId, int revision, String status, Duration timeout
    ) {
        return awaitJson("revision " + revision + " " + status, timeout, () -> {
            try {
                JsonNode value = getJson(
                        "/api/v2/media/" + mediaId + "/revisions/" + revision, 200
                );
                return status.equals(value.path("status").asText()) ? value : null;
            } catch (Exception ignored) {
                return null;
            }
        });
    }

    private JsonNode awaitJson(String label, Duration timeout, Supplier<JsonNode> condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode result = condition.get();
            if (result != null) return result;
            sleep();
        }
        throw new AssertionError("Timed out waiting for " + label);
    }

    private void await(String label, Duration timeout, Supplier<Boolean> condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) return;
            sleep();
        }
        throw new AssertionError("Timed out waiting for " + label);
    }

    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting", exception);
        }
    }

    private byte[] landscapePng() throws Exception {
        BufferedImage image = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(25, 90, 180));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(250, 210, 40));
        graphics.fillOval(300, 120, 800, 660);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private void assertWebpVariants(
            UUID mediaId,
            int revision,
            int expectedCount,
            int expectedWidth,
            int expectedHeight
    ) {
        List<String> keys = variantKeys(mediaId, revision);
        assertEquals(expectedCount, keys.size());
        for (String key : keys) {
            byte[] value = s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket).key(key).build())
                    .asByteArray();
            assertTrue(value.length > 12);
            assertEquals("RIFF", new String(value, 0, 4, StandardCharsets.US_ASCII));
            assertEquals("WEBP", new String(value, 8, 4, StandardCharsets.US_ASCII));
            assertEquals("VP8 ", new String(value, 12, 4, StandardCharsets.US_ASCII));
            assertTrue(value.length >= 30);
            int width = (value[26] & 0xff) | ((value[27] & 0x3f) << 8);
            int height = (value[28] & 0xff) | ((value[29] & 0x3f) << 8);
            assertEquals(expectedWidth, width);
            assertEquals(expectedHeight, height);
        }
    }

    private List<String> variantKeys(UUID mediaId, int revision) {
        String marker = ".r" + revision + ".t1.webp";
        return s3.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket).prefix("public/media/" + mediaId + "/").build())
                .contents().stream()
                .map(S3Object::key)
                .filter(key -> key.endsWith(marker))
                .sorted()
                .toList();
    }

    private void assertSourceWasRemoved(UUID mediaId) {
        assertFalse(objectExists("private/uploads/" + mediaId + "/source"));
    }

    private boolean objectExists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void sendDuplicateS3Event(UUID mediaId) {
        String body = """
                {"Records":[{"s3":{"object":{"key":"private%%2Fuploads%%2F%s%%2Fsource"}}}]}
                """.formatted(mediaId).trim();
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl).messageBody(body).build());
    }

    private int approximateMessages(String url) {
        Map<QueueAttributeName, String> attributes = sqs.getQueueAttributes(
                        GetQueueAttributesRequest.builder()
                                .queueUrl(url)
                                .attributeNames(
                                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
                                ).build())
                .attributes();
        return Integer.parseInt(attributes.getOrDefault(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"
        )) + Integer.parseInt(attributes.getOrDefault(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0"
        ));
    }

    private void assertCloudFrontDelivery(UUID mediaId) {
        String variantKey = variantKeys(mediaId, 1).getFirst();
        URI variantUrl = URI.create(cdnBase + "/" + variantKey.substring("public/".length()));
        await("CloudFront immutable variant", Duration.ofMinutes(2), () -> {
            try {
                HttpResponse<Void> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(variantUrl).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                );
                return response.statusCode() == 200
                        && response.headers().firstValue("cache-control")
                        .orElse("").contains("max-age=31536000")
                        && response.headers().firstValue("cache-control")
                        .orElse("").contains("immutable");
            } catch (Exception ignored) {
                return false;
            }
        });
        try {
            HttpResponse<Void> privateResponse = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(cdnBase
                                    + "/private/media/" + mediaId + "/master.r1.t1.webp"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.discarding()
            );
            assertTrue(privateResponse.statusCode() == 403
                    || privateResponse.statusCode() == 404);
        } catch (Exception exception) {
            throw new AssertionError("Unable to verify private CloudFront denial", exception);
        }
    }

    private String requiredProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + key);
        }
        return value;
    }

    private String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
