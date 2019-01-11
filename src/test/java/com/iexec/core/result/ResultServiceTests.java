package com.iexec.core.result;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;

public class ResultServiceTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String STUB_FILENAME_PREFIX = "iexec-result-";
    private static final String STUB_FILENAME = STUB_FILENAME_PREFIX + CHAIN_TASK_ID;


    @Mock
    private GridFsOperations gridFsOperations;

    @InjectMocks
    private ResultService resultService;

    @Before
    public void init() { MockitoAnnotations.initMocks(this); }

    @Test
    public void shouldAddResult() {
        Result result = Result.builder().chainTaskId(CHAIN_TASK_ID).build();
        String data = "data";
        byte[] dataBytes = data.getBytes();

        String filename = resultService.addResult(result, dataBytes);

        assertThat(filename).isEqualTo(STUB_FILENAME);
        Mockito.verify(gridFsOperations, Mockito.times(1))
            .store(any(InputStream.class), Mockito.eq(filename), Mockito.eq(result));
    }

    @Test
    public void shouldNotAddResultSinceChainTaskIdIsNull() {
        Result result = Result.builder().build();

        String data = "data";
        byte[] dataBytes = data.getBytes();

        String filename = resultService.addResult(result, dataBytes);

        assertThat(filename).isEmpty();
        Mockito.verify(gridFsOperations, Mockito.times(0))
            .store(any(InputStream.class), Mockito.eq(filename), Mockito.eq(result));
    }

    @Test
    public void shouldGetResultByChainTaskId() throws IOException {
        GridFsResource resource = mock(GridFsResource.class);
        InputStream inputStream = IOUtils.toInputStream("stream", "UTF-8");
        byte[] inputStreamBytes = "stream".getBytes();

        when(gridFsOperations.getResources(STUB_FILENAME))
            .thenReturn(new GridFsResource[] {resource});
        when(resource.getInputStream()).thenReturn(inputStream);

        byte[] result = resultService.getResultByChainTaskId(CHAIN_TASK_ID);
        assertThat(result).isEqualTo(inputStreamBytes);
    }

    @Test
    public void shouldGetEmptyArraySinceNoResultWithChainTaskId() {
        when(gridFsOperations.getResources(STUB_FILENAME)).thenReturn(new GridFsResource[0]);

        try {
            byte[] result = resultService.getResultByChainTaskId(CHAIN_TASK_ID);
            assertThat(result).isEmpty();
        } catch (Exception e) {
            System.out.println(e);
        }
    }
} 