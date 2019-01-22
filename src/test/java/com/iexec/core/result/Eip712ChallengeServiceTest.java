package com.iexec.core.result;

import com.iexec.common.result.eip712.Eip712Challenge;
import org.junit.Before;
import org.junit.Test;
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