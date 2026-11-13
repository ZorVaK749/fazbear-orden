package com.fazbear.productos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = com.fazbear.orden.OrdenApplication.class)
class ProductosApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.security.oauth2.jwt.JwtDecoder decoder;

	@Test
	void contextLoads() {
	}

}
