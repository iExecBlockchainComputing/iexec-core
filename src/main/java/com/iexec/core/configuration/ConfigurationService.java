package com.iexec.core.configuration;

import com.iexec.core.chain.ChainConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Slf4j
@Service
public class ConfigurationService {

    private ConfigurationRepository configurationRepository;
    private ChainConfig chainConfig;

    public ConfigurationService(ConfigurationRepository configurationRepository,
                                ChainConfig chainConfig) {
        this.configurationRepository = configurationRepository;
        this.chainConfig = chainConfig;
    }

    private Configuration getConfiguration() {
        if (configurationRepository.count() > 0)
            return configurationRepository.findAll().get(0);

        return configurationRepository.save(
            Configuration
                    .builder()
                    .lastSeenBlockWithDeal(BigInteger.ZERO)
                    .fromReplay(BigInteger.ZERO)
                    .build());
    }

    public BigInteger getLastSeenBlockWithDeal() {
        return this.getConfiguration().getLastSeenBlockWithDeal();
    }

    public void setLastSeenBlockWithDeal(BigInteger lastBlockNumber) {
        Configuration configuration = this.getConfiguration();
        configuration.setLastSeenBlockWithDeal(lastBlockNumber);
        configurationRepository.save(configuration);
    }

    public BigInteger getFromReplay() {
        return this.getConfiguration().getFromReplay();
    }

    public void setFromReplay(BigInteger fromReplay) {
        Configuration configuration = this.getConfiguration();
        configuration.setFromReplay(fromReplay);
        configurationRepository.save(configuration);
    }

}
