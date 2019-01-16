package com.iexec.core.result;

import com.iexec.core.result.eip712.Eip712Challenge;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class Eip712ChallengeServiceTest {

    @InjectMocks
    private Eip712ChallengeService eip712ChallengeService;

    private Eip712Challenge eip712Challenge;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        eip712Challenge = new Eip712Challenge("0x10ff103511e3e233033628dbd641136d4670c16c33a4ce11950ab316ef18bce9", 17);
    }

    @Test
    public void shouldGetCorrectDomainSeparator() {
        assertThat(eip712ChallengeService.getDomainSeparator(eip712Challenge))
                .isEqualTo("0x73b2ffe0e9f80f155eba2ca6ad915b2bd92e0d89c354112dc63b6dd70c30f51e");
    }

    @Test
    public void shouldGetCorrectMessageHash() {
        assertThat(eip712ChallengeService.getMessageHash(eip712Challenge))
                .isEqualTo("0x2ff97da3e19fd11436479ffcec54baa5501c439d8a65f01dec5a217f7bf4bc70");
    }

    @Test
    public void shouldGetCorrectChallengeHashToSign() {
        assertThat(eip712ChallengeService.getEip712ChallengeString(eip712Challenge))
                .isEqualTo("0x3bb958b947bc47479a7ee767d74a45146e41ac703d989d72f2b9c8f1aaf00a13");
    }

}