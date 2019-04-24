package com.iexec.core.result.repo.ipfs;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class IpfsServiceTest {

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldBeIpfsHash() {
        String hash = "QmfZ88JXmx2FJsAxT4ZsJBVhBUXdPoRbDZhbkSS1WsMbUA";
        assertThat(IpfsService.isIpfsHash(hash)).isTrue();
    }

    @Test
    public void shouldBeIpfsHashSinceWrongLength() {
        String hash = "QmfZ88JXmx2FJsAxT4ZsJBVhBUXdPoRbDZhbkSS1WsMbU";
        assertThat(IpfsService.isIpfsHash(hash)).isFalse();
    }

    @Test
    public void shouldBeIpfsHashSinceNotIpfsHash() {
        String hash = "abcd";
        assertThat(IpfsService.isIpfsHash(hash)).isFalse();
    }

    @Test
    public void shouldBeIpfsHashSinceNotIpfsEmpty() {
        String hash = "";
        assertThat(IpfsService.isIpfsHash(hash)).isFalse();
    }


}