# Architecture

## Layers

```text
Compose UI
  -> ViewModels (StateFlow)
    -> Domain repository interfaces
      -> Repository implementations
        -> Room / DataStore / Android Keystore / Ktor gateway / Media APIs
```

### Presentation
`ui/` contains Compose screens, reusable Canvas/vector-driven UI components, themes, bilingual strings, and feature ViewModels. UI state is exposed with `StateFlow` and collected by Compose.

### Domain
`domain/model` contains provider, model, chat, settings, gallery, and diagnostic models. `domain/repository` defines contracts so presentation code does not depend on Room/Ktor implementation details.

### Data
- `data/local`: Room entities/DAOs/database and FTS4 virtual table.
- `data/network`: Ktor/OkHttp AI gateway for OpenAI-compatible, Gemini, and Anthropic streaming plus image/audio operations.
- `data/settings`: encrypted DataStore persistence backed by Android Keystore AES/GCM.
- `data/repository`: mapping/orchestration between domain and data sources.

### Dependency container
`DefaultAppContainer` wires repositories and the database at process startup without a heavyweight DI framework.

## Streaming path

```text
ChatScreen -> ChatViewModel -> ChatRepository.sendMessage()
          -> AiRepository.streamChat()
          -> KtorAiGateway provider adapter
          -> SSE chunks
          -> Room incremental assistant-message updates
          -> Room Flow -> StateFlow -> Compose
```

## Search path

Room maintains `message_fts` as an FTS4 external-content table tied to `messages`. Search terms are escaped and converted into prefix FTS queries. Results join back to `sessions` for direct navigation.

## Security boundary

DataStore stores only encrypted API-key payloads. `CryptoManager` creates a non-exportable Android Keystore AES key and uses a fresh GCM IV for each value. Network keys are decrypted only when preparing a provider request.

## Provider adapters

- OpenAI / OpenAI-compatible: `/v1/chat/completions` SSE, `/v1/images/generations`, `/v1/images/edits`, `/v1/audio/transcriptions`, `/v1/models`.
- Gemini: `v1beta/models/{model}:streamGenerateContent?alt=sse` with `x-goog-api-key`.
- Anthropic: `/v1/messages` SSE with `x-api-key` and `anthropic-version`.

The custom registry determines capability visibility in Chat, Vision, Image, and Audio screens.
