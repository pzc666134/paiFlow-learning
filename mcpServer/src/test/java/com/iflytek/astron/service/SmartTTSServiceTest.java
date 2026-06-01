package com.iflytek.astron.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SmartTTSService 单元测试")
class SmartTTSServiceTest {

    private SmartTTSService smartTTSService;

    @BeforeEach
    void setUp() {
        smartTTSService = new SmartTTSService();
        ReflectionTestUtils.setField(smartTTSService, "appId", "test-app-id");
        ReflectionTestUtils.setField(smartTTSService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(smartTTSService, "apiSecret", "test-api-secret");
        ReflectionTestUtils.setField(smartTTSService, "ttsUrl", "https://tts-api.xfyun.cn/v2/tts");
    }

    @Test
    @DisplayName("测试参数验证 - 文本为空")
    void testSynthesizeWithEmptyText() {
        assertThrows(IllegalArgumentException.class, () -> {
            smartTTSService.synthesize("", "xiaoyan", 50);
        });
    }

    @Test
    @DisplayName("测试参数验证 - 文本为null")
    void testSynthesizeWithNullText() {
        assertThrows(IllegalArgumentException.class, () -> {
            smartTTSService.synthesize(null, "xiaoyan", 50);
        });
    }

    @Test
    @DisplayName("测试参数验证 - 音色为空")
    void testSynthesizeWithEmptyVcn() {
        assertThrows(IllegalArgumentException.class, () -> {
            smartTTSService.synthesize("测试文本", "", 50);
        });
    }

    @Test
    @DisplayName("测试参数验证 - 音色为null")
    void testSynthesizeWithNullVcn() {
        assertThrows(IllegalArgumentException.class, () -> {
            smartTTSService.synthesize("测试文本", null, 50);
        });
    }

    @Test
    @DisplayName("测试配置注入")
    void testConfigurationInjection() {
        assertNotNull(ReflectionTestUtils.getField(smartTTSService, "appId"));
        assertNotNull(ReflectionTestUtils.getField(smartTTSService, "apiKey"));
        assertNotNull(ReflectionTestUtils.getField(smartTTSService, "apiSecret"));
        assertNotNull(ReflectionTestUtils.getField(smartTTSService, "ttsUrl"));
        
        assertEquals("test-app-id", ReflectionTestUtils.getField(smartTTSService, "appId"));
        assertEquals("test-api-key", ReflectionTestUtils.getField(smartTTSService, "apiKey"));
    }
}
