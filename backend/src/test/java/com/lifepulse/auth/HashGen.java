package com.lifepulse.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 哈希重新生成器（V5 seed_demo_accounts.sql 用）。
 *
 * <p>默认 {@code @Disabled}，不在常规 {@code mvn test} 跑，避免污染日志。
 * 重新生成 hash 时：{@code mvn -q test -Dtest=HashGen} → 控制台输出
 * 两行 {@code KEY=hash}。把 hash 粘到 V5__seed_demo_accounts.sql 即可。
 */
@org.junit.jupiter.api.Disabled("regeneration helper; run via -Dtest=HashGen on demand")
class HashGen {

    @Test
    void printSeeds() {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(AuthConstants.BCRYPT_STRENGTH);
        System.out.println("DEMO_HASH=" + enc.encode("Demo123!"));
        System.out.println("ALICE_HASH=" + enc.encode("Demo123!"));
    }
}
