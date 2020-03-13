package com.iexec.core.result.repo.proxy;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
@NoArgsConstructor
public class Jwt {

    @Id
    private String id;

    @Version
    private Long version;

    private String walletAddress;
    private String jwtString;

    public Jwt(String walletAddress, String jwtString) {
        this.walletAddress = walletAddress;
        this.jwtString = jwtString;
    }
}
