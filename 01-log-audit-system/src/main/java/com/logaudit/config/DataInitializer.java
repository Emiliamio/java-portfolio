package com.logaudit.config;

import com.logaudit.entity.User;
import com.logaudit.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 数据初始化 — 首次启动时创建默认账号。
 *
 * 默认账号（仅用于本地 demo，生产环境务必修改密码）：
 *   admin / admin123  （ADMIN，可查询 + 导入 + 导出）
 *   user  / user123   （USER，仅查询）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userMapper.countUsers() > 0) {
            return;
        }

        createUser("admin", "admin123", "ADMIN");
        createUser("user", "user123", "USER");
        log.info("默认账号已创建：admin/admin123 (ADMIN), user/user123 (USER)");
    }

    private void createUser(String username, String rawPassword, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole(role);
        u.setEnabled(true);
        userMapper.insert(u);
    }
}
