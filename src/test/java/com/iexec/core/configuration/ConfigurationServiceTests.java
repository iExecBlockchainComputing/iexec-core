package com.iexec.core.configuration;

import com.iexec.core.chain.ChainConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class ConfigurationServiceTests {

    @Mock
    private ConfigurationRepository configurationRepository;

    @Mock
    private ChainConfig chainConfig;

    @InjectMocks
    private ConfigurationService configurationService;

    @Before
    public void init() { MockitoAnnotations.initMocks(this); }

    @Test
    public void shouldGetLastSeenBlockWithDealFromDatabase() {
        Configuration configuration = Configuration.builder()
            .lastSeenBlockWithDeal(BigInteger.TEN)
            .build();
        List<Configuration> configurationList = Collections.singletonList(configuration);

        when(configurationRepository.count()).thenReturn((long) 1);
        when(configurationRepository.findAll()).thenReturn(configurationList);

        BigInteger lastSeenBlock = configurationService.getLastSeenBlockWithDeal();

        assertThat(lastSeenBlock).isEqualTo(BigInteger.TEN);
    }

    @Test
    public void shouldGetZeroAsLastSeenBlockWithDeal() {
        Configuration configuration = Configuration.builder()
            .lastSeenBlockWithDeal(BigInteger.ZERO)
            .build();

        when(configurationRepository.count()).thenReturn((long) 0);
        when(configurationRepository.save(any())).thenReturn(configuration);
        when(chainConfig.getStartBlockNumber()).thenReturn(0L);

        BigInteger lastSeenBlock = configurationService.getLastSeenBlockWithDeal();

        assertThat(lastSeenBlock).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void shouldSetLastSeenBlockWithDeal() {
        Configuration configuration = Configuration.builder()
            .lastSeenBlockWithDeal(BigInteger.ONE)
            .build();
        List<Configuration> configurationList = Collections.singletonList(configuration);

        when(configurationRepository.count()).thenReturn((long) 1);
        when(configurationRepository.findAll()).thenReturn(configurationList);
        when(configurationRepository.save(any())).thenReturn(configuration);

        configurationService.setLastSeenBlockWithDeal(BigInteger.TEN);;

        assertThat(configuration.getLastSeenBlockWithDeal()).isEqualTo(BigInteger.TEN);
    }

    @Test
    public void shouldGetFromReplayFromDatabase() {
        Configuration configuration = Configuration.builder()
            .fromReplay(BigInteger.TEN)
            .build();
        List<Configuration> configurationList = Collections.singletonList(configuration);

        when(configurationRepository.count()).thenReturn((long) 1);
        when(configurationRepository.findAll()).thenReturn(configurationList);

        BigInteger fromReplay = configurationService.getFromReplay();

        assertThat(fromReplay).isEqualTo(BigInteger.TEN);
    }

    @Test
    public void shouldGetZeroAsFromReplay() {
        Configuration configuration = Configuration.builder()
            .fromReplay(BigInteger.ZERO)
            .build();

        when(configurationRepository.count()).thenReturn((long) 0);
        when(configurationRepository.save(any())).thenReturn(configuration);
        when(chainConfig.getStartBlockNumber()).thenReturn(0L);

        BigInteger fromReplay = configurationService.getFromReplay();

        assertThat(fromReplay).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void shouldSetFromReplay() {
        Configuration configuration = Configuration.builder()
            .fromReplay(BigInteger.ONE)
            .build();
        List<Configuration> configurationList = Collections.singletonList(configuration);

        when(configurationRepository.count()).thenReturn((long) 1);
        when(configurationRepository.findAll()).thenReturn(configurationList);
        when(configurationRepository.save(any())).thenReturn(configuration);

        configurationService.setFromReplay(BigInteger.TEN);;

        assertThat(configuration.getFromReplay()).isEqualTo(BigInteger.TEN);
    }

}