package com.iexec.core.result.repo.mongo;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.result.repo.proxy.Result;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class MongoResultServiceTest {

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private GridFsOperations gridFsOperations;

    @Mock
    private ResultRepositoryConfiguration resultRepositoryConfig;

    @InjectMocks
    private MongoResultService mongoResultService;

    private String chainTaskId;
    private String resultFilename;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        chainTaskId = "0x1";
        resultFilename = "iexec-result-" + chainTaskId;
    }

    @Test
    public void shouldNotFindResultInDatabase() {
        when(gridFsOperations.findOne(any())).thenReturn(null);

        assertThat(mongoResultService.doesResultExist(chainTaskId)).isFalse();
    }

    @Test
    public void shouldFindResultInDatabase() {
        GridFSFile gridFSFileMock = Mockito.mock(GridFSFile.class);

        when(gridFsOperations.findOne(any())).thenReturn(gridFSFileMock);

        assertThat(mongoResultService.doesResultExist(chainTaskId)).isTrue();
    }

    @Test
    public void shouldNotAddResultSinceResultIsNull() {
        String data = "data";
        byte[] dataBytes = data.getBytes();

         String filename = mongoResultService.addResult(null, dataBytes);
         assertThat(filename).isEmpty();
        Mockito.verify(gridFsOperations, Mockito.times(0))
                .store(any(InputStream.class), Mockito.eq(filename), any(Result.class));
    }

    @Test
    public void shouldNotAddResultSinceChainTaskIdIsNull() {
        Result result = Result.builder().build();

        String data = "data";
        byte[] dataBytes = data.getBytes();

         String filename = mongoResultService.addResult(result, dataBytes);

         assertThat(filename).isEmpty();
        Mockito.verify(gridFsOperations, Mockito.times(0))
                .store(any(InputStream.class), Mockito.eq(filename), Mockito.eq(result));
    }

    @Test
    public void shouldAddResult() {
        Result result = Result.builder().chainTaskId(chainTaskId).build();
        String data = "data";
        byte[] dataBytes = data.getBytes();

        when(iexecHubService.getChainTask(any())).thenReturn(Optional.of(new ChainTask()));
        ChainDeal chainDeal = ChainDeal.builder().beneficiary("beneficiary").build();
        when(iexecHubService.getChainDeal(any())).thenReturn(Optional.of(chainDeal));

        when(resultRepositoryConfig.getResultRepositoryURL()).thenReturn("dummyPath");
        String resultLink = mongoResultService.addResult(result, dataBytes);

        assertThat(resultLink).isEqualTo("dummyPath/results/0x1");
        Mockito.verify(gridFsOperations, Mockito.times(1))
            .store(any(), any(), Mockito.eq(result));
    }

    @Test
    public void shouldGetResultByChainTaskId() throws IOException {
        GridFsResource resource = Mockito.mock(GridFsResource.class);
        InputStream inputStream = IOUtils.toInputStream("stream", "UTF-8");
        byte[] inputStreamBytes = "stream".getBytes();

         when(gridFsOperations.getResources(resultFilename))
            .thenReturn(new GridFsResource[] {resource});
        when(resource.getInputStream()).thenReturn(inputStream);

        Optional<byte[]> result = mongoResultService.getResult(chainTaskId);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(inputStreamBytes);
    }

    @Test
    public void shouldGetEmptyArraySinceNoResultWithChainTaskId() {
        when(gridFsOperations.getResources(resultFilename)).thenReturn(new GridFsResource[0]);

        Optional<byte[]> result = mongoResultService.getResult(chainTaskId);
        assertThat(result.isPresent()).isFalse();
    }

}