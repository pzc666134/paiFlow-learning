# Model Integration Package

This package separates two model usage paths:

## llm

`llm` is for ordinary LLM node calls.

Call flow:

```text
LLMNodeExecutor
  -> ModelServiceClient
  -> ModelFactory
  -> ModelIntegration
  -> LlmResVo
```

Responsibilities:

- `ModelServiceClient`: validates request and delegates model calls.
- `ModelFactory`: registers and resolves `ModelIntegration` by `ModelTypeEnum`.
- `ModelIntegration`: executes one LLM request and returns aggregated response data.
- `bo`: request, callback and response DTOs.
- `history`: LLM node chat history storage.
- `impl`: provider-specific ordinary LLM integrations.

## agent

`agent` is for Spring AI Agent node chat model construction.

Call flow:

```text
AiAgentNodeExecutor
  -> AgentChatModelServiceClient
  -> AgentChatModelFactory
  -> AgentChatModelProvider
  -> ChatModel
  -> ChatClient
```

Responsibilities:

- `AgentChatModelServiceClient`: resolves node model parameters and asks the factory for a chat model.
- `AgentChatModelFactory`: registers and resolves `AgentChatModelProvider` by `ModelTypeEnum`.
- `AgentChatModelProvider`: builds a Spring AI `ChatModel`; it does not execute the agent request.

## Shared Model Type

`ModelTypeEnum` is shared by both paths and lives in `engine.context`.

Keep the two paths separate:

- Ordinary LLM integrations are request callers.
- Agent providers are chat model builders.

