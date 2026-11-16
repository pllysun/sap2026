package com.sap.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class HomeworkImageFetcherTest {
    private final Function<String, InetAddress[]> publicDns = host -> new InetAddress[]{address("8.8.8.8")};

    @Test
    void rejectsUnsafeUrlsBeforeNetwork() {
        for (String url : new String[]{"file:///etc/passwd", "http://localhost/a.png", "http://localhost./a.png",
                "http://metadata.google.internal/a.png", "http://metadata.google.internal./a.png", "http://x.example:8080/a.png",
                "https://user:pass@x.example/a.png", "http://127.0.0.1/a.png"}) {
            assertThrows(IllegalArgumentException.class, () -> HomeworkImageFetcher.validateUrl(url, publicDns), url);
        }
        for (String ip : new String[]{"10.0.0.1", "192.168.1.2", "172.16.0.1", "169.254.169.254",
                "100.100.100.200", "127.0.0.1", "224.0.0.1", "0.0.0.0", "::1", "fc00::1",
                "2001:0000::1", "2002:7f00:0001::1"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> HomeworkImageFetcher.validateUrl("https://example.com/a.png", host -> new InetAddress[]{address(ip)}), ip);
        }
    }

    @Test
    void acceptsPublicHttpsWithoutConnecting() {
        assertEquals("https://images.example.com/a.png",
                HomeworkImageFetcher.validateUrl("https://images.example.com/a.png", publicDns).toString());
    }

    @Test
    void acceptsImageMagicAndRejectsSvgOrHtmlBytes() {
        assertEquals("png", HomeworkImageFetcher.extension(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}));
        assertEquals("jpg", HomeworkImageFetcher.extension(new byte[]{(byte) 255, (byte) 216, (byte) 255}));
        assertEquals("gif", HomeworkImageFetcher.extension("GIF89a".getBytes()));
        assertEquals("webp", HomeworkImageFetcher.extension("RIFF0000WEBP".getBytes()));
        assertEquals("bmp", HomeworkImageFetcher.extension("BM".getBytes()));
        assertNull(HomeworkImageFetcher.extension("<svg></svg>".getBytes()));
        assertNull(HomeworkImageFetcher.extension("<html>".getBytes()));
    }

    private static InetAddress address(String value) {
        try { return InetAddress.getByName(value); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
