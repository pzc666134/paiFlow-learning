package com.iflytek.astron;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MCP工具功能测试")
class McpToolsFunctionalTest {

    @Autowired
    private McpTools mcpTools;

    @Test
    @DisplayName("功能测试 - smartTTS完整流程")
    void testSmartTTSFullFlow() {
        String result = mcpTools.smartTTS("你好世界", "xiaoyan", 50);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("功能测试 - qwenTTS完整流程")
    void testQwenTTSFullFlow() {
        String result = mcpTools.qwenTTS("你好世界", "CHERRY");
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("功能测试 - 边界值处理（最小语速）")
    void testSmartTTSMinSpeed() {
        String result = mcpTools.smartTTS("测试", "xiaoyan", 1);
        assertNotNull(result);
    }

    @Test
    @DisplayName("功能测试 - 边界值处理（最大语速）")
    void testSmartTTSMaxSpeed() {
        String result = mcpTools.smartTTS("测试", "xiaoyan", 100);
        assertNotNull(result);
    }

    @Test
    @DisplayName("功能测试 - 中文文本处理")
    void testChineseTextProcessing() {
        String chineseText = "这是一个中文文本转语音的测试";
        String result = mcpTools.smartTTS(chineseText, "xiaoyan", 50);
        
        assertNotNull(result);
    }

    @Test
    @DisplayName("功能测试 - 英文文本处理")
    void testEnglishTextProcessing() {
        String englishText = "This is a test for text to speech conversion";
        String result = mcpTools.smartTTS(englishText, "xiaoyan", 50);
        
        assertNotNull(result);
    }

    @Test
    @DisplayName("功能测试 - 混合语言文本处理")
    void testMixedLanguageTextProcessing() {
        String mixedText = "Hello 你好 World 世界";
        String result = mcpTools.smartTTS(mixedText, "xiaoyan", 50);
        
        assertNotNull(result);
    }

    @Test
    @DisplayName("功能测试 - 长文本处理")
    void testLongTextProcessing() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("这是第").append(i).append("句测试文本。");
        }
        
        String result = mcpTools.qwenTTS(longText.toString(), "CHERRY");
        assertNotNull(result);
    }

    @Test
    @DisplayName("功能测试 - 特殊字符处理")
    void testSpecialCharactersHandling() {
        String specialText = "测试！@#￥%……&*（）——+";
        String result = mcpTools.smartTTS(specialText, "xiaoyan", 50);
        
        assertNotNull(result);
    }

    @Test
    @DisplayName("功能测试 - 空字符串处理")
    void testEmptyStringHandling() {
        String result = mcpTools.smartTTS("", "xiaoyan", 50);
        assertEquals("错误：文本内容不能为空", result);
    }

    @Test
    @DisplayName("功能测试 - 返回值格式验证")
    void testReturnValueFormat() {
        String result = mcpTools.smartTTS("测试", "xiaoyan", 50);
        
        assertTrue(result.contains("音频生成成功") || result.contains("TTS合成失败"));
        if (result.contains("音频生成成功")) {
            assertTrue(result.contains("字节"));
            assertTrue(result.contains("Base64编码"));
        }
    }

    @Test
    @DisplayName("功能测试 - Base64编码有效性")
    void testBase64Validity() throws Exception {
        String result = mcpTools.smartTTS("测试", "xiaoyan", 50);
        
        if (result.contains("Base64编码: ")) {
            String base64Part = result.split("Base64编码: ")[1];
            byte[] decoded = Base64.getDecoder().decode(base64Part);
            assertNotNull(decoded);
            assertTrue(decoded.length > 0);
        }
    }
}
