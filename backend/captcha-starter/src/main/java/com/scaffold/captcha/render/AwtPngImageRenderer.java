package com.scaffold.captcha.render;

import com.scaffold.captcha.CaptchaImageRenderer;
import com.scaffold.captcha.CaptchaProperties;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AWT PNG 渲染器（默认实现）：底色 + 干扰线 + 噪点 + 随机字号字符，
 * 输出 base64 data URL。Graphics 在 finally 中释放，避免句柄泄漏。
 */
public class AwtPngImageRenderer implements CaptchaImageRenderer {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] FONT_NAMES = {"Arial", "Verdana", "Tahoma"};

    private final CaptchaProperties props;

    public AwtPngImageRenderer(CaptchaProperties props) {
        this.props = props;
    }

    @Override
    public String render(String code) {
        int width = props.getWidth();
        int height = props.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBackground(graphics, width, height);
            drawInterference(graphics, width, height);
            drawCode(graphics, code, height);
        } finally {
            graphics.dispose();
        }
        return "data:image/png;base64," + toBase64(image);
    }

    private void drawBackground(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(0xF5, 0xF7, 0xFA));
        graphics.fillRect(0, 0, width, height);
    }

    private void drawInterference(Graphics2D graphics, int width, int height) {
        // 干扰线：随机起止、随机色
        for (int i = 0; i < 5; i++) {
            graphics.setColor(randomColor(120, 200));
            graphics.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height),
                    RANDOM.nextInt(width), RANDOM.nextInt(height));
        }
        // 噪点：随机像素块
        for (int i = 0; i < 30; i++) {
            graphics.setColor(randomColor(100, 220));
            graphics.fillRect(RANDOM.nextInt(width), RANDOM.nextInt(height), 2, 2);
        }
    }

    private void drawCode(Graphics2D graphics, String code, int height) {
        int charCount = code.length();
        // 留出左右边距，字符均匀分布
        int slot = (props.getWidth() - 16) / charCount;
        for (int i = 0; i < charCount; i++) {
            graphics.setColor(randomColor(0, 110));
            Font font = new Font(FONT_NAMES[RANDOM.nextInt(FONT_NAMES.length)],
                    Font.BOLD, 26 + RANDOM.nextInt(9));
            graphics.setFont(font);
            // 随机基线偏移制造轻微起伏
            int baseline = (height + font.getSize()) / 2 - RANDOM.nextInt(6);
            graphics.drawString(String.valueOf(code.charAt(i)), 8 + i * slot, baseline);
        }
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + RANDOM.nextInt(range), min + RANDOM.nextInt(range), min + RANDOM.nextInt(range));
    }

    private String toBase64(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("验证码图片渲染失败", e);
        }
    }
}
