package com.ian.community.common.media.worker;

import com.ian.community.common.media.MediaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.runtime", havingValue = "worker")
public class SqsMediaWorker implements SmartLifecycle {
    private static final Pattern SOURCE_KEY = Pattern.compile("^private/uploads/([0-9a-fA-F-]{36})/source$");

    private final SqsClient sqsClient;
    private final MediaProcessingService processingService;
    private final MediaProperties properties;
    private final JsonMapper jsonMapper;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService executor;
    private ScheduledExecutorService heartbeatExecutor;

    public SqsMediaWorker(
            SqsClient sqsClient,
            MediaProcessingService processingService,
            MediaProperties properties,
            JsonMapper jsonMapper
    ) {
        this.sqsClient = sqsClient;
        this.processingService = processingService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        sweepScratch();
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "media-sqs-worker");
            thread.setDaemon(false);
            return thread;
        });
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "media-worker-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::touchHeartbeat, 0, 30, TimeUnit.SECONDS);
        executor.submit(this::pollLoop);
    }

    @Override
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            touchHeartbeat();
            try {
                ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(properties.queueUrl())
                        .waitTimeSeconds(properties.worker().waitTimeSeconds())
                        .visibilityTimeout(properties.worker().visibilityTimeoutSeconds())
                        .maxNumberOfMessages(1)
                        .messageSystemAttributeNamesWithStrings("ApproximateReceiveCount")
                        .build());
                for (Message message : response.messages()) {
                    handle(message);
                }
            } catch (SqsException exception) {
                sleepAfterFailure();
            }
        }
    }

    private void handle(Message message) {
        MediaJob job;
        try {
            job = extractJob(message.body());
        } catch (Exception exception) {
            delete(message);
            return;
        }

        int receiveCount = Integer.parseInt(message.attributesAsStrings()
                .getOrDefault("ApproximateReceiveCount", "1"));
        try {
            processingService.process(job.mediaId(), job.revision());
            delete(message);
        } catch (PermanentMediaProcessingException exception) {
            delete(message);
        } catch (RuntimeException exception) {
            if (receiveCount >= properties.worker().maxReceiveCount()) {
                processingService.failRetryExhausted(job.mediaId(), job.revision());
            }
        } finally {
            touchHeartbeat();
        }
    }

    UUID extractMediaId(String body) throws Exception {
        return extractJob(body).mediaId();
    }

    MediaJob extractJob(String body) throws Exception {
        JsonNode root = jsonMapper.readTree(body);
        if ("MEDIA_REVISION".equals(root.path("type").asText())) {
            UUID mediaId = UUID.fromString(root.path("mediaId").asText());
            int revision = root.path("revision").asInt(0);
            if (revision < 2) {
                throw new IllegalArgumentException("Invalid media revision job");
            }
            return new MediaJob(mediaId, revision);
        }
        JsonNode records = root.path("Records");
        if (!records.isArray() || records.isEmpty()) {
            throw new IllegalArgumentException("Unsupported S3 event");
        }
        String encodedKey = records.get(0).path("s3").path("object").path("key").asText();
        String key = URLDecoder.decode(encodedKey.replace("+", "%2B"), StandardCharsets.UTF_8);
        Matcher matcher = SOURCE_KEY.matcher(key);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected S3 key");
        }
        return new MediaJob(UUID.fromString(matcher.group(1)), 1);
    }

    record MediaJob(UUID mediaId, int revision) {}

    private void delete(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
    }

    private void touchHeartbeat() {
        try {
            Path root = Path.of(properties.worker().scratchRoot()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Files.writeString(
                    root.resolve("heartbeat"),
                    Instant.now().toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ignored) {
            // Docker healthcheck will make persistent heartbeat failures visible.
        }
    }

    void sweepScratch() {
        Path root = Path.of(properties.worker().scratchRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            try (var paths = Files.list(root)) {
                paths.filter(path -> path.getFileName().toString().startsWith("media-"))
                        .forEach(this::deleteTree);
            }
        } catch (Exception ignored) {
            // Healthcheck and the first job will surface an unusable scratch mount.
        }
    }

    private void deleteTree(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, java.io.IOException exc) throws java.io.IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
            // Best-effort startup sweep.
        }
    }

    private void sleepAfterFailure() {
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
