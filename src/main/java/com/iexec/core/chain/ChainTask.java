package com.iexec.core.chain;

import com.iexec.common.utils.BytesUtils;
import lombok.*;
import org.web3j.tuples.generated.Tuple10;
import org.web3j.tuples.generated.Tuple6;

import java.math.BigInteger;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
public class ChainTask {

    private ChainTaskStatus status;
    private String dealid;
    private int idx;
    private int consensusDeadline;
    private String consensusValue;
    private int revealDeadline;
    private int revealCounter;
    private int winnerCounter;
    private List<String> contributors;
    private String results;


    public ChainTask(BigInteger status, byte[] dealid, BigInteger idx, BigInteger consensusDeadline, byte[] consensusValue, BigInteger revealDeadline, BigInteger revealCounter, BigInteger winnerCounter, List<String> contributors, byte[] results) {
        this.setStatus(status);
        this.setDealid(dealid);
        this.setIdx(idx);
        this.setConsensusDeadline(consensusDeadline);
        this.setConsensusValue(consensusValue);
        this.setRevealDeadline(revealDeadline);
        this.setRevealCounter(revealCounter);
        this.setWinnerCounter(winnerCounter);
        this.setContributors(contributors);
        this.setResults(results);

    }

    private void setStatus(BigInteger status) {
        this.status = ChainTaskStatus.getValue(status);
    }

    private void setDealid(byte[] dealid) {
        this.dealid = BytesUtils.bytesToString(dealid);
    }

    private void setIdx(BigInteger idx) {
        this.idx = idx.intValue();
    }

    private void setConsensusDeadline(BigInteger consensusDeadline) {
        this.consensusDeadline = consensusDeadline.intValue();
    }

    private void setConsensusValue(byte[] consensusValue) {
        this.consensusValue = BytesUtils.bytesToString(consensusValue);
    }

    private void setRevealDeadline(BigInteger revealDeadline) {
        this.revealDeadline = revealDeadline.intValue();
    }

    private void setRevealCounter(BigInteger revealCounter) {
        this.revealCounter = revealCounter.intValue();
    }

    private void setWinnerCounter(BigInteger winnerCounter) {
        this.winnerCounter = winnerCounter.intValue();
    }

    public void setContributors(List<String> contributors) {
        this.contributors = contributors;
    }

    private void setResults(byte[] results) {
        this.results = BytesUtils.bytesToString(results);
    }

    public static ChainTask tuple2ChainTask(Tuple10<BigInteger, byte[], BigInteger, BigInteger, byte[], BigInteger, BigInteger, BigInteger, List<String>, byte[]> chainTask) {
        if (chainTask != null) {
            return new ChainTask(chainTask.getValue1(),
                    chainTask.getValue2(),
                    chainTask.getValue3(),
                    chainTask.getValue4(),
                    chainTask.getValue5(),
                    chainTask.getValue6(),
                    chainTask.getValue7(),
                    chainTask.getValue8(),
                    chainTask.getValue9(),
                    chainTask.getValue10());
        }
        return null;
    }


}
