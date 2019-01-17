package com.iexec.core.result.eip712;

import lombok.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Eip712AuthenticationModel {

    private Eip712Challenge eip712Challenge;
    private String eip712ChallengeSignature;
    private String walletAddress;

}


