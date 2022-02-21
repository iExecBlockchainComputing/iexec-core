package com.iexec.core.utils.version;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.info.BuildProperties;

public class VersionServiceTest {

    @Mock
    private BuildProperties buildProperties;

    @InjectMocks
    private VersionService versionService;

    @BeforeEach
    public void preflight() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest
    @ValueSource(strings={"x.y.z", "x.y.z-rc"})
    void testNonSnapshotVersion(String version) {
        Mockito.when(buildProperties.getVersion()).thenReturn(version);
        Assertions.assertEquals(version, versionService.getVersion());
        Assertions.assertFalse(versionService.isSnapshot());
    }

    @Test
    void testSnapshotVersion() {
        Mockito.when(buildProperties.getVersion()).thenReturn("x.y.z-NEXT-SNAPSHOT");
        Assertions.assertEquals("x.y.z-NEXT-SNAPSHOT", versionService.getVersion());
        Assertions.assertTrue(versionService.isSnapshot());
    }

}
