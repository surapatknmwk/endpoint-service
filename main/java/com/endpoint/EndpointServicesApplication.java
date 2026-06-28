package com.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Consolidated entry point that merges the formerly separate microservices
 * (authentication, core, master-data and search) into a single Spring Boot
 * application so they can run inside one container.
 *
 * <p>The four original code bases keep their own base packages
 * ({@code com.authen}, {@code com.core}, {@code com.master},
 * {@code com.search}). Because several classes share the same simple name across
 * those packages (e.g. {@code ApiClient}, {@code SecurityFilter},
 * {@code GlobalExceptionHandler}, {@code MasterDataService}), the
 * {@link FullyQualifiedAnnotationBeanNameGenerator} is used so every component
 * gets a unique, fully-qualified bean name instead of clashing on the default
 * short name.</p>
 */
@Slf4j
@EnableCaching
@SpringBootApplication(
        scanBasePackages = {"com.common", "com.authen", "com.core", "com.master", "com.search"},
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
@EntityScan(basePackages = {
        "com.authen.entity",
        "com.core.entity",
        "com.master.entity",
        "com.search.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.authen.repository",
        "com.core.repository",
        "com.master.repository",
        "com.search.repository"
})
public class EndpointServicesApplication {

    public static void main(String[] args) {
        Environment env = SpringApplication.run(EndpointServicesApplication.class, args).getEnvironment();

        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String profiles = String.join(", ", env.getActiveProfiles().length > 0 ? env.getActiveProfiles() : new String[]{"default"});
        String dbUrl = env.getProperty("spring.datasource.url", "N/A");

        log.info("===========================================================");
        log.info(" Application : {}", env.getProperty("spring.application.name"));
        log.info(" Profile(s)  : {}", profiles);
        log.info(" Port        : {}", port);
        log.info(" Context Path: {}", contextPath);
        log.info(" DB URL      : {}", dbUrl);
        log.info(" Swagger     : http://localhost:{}/swagger-ui.html", port);
        log.info(" Modules     : authen / core / master / search");
        log.info("===========================================================");
    }

}
