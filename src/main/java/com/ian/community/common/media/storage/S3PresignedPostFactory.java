package com.ian.community.common.media.storage;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaProperties;
import com.ian.community.common.media.dto.PresignedPostResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
public class S3PresignedPostFactory {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final MediaProperties properties;
    private final AwsCredentialsProvider credentialsProvider;
    private final JsonMapper jsonMapper;

    public S3PresignedPostFactory(
            MediaProperties properties,
            AwsCredentialsProvider credentialsProvider,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.credentialsProvider = credentialsProvider;
        this.jsonMapper = jsonMapper;
    }

    public PresignedPostResponse create(String key, String contentType, long maximumSize, Duration duration) {
        AwsCredentials credentials = credentialsProvider.resolveCredentials();
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(duration);
        ZonedDateTime now = issuedAt.atZone(ZoneOffset.UTC);
        String date = DATE.format(now);
        String timestamp = TIMESTAMP.format(now);
        String scope = date + "/" + properties.awsRegion() + "/s3/aws4_request";
        String credential = credentials.accessKeyId() + "/" + scope;

        List<Object> conditions = new ArrayList<>();
        conditions.add(Map.of("bucket", properties.bucket()));
        conditions.add(Map.of("key", key));
        conditions.add(Map.of("Content-Type", contentType));
        conditions.add(Map.of("x-amz-server-side-encryption", "AES256"));
        conditions.add(Map.of("x-amz-algorithm", "AWS4-HMAC-SHA256"));
        conditions.add(Map.of("x-amz-credential", credential));
        conditions.add(Map.of("x-amz-date", timestamp));
        conditions.add(List.of("content-length-range", 1, maximumSize));
        if (credentials instanceof AwsSessionCredentials session) {
            conditions.add(Map.of("x-amz-security-token", session.sessionToken()));
        }

        try {
            String policyJson = jsonMapper.writeValueAsString(Map.of(
                    "expiration", DateTimeFormatter.ISO_INSTANT.format(expiresAt),
                    "conditions", conditions
            ));
            String policy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = signatureKey(credentials.secretAccessKey(), date, properties.awsRegion());
            String signature = HexFormat.of().formatHex(hmac(signingKey, policy));

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("key", key);
            fields.put("Content-Type", contentType);
            fields.put("x-amz-server-side-encryption", "AES256");
            fields.put("x-amz-algorithm", "AWS4-HMAC-SHA256");
            fields.put("x-amz-credential", credential);
            fields.put("x-amz-date", timestamp);
            fields.put("policy", policy);
            fields.put("x-amz-signature", signature);
            if (credentials instanceof AwsSessionCredentials session) {
                fields.put("x-amz-security-token", session.sessionToken());
            }
            String url = uploadUrl();
            return new PresignedPostResponse(url, Map.copyOf(fields), expiresAt);
        } catch (JacksonException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String uploadUrl() {
        String endpoint = properties.endpoints().s3Presign();
        if (endpoint == null || endpoint.isBlank()) {
            return "https://" + properties.bucket() + ".s3."
                    + properties.awsRegion() + ".amazonaws.com";
        }
        String base = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
        if (properties.endpoints().s3PathStyle()) {
            return base + "/" + properties.bucket();
        }
        URI uri = URI.create(base);
        String authority = properties.bucket() + "." + uri.getRawAuthority();
        return URI.create(uri.getScheme() + "://" + authority + uri.getRawPath()).toString();
    }

    private byte[] signatureKey(String secret, String date, String region) {
        byte[] dateKey = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), date);
        byte[] regionKey = hmac(dateKey, region);
        byte[] serviceKey = hmac(regionKey, "s3");
        return hmac(serviceKey, "aws4_request");
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create AWS signature", exception);
        }
    }
}
