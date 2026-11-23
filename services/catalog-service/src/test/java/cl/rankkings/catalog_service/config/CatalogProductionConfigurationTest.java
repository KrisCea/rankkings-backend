package cl.rankkings.catalog_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogProductionConfigurationTest {
    @Test
    void h2ConsoleIsDisabledInProductionProfile() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(
                        context.getEnvironment().getProperty("spring.h2.console.enabled", Boolean.class)
                ).isFalse());
    }
}
