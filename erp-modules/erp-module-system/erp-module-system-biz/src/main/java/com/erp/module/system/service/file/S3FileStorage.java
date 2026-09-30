package com.erp.module.system.service.file;

import com.erp.module.system.dal.dataobject.FileDO;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * S3 兼容对象存储（AWS S3、MinIO、阿里云 OSS、腾讯云 COS 等），使用 AWS Signature V4 直接调用 REST 接口，不依赖 SDK。
 *
 * <p>对象键 = 前缀 + 附件相对路径。上传先写临时文件得到长度（S3 PUT 需要 Content-Length），正文不参与签名（UNSIGNED-PAYLOAD）。
 * pathStyle=true 时地址为 endpoint/bucket/key（MinIO 默认），否则为 bucket.endpoint/key（OSS、COS 要求）。
 */
public class S3FileStorage implements FileStorage {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final String UNSIGNED = "UNSIGNED-PAYLOAD";

    public record Config(String endpoint, String region, String bucket, String accessKey, String secretKey, boolean pathStyle, String prefix) {
    }

    private final Config cfg;
    private final URI endpoint;
    private final HttpClient http;
    private final Clock clock;

    public S3FileStorage(Config cfg) {
        this(cfg, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Clock.systemUTC());
    }

    S3FileStorage(Config cfg, HttpClient http, Clock clock) {
        if (!StringUtils.hasText(cfg.endpoint()) || !StringUtils.hasText(cfg.bucket()) || !StringUtils.hasText(cfg.accessKey())
                || !StringUtils.hasText(cfg.secretKey())) {
            throw new IllegalStateException("对象存储配置不完整：需要 ERP_S3_ENDPOINT、ERP_S3_BUCKET、ERP_S3_ACCESS_KEY、ERP_S3_SECRET_KEY");
        }
        this.cfg = cfg;
        String e = cfg.endpoint().trim();
        this.endpoint = URI.create(e.endsWith("/") ? e.substring(0, e.length() - 1) : e);
        this.http = http;
        this.clock = clock;
    }

    @Override
    public String type() {
        return FileDO.STORAGE_S3;
    }

    @Override
    public void put(String path, InputStream content) throws IOException {
        Path tmp = Files.createTempFile("erp-upload-", ".part");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofMinutes(10))
                    .PUT(HttpRequest.BodyPublishers.ofFile(tmp));
            send(sign(b, "PUT", path, Map.of()), HttpResponse.BodyHandlers.discarding(), "上传");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public InputStream open(String path) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofMinutes(10)).GET();
        HttpResponse<InputStream> r = send(sign(b, "GET", path, Map.of()), HttpResponse.BodyHandlers.ofInputStream(), "读取");
        return r.body();
    }

    @Override
    public void delete(String path) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofMinutes(1)).DELETE();
        send(sign(b, "DELETE", path, Map.of()), HttpResponse.BodyHandlers.discarding(), "删除");
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler, String action) throws IOException {
        try {
            HttpResponse<T> r = http.send(req, handler);
            int s = r.statusCode();
            if (s == 404 && "删除".equals(action)) return r;
            if (s < 200 || s >= 300) {
                if (r.body() instanceof InputStream in) in.close();
                throw new IOException("对象存储" + action + "失败：HTTP " + s + " " + req.uri());
            }
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("对象存储" + action + "被中断", e);
        }
    }

    // ==================== 地址与签名 ====================

    String key(String path) {
        String p = cfg.prefix() == null ? "" : cfg.prefix().trim();
        if (!p.isEmpty() && !p.endsWith("/")) p += "/";
        return p + path;
    }

    String host() {
        String h = endpoint.getHost() + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
        return cfg.pathStyle() ? h : cfg.bucket() + "." + h;
    }

    /** 规范 URI：每段按 RFC 3986 编码，保留 / */
    String canonicalUri(String path) {
        String raw = cfg.pathStyle() ? cfg.bucket() + "/" + key(path) : key(path);
        StringBuilder sb = new StringBuilder();
        for (String seg : raw.split("/", -1)) sb.append('/').append(encode(seg));
        return sb.toString();
    }

    URI uri(String path) {
        return URI.create(endpoint.getScheme() + "://" + host() + canonicalUri(path));
    }

    private HttpRequest sign(HttpRequest.Builder b, String method, String path, Map<String, String> extraHeaders) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC);
        Map<String, String> headers = new TreeMap<>(extraHeaders);
        headers.put("host", host());
        headers.put("x-amz-content-sha256", UNSIGNED);
        headers.put("x-amz-date", AMZ_DATE.format(now));
        String auth = authorization(method, canonicalUri(path), "", headers, UNSIGNED, now);
        headers.forEach((k, v) -> {
            if (!"host".equals(k)) b.header(k, v);
        });
        return b.header("Authorization", auth).build();
    }

    /** AWS Signature V4：返回 Authorization 头；headers 的键须为小写 */
    String authorization(String method, String canonicalUri, String canonicalQuery, Map<String, String> headers, String payloadHash, ZonedDateTime now) {
        String region = StringUtils.hasText(cfg.region()) ? cfg.region() : "us-east-1";
        String date = DATE.format(now);
        StringBuilder ch = new StringBuilder();
        StringBuilder sh = new StringBuilder();
        new TreeMap<>(headers).forEach((k, v) -> {
            ch.append(k).append(':').append(v.trim()).append('\n');
            if (!sh.isEmpty()) sh.append(';');
            sh.append(k);
        });
        String canonical = method + "\n" + canonicalUri + "\n" + canonicalQuery + "\n" + ch + "\n" + sh + "\n" + payloadHash;
        String scope = date + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + AMZ_DATE.format(now) + "\n" + scope + "\n" + hex(sha256(canonical));
        byte[] k = hmac(("AWS4" + cfg.secretKey()).getBytes(StandardCharsets.UTF_8), date);
        k = hmac(k, region);
        k = hmac(k, "s3");
        k = hmac(k, "aws4_request");
        String signature = hex(hmac(k, toSign));
        return "AWS4-HMAC-SHA256 Credential=" + cfg.accessKey() + "/" + scope + ",SignedHeaders=" + sh + ",Signature=" + signature;
    }

    static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
