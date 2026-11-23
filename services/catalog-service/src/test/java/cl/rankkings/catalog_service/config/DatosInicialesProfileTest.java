package cl.rankkings.catalog_service.config;

import cl.rankkings.catalog_service.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DatosInicialesProfileTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(ItemRepository.class, () -> mock(ItemRepository.class))
            .withPropertyValues("rankkings.seed-catalog=true");

    @Test
    void productionProfileDoesNotRegisterDemoSeederEvenWhenSeedingIsEnabled() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).doesNotHaveBean(DatosIniciales.class));
    }

    @Test
    void localProfileCanRegisterCatalogSeeder() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).hasSingleBean(DatosIniciales.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DatosIniciales.class)
    static class TestConfiguration {
    }
}
