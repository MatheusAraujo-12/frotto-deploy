package com.localuz.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class AWSConfig {

    private static final Regions BUCKET_REGION = Regions.US_EAST_1;

    private final Logger log = LoggerFactory.getLogger(AWSConfig.class);

    @Value("${aws.region:}")
    private String configuredRegion;

    @Value("${aws.key:${aws.accessKeyId:}}")
    private String key;

    @Value("${aws.secret:${aws.secretAccessKey:}}")
    private String secret;

    @Bean
    public AmazonS3 AuthenticationS3() {
        if (
            configuredRegion != null &&
            !configuredRegion.isBlank() &&
            !configuredRegion.equalsIgnoreCase(BUCKET_REGION.getName())
        ) {
            log.warn(
                "aws.region is set to '{}' but bucket is in '{}'; forcing S3 client region to '{}'",
                configuredRegion,
                BUCKET_REGION.getName(),
                BUCKET_REGION.getName()
            );
        }
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard().withRegion(BUCKET_REGION);
        if (key != null && !key.isBlank() && secret != null && !secret.isBlank()) {
            builder.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(key, secret)));
        }
        return builder.build();
    }
}
