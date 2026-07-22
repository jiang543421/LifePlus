package com.lifepulse.ai.llm;

import com.lifepulse.ai.llm.exception.LlmResponseInvalidException;
import com.lifepulse.ai.llm.exception.LlmUnavailableException;

/**
 * LLM 接入层接口（spec §4.3）。
 *
 * <p>Provider 由 {@code lp.ai.llm.provider} 配置驱动条件装配；
 * Task 3 / Task 4 提供 DeepSeek（远程 OpenAI 兼容）与 Ollama（本地 HTTP）
 * 两个实现。
 *
 * <p>调用方必须在 catch 中显式处理两种异常并降级到 L2 模板
 * （CLAUDE.md §11.3）；不允许吞错。
 */
public interface LlmClient {

    /**
     * 调用 LLM 生成内容。
     *
     * @throws LlmUnavailableException     5xx / 429 / 网络超时 / 连接错误
     * @throws LlmResponseInvalidException 4xx / 响应解析失败 / 非 JSON
     */
    LlmResponse generate(LlmRequest request);
}
