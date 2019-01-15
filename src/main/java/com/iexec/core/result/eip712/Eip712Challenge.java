package com.iexec.core.result.eip712;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.io.IOException;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Eip712Challenge {


    private static final String TYPES = "{\n" +
            "        \"EIP712Domain\": [\n" +
            "            {\n" +
            "                \"name\": \"name\",\n" +
            "                \"type\": \"string\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"version\",\n" +
            "                \"type\": \"string\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"chainId\",\n" +
            "                \"type\": \"uint256\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"Challenge\": [\n" +
            "            {\n" +
            "                \"name\": \"challenge\",\n" +
            "                \"type\": \"string\"\n" +
            "            }\n" +
            "        ]\n" +
            "    }";
    private static final String PRIMARY_TYPE = "Challenge";


    public Object types;
    public Domain domain;
    public String primaryType;
    public Message message;

    public Object getTypes() {
        try {
            return new ObjectMapper().readValue(TYPES, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getPrimaryType() {
        return PRIMARY_TYPE;
    }
}


