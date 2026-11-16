package com.sap.service;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded image download with DNS pinning and checks on every redirect. */
@Component
class HomeworkImageFetcher {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    record ImageData(byte[] bytes, String extension, String contentType) {}

    ImageData fetch(String url, long deadlineNanos) throws IOException {
        URI current = URI.create(url);
        for (int redirects = 0; redirects <= 3; redirects++) {
            AtomicReference<InetAddress[]> pinned = new AtomicReference<>();
            URI checked = validateUrl(current.toString(), host -> {
                try {
                    InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
                    pinned.set(addresses);
                    return addresses;
                }
                catch (java.net.UnknownHostException e) { throw new IllegalArgumentException("DNS lookup failed", e); }
            });
            int remaining = remainingMillis(deadlineNanos);
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(Math.min(5000, remaining))
                    .setConnectionRequestTimeout(Math.min(5000, remaining))
                    .setSocketTimeout(Math.min(10000, remaining)).setRedirectsEnabled(false).build();
            // HttpClient connects only to the same addresses that passed validation.
            try (CloseableHttpClient client = HttpClients.custom().disableRedirectHandling()
                    .setDnsResolver(host -> {
                        if (!checked.getHost().equalsIgnoreCase(host)) throw new java.net.UnknownHostException("Unexpected host");
                        return pinned.get();
                    })
                    .setDefaultRequestConfig(config).build()) {
                HttpGet get = new HttpGet(checked);
                get.setHeader("User-Agent", "SAP-HomeworkImage/1.0");
                get.setHeader("Accept", "image/*");
                try (CloseableHttpResponse response = client.execute(get)) {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 300 && status < 400) {
                        if (redirects == 3) throw new IOException("Too many redirects");
                        var location = response.getFirstHeader("Location");
                        if (location == null) throw new IOException("Redirect without Location");
                        current = checked.resolve(location.getValue());
                        continue;
                    }
                    if (status != 200 || response.getEntity() == null) throw new IOException("HTTP " + status);
                    long declared = response.getEntity().getContentLength();
                    if (declared > MAX_BYTES) throw new IOException("Image exceeds 8 MB");
                    try (InputStream input = response.getEntity().getContent()) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            remainingMillis(deadlineNanos);
                            if (output.size() + count > MAX_BYTES) throw new IOException("Image exceeds 8 MB");
                            output.write(buffer, 0, count);
                        }
                        byte[] bytes = output.toByteArray();
                        String ext = extension(bytes);
                        if (ext == null) throw new IOException("Unsupported image bytes");
                        return new ImageData(bytes, ext, "image/" + (ext.equals("jpg") ? "jpeg" : ext));
                    }
                }
            }
        }
        throw new IOException("Too many redirects");
    }

    static URI validateUrl(String url, Function<String, InetAddress[]> resolver) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || host == null || host.isBlank() || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)
                || uri.getRawFragment() != null) throw new IllegalArgumentException("Blocked image URL");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local")
                || h.endsWith(".internal") || h.equals("metadata.google.internal")) {
            throw new IllegalArgumentException("Blocked image host");
        }
        if (h.matches("[0-9.]+") || h.contains(":")) {
            try {
                if (!isPublic(InetAddress.getByName(h))) throw new IllegalArgumentException("Blocked image address");
            } catch (java.net.UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP address", e);
            }
        }
        InetAddress[] addresses = resolver.apply(host);
        if (addresses == null || addresses.length == 0) throw new IllegalArgumentException("No resolved address");
        for (InetAddress address : addresses) {
            if (!isPublic(address)) throw new IllegalArgumentException("Blocked image address");
        }
        return uri;
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255;
            if (a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && c == 254
                    || a == 100 && c >= 64 && c <= 127 || a == 172 && c >= 16 && c <= 31
                    || a == 192 && (c == 0 || c == 168) || a == 198 && (c == 18 || c == 19)
                    || a == 255) return false;
            return !(a == 192 && c == 0 && d == 2 || a == 198 && c == 51 && d == 100
                    || a == 203 && c == 0 && d == 113);
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 255;
            boolean teredo = first == 0x20 && (b[1] & 255) == 0x01 && b[2] == 0 && b[3] == 0;
            boolean sixToFour = first == 0x20 && (b[1] & 255) == 0x02;
            return (first & 0xfe) != 0xfc && first != 0 && !teredo && !sixToFour
                    && !(first == 0x20 && (b[1] & 255) == 0x01 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8);
        }
        return false;
    }

    private static int remainingMillis(long deadlineNanos) throws IOException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remaining <= 0) throw new IOException("Document time limit exceeded");
        return (int) Math.min(remaining, Integer.MAX_VALUE);
    }

    static String extension(byte[] b) {
        if (b.length >= 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71
                && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10) return "png";
        if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255) return "jpg";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') return "gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        if (b.length >= 2 && b[0] == 'B' && b[1] == 'M') return "bmp";
        return null;
    }
}
