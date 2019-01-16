package com.iexec.core.result;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.result.eip712.Domain;
import com.iexec.core.result.eip712.Eip712Challenge;
import com.iexec.core.result.eip712.Message;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class Eip712ChallengeServiceTest {


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldBla() throws IOException {
        Domain domain = Domain.builder()
                .name("iExec Result Repository")
                .version("1")
                .chainId(17L)
                .build();

        Message message = Message.builder()
                .challenge("0x10ff103511e3e233033628dbd641136d4670c16c33a4ce11950ab316ef18bce9")
                .build();

        Eip712Challenge eip712Challenge = Eip712Challenge.builder()
                .domain(domain)
                .message(message)
                .build();


        String eip712ChallengeString = Eip712ChallengeService.convertEip712ChallengeToString(eip712Challenge);

        //assertThat(eip712ChallengeString).isEqualTo("e6f3628b5a01f855f3e258bf84462fc48b57d0603060b84c495bb44ae2e01318");


        /*
        ECKeyPair ecKeyPair = ECKeyPair.create(BytesUtils.stringToBytes("0x2fac4d263f1b20bfc33ea2bcb1cbe1521322dbde81d04b0c454ffff1218f0ed6"));
        Sign.SignatureData sign = Sign.signMessage(BytesUtils.stringToBytes(eip712ChallengeString), ecKeyPair);
        System.out.println(BytesUtils.bytesToString(sign.getR()));
        System.out.println(BytesUtils.bytesToString(sign.getS()));
        */
    }


}