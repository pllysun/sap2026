package com.sap.jw.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sap.jw.config.JwProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 调用 ddddocr 边车识别验证码图片。失败返回空串（不抛异常）。 */
@Component
public class OcrClient {

    private static final Logger log = LoggerFactory.getLogger(OcrClient.class);

    private final JwProperties props;
    // 强制 HTTP/1.1：JDK 默认 HTTP/2 会向 uvicorn 发 h2c 升级头，被拒为 400 Bad Request
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OcrClient(JwProperties props) {
        this.props = props;
    }

    public String recognize(byte[] image) {
        if (image == null || image.length == 0) return "";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.getOcrUrl() + "/ocr"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(image))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JSONObject o = JSON.parseObject(resp.body());
            String code = o == null ? null : o.getString("code");
            return code == null ? "" : code.trim();
        } catch (Exception e) {
            log.warn("OCR 边车调用失败: {}", e.getMessage());
            return "";
        }
    }
}
