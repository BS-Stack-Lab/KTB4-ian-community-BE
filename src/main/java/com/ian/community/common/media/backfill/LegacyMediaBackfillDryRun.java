package com.ian.community.common.media.backfill;

import com.ian.community.post.repository.PostImageRepository;
import com.ian.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.runtime", havingValue = "media-backfill-dry-run")
public class LegacyMediaBackfillDryRun implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyMediaBackfillDryRun.class);

    private final PostImageRepository postImageRepository;
    private final UserRepository userRepository;
    private final ConfigurableApplicationContext context;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments arguments) {
        long postCandidates = postImageRepository.countByMediaAssetIsNullAndPendingMediaIsNull();
        long profileCandidates = userRepository.countByProfileMediaIsNull();
        log.info(
                "MEDIA_BACKFILL_DRY_RUN postCandidates={} profileCandidates={} mutations=0",
                postCandidates,
                profileCandidates
        );
        SpringApplication.exit(context, () -> 0);
    }
}
