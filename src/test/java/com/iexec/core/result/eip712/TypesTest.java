package com.iexec.core.result.eip712;

import org.junit.Test;

import static org.assertj.core.api.Java6Assertions.assertThat;


public class TypesTest {

    @Test
    public void typeParamsToString() {
        Eip712Challenge eip712Challenge = new Eip712Challenge("abcd", 1);
        assertThat(Types.typeParamsToString(eip712Challenge.getTypes().getDomainTypeParams())).isEqualTo("string name,string version,uint256 chainId");
    }
}