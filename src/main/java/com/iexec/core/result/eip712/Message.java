package com.iexec.core.result.eip712;


import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Message {

    private String challenge;
}
