package com.erp.module.system.service;

import com.erp.common.exception.BizException;
import com.erp.module.system.api.SystemErrorCodes;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片验证码（01-13 3.1）：4 位字母数字，不区分大小写，有效期 2 分钟，一次性。
 * 另记录不存在的用户名的失败次数，使“是否需要验证码”的判断对存在与不存在的账号一致，避免借此探测账号。
 * 存在内存中（单实例）；多实例部署时改为 Redis。
 */
@Service
public class CaptchaService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Duration UNKNOWN_FAIL_TTL = Duration.ofMinutes(30);
    private static final int MAX_ENTRIES = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(String code, Instant expireAt) {
    }

    private record Counter(int count, Instant expireAt) {
    }

    private final Map<String, Entry> captchas = new ConcurrentHashMap<>();
    private final Map<String, Counter> unknownFails = new ConcurrentHashMap<>();

    public record Captcha(String captchaId, String image) {
    }

    public Captcha create() {
        cleanup();
        String code = randomCode();
        String id = UUID.randomUUID().toString().replace("-", "");
        captchas.put(id, new Entry(code, Instant.now().plus(TTL)));
        return new Captcha(id, "data:image/png;base64," + render(code));
    }

    /** 校验并作废（一次性） */
    public void verify(String captchaId, String input) {
        Entry e = captchaId == null ? null : captchas.remove(captchaId);
        if (e == null || e.expireAt().isBefore(Instant.now())) throw new BizException(SystemErrorCodes.AUTH_CAPTCHA_EXPIRED);
        if (input == null || !e.code().equalsIgnoreCase(input.trim())) throw new BizException(SystemErrorCodes.AUTH_CAPTCHA_ERROR);
    }

    public int unknownUserFails(String username) {
        Counter c = unknownFails.get(key(username));
        return c == null || c.expireAt().isBefore(Instant.now()) ? 0 : c.count();
    }

    public void increaseUnknownUserFails(String username) {
        cleanup();
        unknownFails.merge(key(username), new Counter(1, Instant.now().plus(UNKNOWN_FAIL_TTL)),
                (a, b) -> new Counter(a.expireAt().isBefore(Instant.now()) ? 1 : a.count() + 1, b.expireAt()));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private void cleanup() {
        Instant now = Instant.now();
        if (captchas.size() > MAX_ENTRIES / 2) captchas.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(now));
        if (unknownFails.size() > MAX_ENTRIES) unknownFails.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(now));
        if (captchas.size() > MAX_ENTRIES) captchas.clear();
        if (unknownFails.size() > MAX_ENTRIES * 2) unknownFails.clear();
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        return sb.toString();
    }

    private static String render(String code) {
        int w = 110, h = 40;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(150 + RANDOM.nextInt(80), 150 + RANDOM.nextInt(80), 150 + RANDOM.nextInt(80)));
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(RANDOM.nextInt(w), RANDOM.nextInt(h), RANDOM.nextInt(w), RANDOM.nextInt(h));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(RANDOM.nextInt(90), RANDOM.nextInt(90), 60 + RANDOM.nextInt(120)));
                double angle = (RANDOM.nextDouble() - 0.5) * 0.5;
                int x = 12 + i * 24;
                g.rotate(angle, x, 28);
                g.drawString(String.valueOf(code.charAt(i)), x, 30);
                g.rotate(-angle, x, 28);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
