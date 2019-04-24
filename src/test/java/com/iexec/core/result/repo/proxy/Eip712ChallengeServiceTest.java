package com.iexec.core.result.repo.proxy;

import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.result.repo.proxy.Eip712ChallengeService;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class Eip712ChallengeServiceTest {

    @InjectMocks
    private Eip712ChallengeService eip712ChallengeService;

    private Eip712Challenge eip712Challenge;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }
}