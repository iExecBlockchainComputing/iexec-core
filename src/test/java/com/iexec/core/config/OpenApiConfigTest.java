package com.iexec.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@Import(ProjectInfoAutoConfiguration.class)
class OpenApiConfigTest {

    @Autowired
    private BuildProperties buildProperties;

    private OpenApiConfig openApiConfig;

    @BeforeEach
    void setUp() {
        openApiConfig = new OpenApiConfig(buildProperties);
    }

    @Test
    void shouldReturnOpenAPIObjectWithCorrectInfo() {
        OpenAPI api = openApiConfig.api();
        assertThat(api).isNotNull().
                extracting(OpenAPI::getInfo).isNotNull().
                extracting(
                        Info::getVersion,
                        Info::getTitle
                )
                .containsExactly(buildProperties.getVersion(), OpenApiConfig.TITLE);
    }
}
