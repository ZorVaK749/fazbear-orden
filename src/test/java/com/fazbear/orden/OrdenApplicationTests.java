package com.fazbear.orden;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrdenApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Test
    void contextLoads() {}
}
