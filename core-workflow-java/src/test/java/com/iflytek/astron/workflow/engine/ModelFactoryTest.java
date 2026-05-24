package com.iflytek.astron.workflow.engine;
import com.iflytek.astron.workflow.engine.integration.model.ModelFactory;
import com.iflytek.astron.workflow.engine.integration.model.ModelIntegration;
import com.iflytek.astron.workflow.engine.integration.model.ModelTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ModelFactory 单元测试
 *
 * @author YiHui
 * @date 2025/12/1
 */
@DisplayName("ModelFactory 测试")
public class ModelFactoryTest {

    private ModelFactory modelFactory;
    private ModelIntegration mockOpenAiIntegration;
    private ModelIntegration mockClaudeIntegration;

    @BeforeEach
    void setUp() {
        modelFactory = new ModelFactory();
        
        // 创建 Mock 对象
        mockOpenAiIntegration = mock(ModelIntegration.class);
        mockClaudeIntegration = mock(ModelIntegration.class);
        
        when(mockOpenAiIntegration.getModelType()).thenReturn(ModelTypeEnum.OPENAI);
        when(mockClaudeIntegration.getModelType()).thenReturn(ModelTypeEnum.CLAUDE);
    }

    @Test
    @DisplayName("初始化时应自动注册所有ModelIntegration实现")
    void testInit_AutoRegistersIntegrations() {
        List<ModelIntegration> integrations = Arrays.asList(mockOpenAiIntegration, mockClaudeIntegration);
        modelFactory.setModelIntegrations(integrations);
        
        modelFactory.init();
        
        assertTrue(modelFactory.hasModelIntegration(ModelTypeEnum.OPENAI));
        assertTrue(modelFactory.hasModelIntegration(ModelTypeEnum.CLAUDE));
        assertEquals(2, modelFactory.getRegisteredCount());
    }

    @Test
    @DisplayName("通过枚举获取已注册的集成")
    void testGetModelIntegration_ByEnum_Success() {
        modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, mockOpenAiIntegration);
        
        ModelIntegration result = modelFactory.getModelIntegration(ModelTypeEnum.OPENAI);
        
        assertNotNull(result);
        assertSame(mockOpenAiIntegration, result);
    }

    @Test
    @DisplayName("通过字符串获取已注册的集成")
    void testGetModelIntegration_ByString_Success() {
        modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, mockOpenAiIntegration);
        
        ModelIntegration result = modelFactory.getModelIntegration("openai");
        
        assertNotNull(result);
        assertSame(mockOpenAiIntegration, result);
    }

    @Test
    @DisplayName("获取未注册的集成应抛出异常")
    void testGetModelIntegration_NotFound_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            modelFactory.getModelIntegration(ModelTypeEnum.CLAUDE);
        });
    }

    @Test
    @DisplayName("获取null类型应抛出异常")
    void testGetModelIntegration_NullType_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            modelFactory.getModelIntegration((ModelTypeEnum) null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            modelFactory.getModelIntegration((String) null);
        });
    }

    @Test
    @DisplayName("获取空字符串类型应抛出异常")
    void testGetModelIntegration_EmptyString_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            modelFactory.getModelIntegration("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            modelFactory.getModelIntegration("   ");
        });
    }

    @Test
    @DisplayName("检查已注册的集成应返回true")
    void testHasModelIntegration_Registered_ReturnsTrue() {
        modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, mockOpenAiIntegration);
        
        assertTrue(modelFactory.hasModelIntegration(ModelTypeEnum.OPENAI));
        assertTrue(modelFactory.hasModelIntegration("openai"));
    }

    @Test
    @DisplayName("检查未注册的集成应返回false")
    void testHasModelIntegration_NotRegistered_ReturnsFalse() {
        assertFalse(modelFactory.hasModelIntegration(ModelTypeEnum.CLAUDE));
        assertFalse(modelFactory.hasModelIntegration("claude"));
    }

    @Test
    @DisplayName("注册集成应成功")
    void testRegisterModelIntegration_Success() {
        modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, mockOpenAiIntegration);
        
        assertTrue(modelFactory.hasModelIntegration(ModelTypeEnum.OPENAI));
        assertEquals(1, modelFactory.getRegisteredCount());
    }

    @Test
    @DisplayName("重复注册应覆盖并记录警告")
    void testRegisterModelIntegration_Duplicate_Overwrites() {
        ModelIntegration anotherIntegration = mock(ModelIntegration.class);
        when(anotherIntegration.getModelType()).thenReturn(ModelTypeEnum.OPENAI);
        
        modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, mockOpenAiIntegration);
        modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, anotherIntegration);
        
        assertSame(anotherIntegration, modelFactory.getModelIntegration(ModelTypeEnum.OPENAI));
        assertEquals(1, modelFactory.getRegisteredCount());
    }

    @Test
    @DisplayName("注册null类型应抛出异常")
    void testRegisterModelIntegration_NullType_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            modelFactory.registerModelIntegration((ModelTypeEnum) null, mockOpenAiIntegration);
        });
    }

    @Test
    @DisplayName("注册null集成应抛出异常")
    void testRegisterModelIntegration_NullIntegration_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, null);
        });
    }

    @Test
    @DisplayName("获取已注册数量应正确")
    void testGetRegisteredCount_Correct() {
        assertEquals(0, modelFactory.getRegisteredCount());
        
        modelFactory.registerModelIntegration(ModelTypeEnum.OPENAI, mockOpenAiIntegration);
        assertEquals(1, modelFactory.getRegisteredCount());
        
        modelFactory.registerModelIntegration(ModelTypeEnum.CLAUDE, mockClaudeIntegration);
        assertEquals(2, modelFactory.getRegisteredCount());
    }
}
