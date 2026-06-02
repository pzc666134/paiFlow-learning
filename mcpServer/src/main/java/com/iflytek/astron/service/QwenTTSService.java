package com.iflytek.astron.service;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class QwenTTSService {
    private static final Logger log = LoggerFactory.getLogger(QwenTTSService.class);

    private static final int CHUNK_SIZE = 500;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Value("${qwen.api-key:}")
    private String apiKey;

    @Value("${qwen.model:qwen-tts-latest}")
    private String model;

    public byte[] synthesize(String text, String vcn) throws Exception {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text is required");
        }
        if (vcn == null || vcn.isEmpty()) {
            throw new IllegalArgumentException("Voice character (vcn) is required");
        }

        List<String> textChunks = splitTextByBytes(text, CHUNK_SIZE);
        log.info("Text split into {} chunks for TTS processing", textChunks.size());

        List<byte[]> audioChunks;
        if (textChunks.size() == 1) {
            audioChunks = List.of(synthesizeTextToAudio(textChunks.get(0), vcn));
        } else {
            audioChunks = synthesizeTextChunksAsync(textChunks, vcn);
        }

        byte[] mergedAudio = mergeAudioFiles(audioChunks);
        log.info("Merged {} audio chunks, total size: {} bytes", audioChunks.size(), mergedAudio.length);

        return mergedAudio;
    }

    private List<String> splitTextByBytes(String text, int maxBytes) {
        List<String> chunks = new ArrayList<>();
        byte[] bytes = text.getBytes();
        
        if (bytes.length <= maxBytes) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < bytes.length) {
            int end = Math.min(start + maxBytes, bytes.length);
            
            while (end > start && !isValidUtf8Boundary(bytes, end)) {
                end--;
            }
            
            String chunk = new String(bytes, start, end - start);
            chunks.add(chunk);
            start = end;
        }
        
        return chunks;
    }

    private boolean isValidUtf8Boundary(byte[] bytes, int position) {
        if (position >= bytes.length) return true;
        byte b = bytes[position];
        return (b & 0x80) == 0 || (b & 0xC0) == 0xC0;
    }

    private byte[] synthesizeTextToAudio(String text, String vcn) throws Exception {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .text(text)
                .voice(getVoice(vcn))
                .build();

        MultiModalConversationResult result = conv.call(param);
        String resUrl = result.getOutput().getAudio().getUrl();
        log.info("TTS generated URL: {}", resUrl);

        return downloadAudioFromUrl(resUrl);
    }

    private List<byte[]> synthesizeTextChunksAsync(List<String> textChunks, String vcn) throws Exception {
        List<byte[]> results = new ArrayList<>();
        
        for (int i = 0; i < textChunks.size(); i++) {
            try {
                log.info("Processing TTS chunk {}/{}", i + 1, textChunks.size());
                byte[] audioData = synthesizeTextToAudio(textChunks.get(i), vcn);
                results.add(audioData);
                
                if (i < textChunks.size() - 1) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.error("Failed to synthesize chunk {}: {}", i, e.getMessage());
                throw new RuntimeException("TTS chunk synthesis failed at chunk " + i, e);
            }
        }
        
        return results;
    }

    private byte[] mergeAudioFiles(List<byte[]> audioChunks) {
        if (audioChunks.size() == 1) {
            return audioChunks.get(0);
        }

        int totalSize = 0;
        int headerSize = 44;
        
        for (byte[] chunk : audioChunks) {
            totalSize += (chunk.length - headerSize);
        }

        byte[] firstChunk = audioChunks.get(0);
        byte[] merged = new byte[headerSize + totalSize];
        
        System.arraycopy(firstChunk, 0, merged, 0, headerSize);
        
        int offset = headerSize;
        for (byte[] chunk : audioChunks) {
            int dataSize = chunk.length - headerSize;
            System.arraycopy(chunk, headerSize, merged, offset, dataSize);
            offset += dataSize;
        }

        int fileSize = merged.length - 8;
        merged[4] = (byte) (fileSize & 0xFF);
        merged[5] = (byte) ((fileSize >> 8) & 0xFF);
        merged[6] = (byte) ((fileSize >> 16) & 0xFF);
        merged[7] = (byte) ((fileSize >> 24) & 0xFF);

        int dataSize = totalSize;
        merged[40] = (byte) (dataSize & 0xFF);
        merged[41] = (byte) ((dataSize >> 8) & 0xFF);
        merged[42] = (byte) ((dataSize >> 16) & 0xFF);
        merged[43] = (byte) ((dataSize >> 24) & 0xFF);

        return merged;
    }

    private AudioParameters.Voice getVoice(String vcn) {
        for (AudioParameters.Voice vo : AudioParameters.Voice.values()) {
            if (vo.name().equalsIgnoreCase(vcn) || vo.getValue().equalsIgnoreCase(vcn)) {
                return vo;
            }
        }
        return AudioParameters.Voice.CHERRY;
    }

    private byte[] downloadAudioFromUrl(String audioUrl) throws Exception {
        Request request = new Request.Builder()
                .url(audioUrl)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("下载音频文件失败: " + response.code());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new RuntimeException("音频文件内容为空");
            }

            return responseBody.bytes();
        }
    }
}
