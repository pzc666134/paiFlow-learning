package com.iflytek.astron.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmartTTSService {
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Value("${spark.app-id:}")
    private String appId;

    @Value("${spark.api-key:}")
    private String apiKey;

    @Value("${spark.api-secret:}")
    private String apiSecret;

    @Value("${spark.tts-url:https://tts-api.xfyun.cn/v2/tts}")
    private String ttsUrl;

    public byte[] synthesize(String text, String vcn, Integer speed) throws Exception {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text is required");
        }
        if (vcn == null || vcn.isEmpty()) {
            throw new IllegalArgumentException("Voice character (vcn) is required");
        }

        log.info("Performing Smart TTS synthesis for text: {} with voice: {}, speed: {}",
                text.substring(0, Math.min(text.length(), 50)) + (text.length() > 50 ? "..." : ""),
                vcn, speed);

        String authUrl = getAuthUrl(ttsUrl, apiKey, apiSecret);
        String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");

        CompletableFuture<byte[]> resultFuture = new CompletableFuture<>();
        TTSWebSocketListener listener = new TTSWebSocketListener(resultFuture, text, vcn, speed, appId);

        WebSocket webSocket = httpClient.newWebSocket(new Request.Builder().url(wsUrl).build(), listener);

        try {
            return resultFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            webSocket.close(1000, "Timeout or error");
            throw new RuntimeException("TTS synthesis failed: " + e.getMessage(), e);
        }
    }

    private String getAuthUrl(String requestUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(requestUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());

        String signatureOrigin = "host: " + url.getHost() + "\n" +
                "date: " + date + "\n" +
                "GET " + url.getPath() + " HTTP/1.1";

        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
        mac.init(spec);
        byte[] signData = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signData);

        String authorizationOrigin = "api_key=\"" + apiKey + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"" + signature + "\"";
        String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

        return requestUrl + "?authorization=" + URLEncoder.encode(authorization, StandardCharsets.UTF_8.name()) +
                "&date=" + URLEncoder.encode(date, StandardCharsets.UTF_8.name()) +
                "&host=" + URLEncoder.encode(url.getHost(), StandardCharsets.UTF_8.name());
    }

    private static class TTSWebSocketListener extends WebSocketListener {
        private final CompletableFuture<byte[]> resultFuture;
        private final String text;
        private final String vcn;
        private final Integer speed;
        private final String appId;
        private final ByteArrayOutputStream audioStream = new ByteArrayOutputStream();

        public TTSWebSocketListener(CompletableFuture<byte[]> resultFuture, String text, String vcn, Integer speed, String appId) {
            this.resultFuture = resultFuture;
            this.text = text;
            this.vcn = vcn;
            this.speed = speed;
            this.appId = appId;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            JSONObject requestJson = buildTTSRequest();
            webSocket.send(requestJson.toString());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject responseJson = JSON.parseObject(text);
                if (log.isDebugEnabled()) {
                    log.debug("TTS service response: {}", responseJson);
                }

                int code = responseJson.getJSONObject("header").getIntValue("code");

                if (code != 0) {
                    String message = responseJson.getJSONObject("header").getString("message");
                    resultFuture.completeExceptionally(new RuntimeException("TTS service error: " + code + " - " + message));
                    webSocket.close(1000, "Error");
                    return;
                }

                if (responseJson.containsKey("payload")) {
                    JSONObject payload = responseJson.getJSONObject("payload");
                    if (payload.containsKey("audio")) {
                        String audioBase64 = payload.getJSONObject("audio").getString("audio");
                        byte[] audioData = Base64.getDecoder().decode(audioBase64);
                        audioStream.write(audioData);
                    }

                    int status = payload.getJSONObject("audio").getIntValue("status");
                    if (status == 2) {
                        resultFuture.complete(audioStream.toByteArray());
                        webSocket.close(1000, "Completed");
                    }
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
                webSocket.close(1000, "Error");
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            resultFuture.completeExceptionally(t);
        }

        private JSONObject buildTTSRequest() {
            JSONObject request = new JSONObject();

            JSONObject header = new JSONObject();
            header.put("app_id", appId);
            header.put("status", 2);
            request.put("header", header);

            JSONObject parameter = new JSONObject();
            JSONObject tts = new JSONObject();
            tts.put("vcn", vcn);
            tts.put("speed", speed);
            tts.put("volume", 50);
            tts.put("pitch", 50);
            tts.put("bgs", 0);
            tts.put("rhy", 0);
            tts.put("reg", 0);
            tts.put("rdn", 0);

            JSONObject audio = new JSONObject();
            audio.put("encoding", "lame");
            audio.put("sample_rate", 24000);
            audio.put("channels", 1);
            audio.put("bit_depth", 16);
            audio.put("frame_size", 0);
            tts.put("audio", audio);
            parameter.put("tts", tts);
            request.put("parameter", parameter);

            JSONObject payload = new JSONObject();
            JSONObject textPayload = new JSONObject();
            textPayload.put("encoding", "utf8");
            textPayload.put("compress", "raw");
            textPayload.put("format", "plain");
            textPayload.put("status", 2);
            textPayload.put("seq", 0);
            textPayload.put("text", Base64.getEncoder().encodeToString(this.text.getBytes(StandardCharsets.UTF_8)));
            payload.put("text", textPayload);
            request.put("payload", payload);

            return request;
        }
    }

    private static class ByteArrayOutputStream {
        private byte[] buf = new byte[1024];
        private int count = 0;

        public synchronized void write(byte[] b) {
            ensureCapacity(count + b.length);
            System.arraycopy(b, 0, buf, count, b.length);
            count += b.length;
        }

        public synchronized byte[] toByteArray() {
            return Arrays.copyOf(buf, count);
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity - buf.length > 0) {
                grow(minCapacity);
            }
        }

        private void grow(int minCapacity) {
            int oldCapacity = buf.length;
            int newCapacity = oldCapacity << 1;
            if (newCapacity - minCapacity < 0) {
                newCapacity = minCapacity;
            }
            buf = Arrays.copyOf(buf, newCapacity);
        }
    }

    private static class URL {
        private final String url;
        private final String host;
        private final String path;

        public URL(String url) throws Exception {
            this.url = url;
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) {
                throw new IllegalArgumentException("Invalid URL: " + url);
            }
            int hostStart = protocolEnd + 3;
            int hostEnd = url.indexOf("/", hostStart);
            if (hostEnd == -1) {
                this.host = url.substring(hostStart);
                this.path = "/";
            } else {
                this.host = url.substring(hostStart, hostEnd);
                this.path = url.substring(hostEnd);
            }
        }

        public String getHost() {
            return host;
        }

        public String getPath() {
            return path;
        }
    }
}
