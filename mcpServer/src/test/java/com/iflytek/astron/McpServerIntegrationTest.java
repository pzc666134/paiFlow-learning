package com.iflytek.astron;


import com.iflytek.astron.service.QwenTTSService;
import com.iflytek.astron.service.SmartTTSService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MCP服务器 Spring上下文集成测试")
class McpServerIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private McpTools mcpTools;

    @Autowired(required = false)
    private SmartTTSService smartTTSService;

    @Autowired(required = false)
    private QwenTTSService qwenTTSService;

    @Test
    @DisplayName("测试应用上下文加载")
    void testContextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    @DisplayName("测试McpTools Bean注册")
    void testMcpToolsBeanExists() {
        assertNotNull(mcpTools, "McpTools应该被注册为Spring Bean");
    }

    @Test
    @DisplayName("测试SmartTTSService Bean注册")
    void testSmartTTSServiceBeanExists() {
        assertNotNull(smartTTSService, "SmartTTSService应该被注册为Spring Bean");
    }

    @Test
    @DisplayName("测试QwenTTSService Bean注册")
    void testQwenTTSServiceBeanExists() {
        assertNotNull(qwenTTSService, "QwenTTSService应该被注册为Spring Bean");
    }

    @Test
    @DisplayName("测试依赖注入")
    void testDependencyInjection() {
        assertNotNull(mcpTools);
        assertNotNull(smartTTSService);
        assertNotNull(qwenTTSService);
    }

    @Test
    @DisplayName("测试McpConfig配置类")
    void testMcpConfigExists() {
        assertTrue(applicationContext.containsBean("mcpTools"), "mcpTools Bean应该存在");
    }

    @Test
    @DisplayName("测试Spring AI MCP工具提供者")
    void testToolCallbackProviderExists() {
        boolean hasToolProvider = applicationContext.getBeanNamesForType(
                org.springframework.ai.tool.ToolCallbackProvider.class).length > 0;
        assertTrue(hasToolProvider, "应该存在ToolCallbackProvider Bean");
    }
}
