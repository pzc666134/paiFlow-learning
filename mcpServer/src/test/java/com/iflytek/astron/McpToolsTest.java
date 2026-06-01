package com.iflytek.astron;


import com.iflytek.astron.service.QwenTTSService;
import com.iflytek.astron.service.SmartTTSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("McpTools 集成测试")
class McpToolsTest {

    @Mock
    private SmartTTSService smartTTSService;

    @Mock
    private QwenTTSService qwenTTSService;

    @InjectMocks
    private McpTools mcpTools;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("测试smartTTS - 正常调用")
    void testSmartTTSNormal() throws Exception {
        byte[] mockAudioData = "mock audio data".getBytes();
        when(smartTTSService.synthesize(anyString(), anyString(), anyInt())).thenReturn(mockAudioData);

        String result = mcpTools.smartTTS("测试文本", "xiaoyan", 50);

        assertNotNull(result);
        assertTrue(result.contains("音频生成成功"));
        assertTrue(result.contains("字节"));
        assertTrue(result.contains("Base64编码"));
        
        verify(smartTTSService, times(1)).synthesize("测试文本", "xiaoyan", 50);
    }

    @Test
    @DisplayName("测试smartTTS - 默认语速")
    void testSmartTTSDefaultSpeed() throws Exception {
        byte[] mockAudioData = "mock audio data".getBytes();
        when(smartTTSService.synthesize(anyString(), anyString(), anyInt())).thenReturn(mockAudioData);

        String result = mcpTools.smartTTS("测试文本", "xiaoyan", null);

        assertNotNull(result);
        verify(smartTTSService, times(1)).synthesize("测试文本", "xiaoyan", 50);
    }

    @Test
    @DisplayName("测试smartTTS - 文本为空")
    void testSmartTTSEmptyText() throws Exception {
        String result = mcpTools.smartTTS("", "xiaoyan", 50);
        
        assertEquals("错误：文本内容不能为空", result);
        verify(smartTTSService, never()).synthesize(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("测试smartTTS - 文本为null")
    void testSmartTTSNullText() throws Exception {
        String result = mcpTools.smartTTS(null, "xiaoyan", 50);
        
        assertEquals("错误：文本内容不能为空", result);
        verify(smartTTSService, never()).synthesize(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("测试smartTTS - 音色为空")
    void testSmartTTSEmptyVcn() throws Exception {
        String result = mcpTools.smartTTS("测试文本", "", 50);
        
        assertEquals("错误：音色名称不能为空", result);
        verify(smartTTSService, never()).synthesize(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("测试smartTTS - 音色为null")
    void testSmartTTSNullVcn() throws Exception {
        String result = mcpTools.smartTTS("测试文本", null, 50);
        
        assertEquals("错误：音色名称不能为空", result);
        verify(smartTTSService, never()).synthesize(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("测试smartTTS - 服务异常")
    void testSmartTTSServiceException() throws Exception  {
        when(smartTTSService.synthesize(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("TTS服务不可用"));

        String result = mcpTools.smartTTS("测试文本", "xiaoyan", 50);
        
        assertTrue(result.contains("TTS合成失败"));
        assertTrue(result.contains("TTS服务不可用"));
    }

    @Test
    @DisplayName("测试qwenTTS - 正常调用")
    void testQwenTTSNormal() throws Exception {
        byte[] mockAudioData = "mock audio data".getBytes();
        when(qwenTTSService.synthesize(anyString(), anyString())).thenReturn(mockAudioData);

        String result = mcpTools.qwenTTS("测试文本", "CHERRY");

        assertNotNull(result);
        assertTrue(result.contains("音频生成成功"));
        assertTrue(result.contains("字节"));
        assertTrue(result.contains("Base64编码"));
        
        verify(qwenTTSService, times(1)).synthesize("测试文本", "CHERRY");
    }

    @Test
    @DisplayName("测试qwenTTS - 文本为空")
    void testQwenTTSEmptyText() throws  Exception{
        String result = mcpTools.qwenTTS("", "CHERRY");
        
        assertEquals("错误：文本内容不能为空", result);
        verify(qwenTTSService, never()).synthesize(anyString(), anyString());
    }

    @Test
    @DisplayName("测试qwenTTS - 文本为null")
    void testQwenTTSNullText() throws Exception {
        String result = mcpTools.qwenTTS(null, "CHERRY");
        
        assertEquals("错误：文本内容不能为空", result);
        verify(qwenTTSService, never()).synthesize(anyString(), anyString());
    }

    @Test
    @DisplayName("测试qwenTTS - 音色为空")
    void testQwenTTSEmptyVcn() throws  Exception{
        String result = mcpTools.qwenTTS("测试文本", "");
        
        assertEquals("错误：音色名称不能为空", result);
        verify(qwenTTSService, never()).synthesize(anyString(), anyString());
    }

    @Test
    @DisplayName("测试qwenTTS - 音色为null")
    void testQwenTTSNullVcn() throws Exception {
        String result = mcpTools.qwenTTS("测试文本", null);
        
        assertEquals("错误：音色名称不能为空", result);
        verify(qwenTTSService, never()).synthesize(anyString(), anyString());
    }

    @Test
    @DisplayName("测试qwenTTS - 服务异常")
    void testQwenTTSServiceException() throws Exception {
        when(qwenTTSService.synthesize(anyString(), anyString()))
                .thenThrow(new RuntimeException("TTS服务不可用"));

        String result = mcpTools.qwenTTS("测试文本", "CHERRY");
        
        assertTrue(result.contains("TTS合成失败"));
        assertTrue(result.contains("TTS服务不可用"));
    }

    @Test
    @DisplayName("测试Base64编码正确性")
    void testBase64Encoding() throws Exception {
        byte[] originalData = "test audio content".getBytes();
        when(smartTTSService.synthesize(anyString(), anyString(), anyInt())).thenReturn(originalData);

        String result = mcpTools.smartTTS("test", "xiaoyan", 50);
        
        String expectedBase64 = Base64.getEncoder().encodeToString(originalData);
        assertTrue(result.contains(expectedBase64));
    }

    @Test
    @DisplayName("测试返回结果包含音频大小")
    void testResultContainsAudioSize() throws Exception {
        byte[] mockAudioData = new byte[1024];
        when(smartTTSService.synthesize(anyString(), anyString(), anyInt())).thenReturn(mockAudioData);

        String result = mcpTools.smartTTS("test", "xiaoyan", 50);
        
        assertTrue(result.contains("1024"));
    }
}
