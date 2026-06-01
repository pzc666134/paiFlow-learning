package com.iflytek.astron;



import com.iflytek.astron.service.QwenTTSService;
import com.iflytek.astron.service.SmartTTSService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Objects;

@Component
public class McpTools {

    @Autowired
    private SmartTTSService smartTTSService;

    @Autowired
    private QwenTTSService qwenTTSService;

      @McpTool(description = "使用讯飞星火智能TTS将文本转换为语音（MP3格式）")public String smartTTS(
            @McpToolParam(description = "要转换的文本内容") String text,
            @McpToolParam(description = "音色名称，如：xiaoyan, xiaofeng等") String vcn,
            @McpToolParam(description = "语速，范围1-100，默认50") Integer speed) {
        try {
            if (Objects.isNull(text) || text.isEmpty()) {
                return "错误：文本内容不能为空";
            }
            if (Objects.isNull(vcn) || vcn.isEmpty()) {
                return "错误：音色名称不能为空";
            }
            if (Objects.isNull(speed)) {
                speed = 50;
            }

            byte[] audioData = smartTTSService.synthesize(text, vcn, speed);
            
            String base64Audio = Base64.getEncoder().encodeToString(audioData);
            return "音频生成成功，大小: " + audioData.length + " 字节\nBase64编码: " + base64Audio;
        } catch (Exception e) {
            return "TTS合成失败: " + e.getMessage();
        }
    }

    @McpTool(description = "使用阿里千问TTS将文本转换为语音（WAV格式）")
    public String qwenTTS(
            @McpToolParam(description = "要转换的文本内容") String text,
            @McpToolParam(description = "音色名称，如：CHERRY, SERENA等") String vcn) {
        try {
            if (Objects.isNull(text) || text.isEmpty()) {
                return "错误：文本内容不能为空";
            }
            if (Objects.isNull(vcn) || vcn.isEmpty()) {
                return "错误：音色名称不能为空";
            }

            byte[] audioData = qwenTTSService.synthesize(text, vcn);
            
            String base64Audio = Base64.getEncoder().encodeToString(audioData);
            return "音频生成成功，大小: " + audioData.length + " 字节\nBase64编码: " + base64Audio;
        } catch (Exception e) {
            return "TTS合成失败: " + e.getMessage();
        }
    }
}
