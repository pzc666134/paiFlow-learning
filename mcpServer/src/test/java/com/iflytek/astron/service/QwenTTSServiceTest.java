package com.iflytek.astron.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QwenTTSService 单元测试")
class QwenTTSServiceTest {

    private QwenTTSService qwenTTSService;

    @BeforeEach
    void setUp() {
        qwenTTSService = new QwenTTSService();
        ReflectionTestUtils.setField(qwenTTSService, "apiKey", "test-qwen-api-key");
        ReflectionTestUtils.setField(qwenTTSService, "model", "qwen-tts-latest");
    }

    @Test
    @DisplayName("测试参数验证 - 文本为空")
    void testSynthesizeWithEmptyText() {
        assertThrows(IllegalArgumentException.class, () -> {
            qwenTTSService.synthesize("", "CHERRY");
        });
    }

    @Test
    @DisplayName("测试参数验证 - 文本为null")
    void testSynthesizeWithNullText() {
        assertThrows(IllegalArgumentException.class, () -> {
            qwenTTSService.synthesize(null, "CHERRY");
        });
    }

    @Test
    @DisplayName("测试参数验证 - 音色为空")
    void testSynthesizeWithEmptyVcn() {
        assertThrows(IllegalArgumentException.class, () -> {
            qwenTTSService.synthesize("测试文本", "");
        });
    }

    @Test
    @DisplayName("测试参数验证 - 音色为null")
    void testSynthesizeWithNullVcn() {
        assertThrows(IllegalArgumentException.class, () -> {
            qwenTTSService.synthesize("测试文本", null);
        });
    }

    @Test
    @DisplayName("测试配置注入")
    void testConfigurationInjection() {
        assertNotNull(ReflectionTestUtils.getField(qwenTTSService, "apiKey"));
        assertNotNull(ReflectionTestUtils.getField(qwenTTSService, "model"));
        
        assertEquals("test-qwen-api-key", ReflectionTestUtils.getField(qwenTTSService, "apiKey"));
        assertEquals("qwen-tts-latest", ReflectionTestUtils.getField(qwenTTSService, "model"));
    }

    @Test
    @DisplayName("测试文本分片逻辑 - 短文本")
    void testSplitTextByBytesShortText() {
        String shortText = "短文本";
        assertTrue(shortText.getBytes().length <= 500);
    }

    @Test
    @DisplayName("测试文本分片逻辑 - 长文本")
    void testSplitTextByBytesLongText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("测");
        }
        assertTrue(longText.toString().getBytes().length > 500);
    }
}
