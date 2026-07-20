package com.lifepulse.auth.security;

import java.util.Locale;
import java.util.Set;

/**
 * 密码策略常量集中点（issue 2026-07-18-settings-v1-1-followup §2 HIGH-3）。
 *
 * <p>单一事实源：注册 / 改密端点共享同一份规则，避免 DTO 内联正则漂移；
 * 前端 {@code types/index.ts} 的 {@code PASSWORD_RULES} 与 {@link #WEAK_PASSWORDS}
 * 同步镜像。这里扩充了 MVP1 的「长度 + 字母数字」两维，新增「常见弱密码字典」
 * 兜底（top 国内外常见弱口令，case-insensitive 命中即拒）。
 *
 * <p>CLAUDE.md §4.2：禁魔法数字，所有阈值集中。
 * <p>CLAUDE.md §7.3：弱密码字典为安全基线收紧，不暴露完整字典到日志/响应。
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
        // no instances
    }

    /** 最小长度（spec §03 验证规则：≥8）。 */
    public static final int MIN_LENGTH = 8;

    /** 最大长度（BCrypt 截断点 72 字节之外无效，保守 64）。 */
    public static final int MAX_LENGTH = 64;

    /**
     * 复杂度正则：≥1 个字母 + ≥1 个数字。
     * <p>注意：先用 {@link #MIN_LENGTH} / {@link #MAX_LENGTH} 单独校验长度，
     * 此 regex 仅用于字符种类判断，避免单条 message 覆盖多维问题。
     */
    public static final String COMPLEXITY_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    /**
     * 常见弱密码字典（去重 + 小写化）。
     * <ul>
     *   <li>数字序列：12345678 / 123456789 / 1234567890 / 11111111 / 00000000</li>
     *   <li>通用弱口令：password / password1 / password123 / qwerty123 / qwertyuiop</li>
     *   <li>键盘序列：1q2w3e4r / qazwsx / zxcvbnm / asdfgh / asdf1234</li>
     *   <li>常见变体：abcdefgh / abc12345 / letmein / iloveyou / admin123 /
     *       welcome1 / monkey12 / dragon12 / sunshine1</li>
     *   <li>国内常用：a123456 / a12345678 / 5201314 / woaini520 / asd123 / qwe123</li>
     * </ul>
     * <p>大小写不敏感：使用时统一 {@link String#toLowerCase(Locale)} 再查表。
     */
    public static final Set<String> WEAK_PASSWORDS = Set.of(
            // 数字序列
            "12345678", "123456789", "1234567890", "11111111", "111111111",
            "00000000", "000000000", "12121212", "98765432",
            // 通用弱口令
            "password", "password1", "password12", "password123",
            "qwerty", "qwerty12", "qwerty123", "qwertyuiop", "qwertyuio",
            // 键盘序列
            "1q2w3e4r", "1q2w3e4r5t", "qazwsx", "qazwsx123", "zxcvbnm",
            "asdfgh", "asdfghjk", "asdf1234", "asd123", "qwe123",
            // 常见变体
            "abcdefgh", "abcdefg1", "abc12345", "abc123456",
            "letmein", "letmein1", "iloveyou", "iloveyou1",
            "admin", "admin123", "admin1234", "root", "root1234",
            "welcome", "welcome1", "monkey12", "monkey123",
            "dragon12", "dragon123", "sunshine1", "sunshine123",
            "p@ssw0rd", "passw0rd", "passw0rd1", "passw0rd!",
            // 国内常用
            "a123456", "a1234567", "a12345678", "a123456789",
            "5201314", "520520520", "woaini", "woaini520", "woaini1314",
            "5201314520", "qwer1234", "qwerasdf", "zxcvbnm1"
    );

    /** 单条对外的错误文案（汇总「长度/字符/字典」三维，任一不过都会进入同 code=1001）。 */
    public static final String VALIDATION_MESSAGE =
            "密码不符合安全策略：长度 8–64 位，至少含 1 个字母 + 1 个数字，且不能在常见弱密码字典中";

    /**
     * 长度是否在合法区间。
     */
    public static boolean isValidLength(String password) {
        if (password == null) {
            return false;
        }
        int len = password.length();
        return len >= MIN_LENGTH && len <= MAX_LENGTH;
    }

    /**
     * 字符复杂度是否命中（≥1 字母 + ≥1 数字）。
     */
    public static boolean meetsComplexity(String password) {
        if (password == null) {
            return false;
        }
        return password.matches(COMPLEXITY_REGEX);
    }

    /**
     * 是否命中常见弱密码字典（大小写不敏感）。仅在长度合法后调用，避免误判
     * 「123」之类的短串 + 空字典交集。
     */
    public static boolean isWeak(String password) {
        if (password == null) {
            return false;
        }
        return WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT));
    }
}