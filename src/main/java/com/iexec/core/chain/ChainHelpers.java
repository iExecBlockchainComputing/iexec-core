package com.iexec.core.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.contract.generated.Dapp;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import org.web3j.tuples.generated.Tuple6;
import org.web3j.tuples.generated.Tuple9;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;

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

    static ArrayList<String> getChainDealParams(ChainDeal chainDeal) throws IOException {
        LinkedHashMap tasksParamsMap = new ObjectMapper().readValue(chainDeal.getParams(), LinkedHashMap.class);
        return new ArrayList<String>(tasksParamsMap.values());
    }

    static String getDappName(Dapp dapp) throws Exception {
        return dapp.m_dappName().send();
    }

    static String getDockerImage(Dapp dapp) throws Exception {
        // deserialize the dapp params json into POJO
        String jsonDappParams = dapp.m_dappParams().send();
        ChainDappParams dappParams = new ObjectMapper().readValue(jsonDappParams, ChainDappParams.class);
        return dappParams.getUri();
    }
}
