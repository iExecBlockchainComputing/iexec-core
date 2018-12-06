package com.iexec.core.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
class Configuration {

    @Id
    private String id;

    @Version
    private Long version;

    private BigInteger lastSeenBlockWithDeal;

    private BigInteger fromReplay;


}

