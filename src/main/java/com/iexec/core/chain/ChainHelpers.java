package com.iexec.core.chain;

import com.iexec.common.contract.generated.IexecClerkABILegacy;
import org.web3j.tuples.generated.Tuple6;
import org.web3j.tuples.generated.Tuple9;

import java.math.BigInteger;

class ChainHelpers {

    private ChainHelpers(){
        throw new UnsupportedOperationException();
    }

    static ChainDeal getChainDeal(IexecClerkABILegacy iexecClerk, byte[] dealId) throws Exception {
        Tuple9<String, String, BigInteger, String, String, BigInteger, String, String, BigInteger> dealPt1 = iexecClerk.viewDealABILegacy_pt1(dealId).send();
        Tuple6<BigInteger, BigInteger, String, String, String, String> dealPt2 = iexecClerk.viewDealABILegacy_pt2(dealId).send();
        Tuple6<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger> config = iexecClerk.viewConfigABILegacy(dealId).send();

        return ChainDeal.builder()
                .dappPointer(dealPt1.getValue1())
                .dappOwner(dealPt1.getValue2())
                .dappPrice(dealPt1.getValue3())
                .dataPointer(dealPt1.getValue4())
                .dataOwner(dealPt1.getValue5())
                .dataPrice(dealPt1.getValue6())
                .poolPointer(dealPt1.getValue7())
                .poolOwner(dealPt1.getValue8())
                .poolPrice(dealPt1.getValue9())
                .trust(dealPt2.getValue1())
                .tag(dealPt2.getValue2())
                .requester(dealPt2.getValue3())
                .beneficiary(dealPt2.getValue4())
                .callback(dealPt2.getValue5())
                .params(dealPt2.getValue6())
                .category(config.getValue1())
                .startTime(config.getValue2())
                .botFirst(config.getValue3())
                .botSize(config.getValue4())
                .workerStake(config.getValue5())
                .schedulerRewardRatio(config.getValue6())
                .build();
    }
}
