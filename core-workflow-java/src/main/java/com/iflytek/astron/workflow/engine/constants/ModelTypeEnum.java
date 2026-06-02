package com.iflytek.astron.workflow.engine.constants;

import lombok.Getter;

/**
 * 模型类型枚举
 * 定义所有支持的LLM提供商类型，确保类型标识的全局统一性
 *
 * @author YiHui
 * @date 2025/12/1
 */
@Getter
public enum ModelTypeEnum {
    
    /**
     * OpenAI风格的API（包括讯飞、智谱、阿里云等兼容OpenAI接口的提供商）
     */
    OPENAI("openai", "OpenAI风格API"),
    
    /**
     * Anthropic Claude API
     */
    CLAUDE("claude", "Anthropic Claude"),
    
    /**
     * Google Gemini API
     */
    GEMINI("gemini", "Google Gemini"),
    
    /**
     * 百度文心一言
     */
    ERNIE("ernie", "百度文心一言"),
    
    /**
     * 腾讯混元
     */
    HUNYUAN("hunyuan", "腾讯混元");
    
    /**
     * 模型类型标识（用于Map的key）
     */
    private final String code;
    
    /**
     * 模型类型描述
     */
    private final String description;
    
    ModelTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 根据code获取枚举实例
     *
     * @param code 模型类型标识
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 当code不存在时抛出异常
     */
    public static ModelTypeEnum fromCode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Model type code cannot be null or empty");
        }
        for (ModelTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown model type code: " + code);
    }

    /**
     * 检查code是否存在
     *
     * @param code 模型类型标识
     * @return 如果存在返回true，否则返回false
     */
    public static boolean contains(String code) {
        if (code == null) {
            return false;
        }
        for (ModelTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
