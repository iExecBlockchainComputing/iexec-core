package com.iexec.core.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.chain.ChainDeal;
import com.iexec.common.contract.generated.App;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

class ChainHelpers {

    private ChainHelpers() {
        throw new UnsupportedOperationException();
    }

    static ArrayList<String> getChainDealParams(ChainDeal chainDeal) throws IOException {
        LinkedHashMap tasksParamsMap = new ObjectMapper().readValue(chainDeal.getParams(), LinkedHashMap.class);
        return new ArrayList<String>(tasksParamsMap.values());
    }

    static String getChainAppName(App app) throws Exception {
        return app.m_appName().send();
    }

    static String getDockerImage(App app) throws Exception {
        // deserialize the app params json into POJO
        String jsonDappParams = app.m_appParams().send();
        ChainAppParams dappParams = new ObjectMapper().readValue(jsonDappParams, ChainAppParams.class);
        return dappParams.getUri();
    }
}
