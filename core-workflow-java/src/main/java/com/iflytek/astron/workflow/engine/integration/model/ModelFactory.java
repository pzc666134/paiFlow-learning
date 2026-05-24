package com.iflytek.astron.workflow.engine.integration.model;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型集成工厂
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>管理所有LLM提供商的集成实现</li>
 *   <li>支持Spring自动发现和注册</li>
 *   <li>提供类型安全的模型获取方式</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 通过枚举获取
 * ModelIntegration integration = modelFactory.getModelIntegration(ModelTypeEnum.OPENAI);
 * 
 * // 通过字符串获取（向后兼容）
 * ModelIntegration integration = modelFactory.getModelIntegration("openai");
 * 
 * // 检查是否支持
 * if (modelFactory.hasModelIntegration("claude")) {
 *     // 处理Claude
 * }
 * }</pre>
 *
 * @author YiHui
 * @date 2025/12/1
 */
@Component
@Slf4j
public class ModelFactory {

    private final Map<ModelTypeEnum, ModelIntegration> modelMap = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private List<ModelIntegration> modelIntegrations;

    /**
     * 初始化时自动注册所有Spring管理的ModelIntegration实现
     */
    @PostConstruct
    public void init() {
        if (modelIntegrations != null && !modelIntegrations.isEmpty()) {
            for (ModelIntegration integration : modelIntegrations) {
                ModelTypeEnum modelType = integration.getModelType();
                modelMap.put(modelType, integration);
                log.info("Registered model integration: {} - {}", modelType.getCode(), modelType.getDescription());
            }
            log.info("Total registered model integrations: {}", modelMap.size());
        } else {
            log.warn("No model integrations found during initialization");
        }
    }

    /**
     * 根据模型类型枚举获取模型集成实现
     *
     * @param modelType 模型类型枚举
     * @return 模型集成实现实例
     * @throws IllegalArgumentException 当模型类型为空或未找到对应实现时
     */
    public ModelIntegration getModelIntegration(ModelTypeEnum modelType) {
        if (modelType == null) {
            throw new IllegalArgumentException("Model type cannot be null");
        }
        
        ModelIntegration integration = modelMap.get(modelType);
        if (integration == null) {
            throw new IllegalArgumentException("No model integration found for type: " + modelType.getCode());
        }
        return integration;
    }

    /**
     * 根据模型类型标识字符串获取模型集成实现（向后兼容）
     *
     * @param modelTypeCode 模型类型标识（如 "openai", "claude"）
     * @return 模型集成实现实例
     * @throws IllegalArgumentException 当模型类型为空、无效或未找到对应实现时
     */
    public ModelIntegration getModelIntegration(String modelTypeCode) {
        if (modelTypeCode == null || modelTypeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Model type code cannot be null or empty");
        }
        
        ModelTypeEnum modelType = ModelTypeEnum.fromCode(modelTypeCode.trim());
        return getModelIntegration(modelType);
    }

    /**
     * 注册模型集成实现
     * 将指定类型的模型集成实例注册到工厂中，用于后续的模型调用
     *
     * @param modelType 模型类型枚举
     * @param modelIntegration 模型集成实现实例，必须实现ModelIntegration接口
     * @throws IllegalArgumentException 当参数为空时
     */
    public void registerModelIntegration(ModelTypeEnum modelType, ModelIntegration modelIntegration) {
        if (modelType == null) {
            throw new IllegalArgumentException("Model type cannot be null");
        }
        if (modelIntegration == null) {
            throw new IllegalArgumentException("Model integration cannot be null");
        }
        
        ModelIntegration previous = modelMap.put(modelType, modelIntegration);
        if (previous != null) {
            log.warn("Replaced existing model integration for type: {}", modelType.getCode());
        }
        log.info("Registered model integration: {} - {}", modelType.getCode(), modelType.getDescription());
    }

    /**
     * 通过字符串类型标识注册模型集成实现（向后兼容）
     *
     * @param modelTypeCode 模型类型标识（如 "openai", "claude"）
     * @param modelIntegration 模型集成实现实例
     * @throws IllegalArgumentException 当参数为空或类型标识无效时
     */
    public void registerModelIntegration(String modelTypeCode, ModelIntegration modelIntegration) {
        if (modelTypeCode == null || modelTypeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Model type code cannot be null or empty");
        }
        if (modelIntegration == null) {
            throw new IllegalArgumentException("Model integration cannot be null");
        }
        
        ModelTypeEnum modelType = ModelTypeEnum.fromCode(modelTypeCode.trim());
        registerModelIntegration(modelType, modelIntegration);
    }

    /**
     * 检查是否已注册指定类型的模型集成
     *
     * @param modelType 模型类型枚举
     * @return 如果已注册返回true，否则返回false
     */
    public boolean hasModelIntegration(ModelTypeEnum modelType) {
        return modelType != null && modelMap.containsKey(modelType);
    }

    /**
     * 检查是否已注册指定类型的模型集成（向后兼容）
     *
     * @param modelTypeCode 模型类型标识（如 "openai", "claude"）
     * @return 如果已注册返回true，否则返回false
     */
    public boolean hasModelIntegration(String modelTypeCode) {
        if (modelTypeCode == null || modelTypeCode.trim().isEmpty()) {
            return false;
        }
        
        if (!ModelTypeEnum.contains(modelTypeCode.trim())) {
            return false;
        }
        
        return hasModelIntegration(ModelTypeEnum.fromCode(modelTypeCode.trim()));
    }

    /**
     * 获取已注册的模型集成数量
     *
     * @return 已注册的模型集成数量
     */
    public int getRegisteredCount() {
        return modelMap.size();
    }

    /**
     * 设置模型集成列表（主要用于测试）
     *
     * @param modelIntegrations 模型集成列表
     */
    public void setModelIntegrations(List<ModelIntegration> modelIntegrations) {
        this.modelIntegrations = modelIntegrations;
    }
}
