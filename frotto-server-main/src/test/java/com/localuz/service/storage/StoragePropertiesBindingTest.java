package com.localuz.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.FileSystemResource;

/**
 * Proves with the real application YAML files that an absent configuration keeps the legacy S3 mode (production
 * safety) and that the local mode only exists when explicitly requested.
 */
class StoragePropertiesBindingTest {

    // Not the classpath: src/test/resources/config/application.yml shadows the production file there.
    private static final String MAIN_RESOURCES = "src/main/resources/";

    @Test
    void productionProfileWithoutStorageVariablesStaysOnS3() throws IOException {
        StorageProperties properties = bind(Map.of(), "config/application-prod.yml", "config/application.yml");

        assertThat(properties.getMode()).isEqualTo(StorageProperties.StorageMode.S3);
        assertThat(properties.isLocalMode()).isFalse();
        assertThat(properties.getRoot()).isNullOrEmpty();
        assertThat(properties.getFiles().getSigningSecret()).isNullOrEmpty();
        assertThat(properties.getLegacyS3().isReadFallbackEnabled()).isFalse();
        assertThat(properties.getLegacyS3().getPublicBaseUrl()).isEqualTo("https://localuz-locamais.s3.us-east-1.amazonaws.com");
    }

    @Test
    void stagingVariablesEnableLocalModeExplicitly() throws IOException {
        StorageProperties properties = bind(
            Map.of(
                "FROTTO_STORAGE_MODE",
                "local",
                "FROTTO_STORAGE_ROOT",
                "/app/storage",
                "FROTTO_FILES_BASE_URL",
                "https://arquivos-staging.frotto.com.br",
                "FROTTO_LEGACY_S3_READ_FALLBACK_ENABLED",
                "true"
            ),
            "config/application-prod.yml",
            "config/application.yml"
        );

        assertThat(properties.getMode()).isEqualTo(StorageProperties.StorageMode.LOCAL);
        assertThat(properties.getRoot()).isEqualTo("/app/storage");
        assertThat(properties.getFiles().getBaseUrl()).isEqualTo("https://arquivos-staging.frotto.com.br");
        assertThat(properties.getFiles().getUrlTtlSeconds()).isEqualTo(21600);
        assertThat(properties.getLegacyS3().isReadFallbackEnabled()).isTrue();
    }

    @Test
    void devProfileUsesTheDevBucketForReads() throws IOException {
        StorageProperties properties = bind(Map.of(), "config/application-dev.yml", "config/application.yml");

        assertThat(properties.getMode()).isEqualTo(StorageProperties.StorageMode.S3);
        assertThat(properties.getLegacyS3().getPublicBaseUrl()).isEqualTo("https://localuz-locamais-dev.s3.amazonaws.com");
    }

    @Test
    void misspelledModeFailsInsteadOfSilentlyPickingAStorage() {
        assertThatThrownBy(() -> bind(Map.of("FROTTO_STORAGE_MODE", "locl"), "config/application.yml")).isInstanceOf(BindException.class);
    }

    private static StorageProperties bind(Map<String, Object> environment, String... yamlFiles) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("environment", environment));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String yamlFile : yamlFiles) {
            loader.load(yamlFile, new FileSystemResource(MAIN_RESOURCES + yamlFile)).forEach(sources::addLast);
        }
        Binder binder = new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources));
        return binder.bindOrCreate("frotto.storage", Bindable.of(StorageProperties.class));
    }
}
