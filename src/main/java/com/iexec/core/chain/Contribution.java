package com.iexec.core.chain;

import com.iexec.common.utils.BytesUtils;
import lombok.*;
import org.web3j.tuples.generated.Tuple6;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
public class Contribution {

    private ContributionStatus status;
    private String resultHash;
    private String resultSeal;
    private String enclaveChallenge;
    private int score;
    private int weight;

    public Contribution(BigInteger status, byte[] resultHash, byte[] resultSeal, String enclaveChallenge, BigInteger score, BigInteger weight) {
        this.setStatus(status);
        this.setResultHash(resultHash);
        this.setResultSeal(resultSeal);
        this.setEnclaveChallenge(enclaveChallenge);
        this.setScore(score);
        this.setWeight(weight);
    }

    public static Contribution tuple2Contribution(Tuple6<BigInteger, byte[], byte[], String, BigInteger, BigInteger> contribution) {
        if (contribution != null) {
            return new Contribution(contribution.getValue1(),
                    contribution.getValue2(),
                    contribution.getValue3(),
                    contribution.getValue4(),
                    contribution.getValue5(),
                    contribution.getValue6());
        }
        return null;
    }

    public void setStatus(BigInteger status) {
        this.status = ContributionStatus.getValue(status);
    }

    public void setResultHash(byte[] resultHash) {
        this.resultHash = BytesUtils.bytesToString(resultHash);
    }

    public void setResultSeal(byte[] resultSeal) {
        this.resultSeal = BytesUtils.bytesToString(resultSeal);
    }

    public void setScore(BigInteger score) {
        this.score = score.intValue();
    }

    public void setWeight(BigInteger weight) {
        this.weight = weight.intValue();
    }

    public void setStatus(ContributionStatus status) {
        this.status = status;
    }

    public void setResultHash(String resultHash) {
        this.resultHash = resultHash;
    }

    public void setResultSeal(String resultSeal) {
        this.resultSeal = resultSeal;
    }

    public void setEnclaveChallenge(String enclaveChallenge) {
        this.enclaveChallenge = enclaveChallenge;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
