package com.iexec.core.chain;

import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.utils.BytesUtils;
import lombok.*;

import java.math.BigInteger;

@Data
@Getter
@NoArgsConstructor
@Builder
public class DealEvent {

    private String chainDealId;
    private BigInteger blockNumber;

    public DealEvent(String chainDealId, BigInteger blockNumber) {
        this.chainDealId = chainDealId;
        this.blockNumber = blockNumber;
    }

    public DealEvent(IexecClerkABILegacy.SchedulerNoticeEventResponse schedulerNoticeEventResponse) {
        this.chainDealId = BytesUtils.bytesToString(schedulerNoticeEventResponse.dealid);
        this.blockNumber = schedulerNoticeEventResponse.log.getBlockNumber();
    }

}
