package com.iexec.core.result.eip712;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Types {

    @JsonProperty("EIP712Domain")
    private List<TypeParam> domainTypeParams = null;
    @JsonProperty("Challenge")
    private List<TypeParam> challengeTypeParams = null;

    public static String typeParamsToString(List<TypeParam> typeParams) {
        StringBuilder s = new StringBuilder();
        for (TypeParam typeParam : typeParams) {
            s.append(typeParam.getType()).append(" ").append(typeParam.getName());
            if (typeParams.indexOf(typeParam) <= typeParams.size() - 2) {
                s.append(",");
            }
        }
        return s.toString();
    }

    @JsonIgnore
    public List<TypeParam> getDomainTypeParams() {
        return domainTypeParams;
    }

    @JsonIgnore
    public List<TypeParam> getChallengeTypeParams() {
        return challengeTypeParams;
    }
}
