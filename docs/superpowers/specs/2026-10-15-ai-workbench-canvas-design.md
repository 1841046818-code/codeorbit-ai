# CodeOrbit AI Workbench and Infinite Canvas Design

## Status

Approved for design documentation on 2026-10-15. Implementation has not started. This document is ready for user review before an implementation plan is written.

## Context

Summer Checkin remains the host application and owns authentication, navigation, MySQL persistence, and the existing Ollama-backed AI conversation at `/ai-chat`. The current `/agent` route is a study-planning agent, not a coding workbench. There is no infinite-canvas route or canvas persistence model today.

The product adds two signed-in web experiences: an AI coding workbench inspired by DSH Desktop and an infinite canvas inspired by Open Canvas. The browser application must not execute commands on the user's computer or server, nor run arbitrary MCP servers or plugins. The existing Ollama/llama.cpp service remains server-configured; user-managed model connections are a separate feature.

## Goals

- Add discoverable, authenticated `/workbench` and `/canvas` routes within the existing dashboard shell.
- Let each user configure one or more model connections using provider type, API base URL, API key where required, model identifier, connection test, and a default selection.
- Support OpenAI-compatible APIs and server-configured Ollama/llama.cpp. Do not expose server model credentials to the browser.
- Provide a useful browser-based coding workspace with persistent projects/files, streaming AI assistance, and explicit user-controlled application of generated edits.
- Provide a persistent infinite canvas with movable/zoomable nodes, relationships, AI generation, and export.
- Keep all new records scoped to the authenticated user and stored in the existing MySQL database.
- Deliver a local preview before any deployment action.

## Non-goals

- Executing a shell, build, test, or arbitrary code in the browser/server.
- Accessing local files without an explicit browser upload/download action.
- Running user-provided MCP servers, desktop plugins, or arbitrary tools.
- Replacing Summer Checkin's authentication, existing learning features, or `/ai-chat`.
- Real-time multiplayer editing, version-control integration, or unrestricted browser access to private-network model endpoints in the initial web release.

## Product Structure

### Navigation and routes

- Add `AI 工作台` -> `/workbench` and `无限画布` -> `/canvas` to the signed-in desktop and mobile navigation.
- Preserve `/ai-chat` as the lightweight conversation experience.
- Reuse the current authenticated dashboard layout and visual system; these are product workspaces, not separate applications.
- At narrow viewport widths, collapse secondary panels into drawers or tabs without removing core actions.

### AI workbench

- Three-column workspace: project/file navigator, editor and generated artifacts, and AI conversation. The file navigator and AI panel can be collapsed; the active file remains visible and usable on smaller screens.
- Users can create and rename projects and files, edit and save text/code files, upload/download files through explicit browser controls, and browse recent workspace projects.
- AI requests include only the active file or the specific files the user selects. Do not upload the full project implicitly.
- AI responses may create a proposed new file or a proposed replacement/patch for the active file. The user reviews and explicitly applies or discards each proposal; the server does not silently overwrite project files.
- Conversation history is associated with a workbench project and is isolated from the existing general AI-chat history unless the user explicitly starts a new workbench conversation.
- No terminal, command execution, automatic repository scan, or hidden filesystem access is shown as available.

### Model connections

- Provide a model settings surface reachable from the workbench, with connection name, provider kind, API base URL, API key, model identifier, and default toggle.
- OpenAI-compatible custom providers use a caller-entered base URL and model identifier. Model discovery may be offered when the provider's `/models` endpoint is compatible; manual model entry remains available.
- Ollama/llama.cpp running as the product's managed service is a server-configured provider and does not require a per-user API key. The server's configured default model is selectable only if the service exposes a safe model list; otherwise display the configured model as read-only.
- A connection test makes a bounded, non-streaming request and returns a safe success/failure message. It never returns provider response headers or secrets.
- The selected provider/model is stored as a per-user default and can be overridden for an individual workbench request. Canvas has its own model selector and an in-canvas entry to manage connections; both surfaces share the same per-user connection profiles and provider service, so credentials are configured only once.
- Credentials are encrypted at rest using authenticated encryption (AES-256-GCM) with a required server-side encryption key from the environment. Ciphertext, nonce, and authentication tag are persisted separately or in a versioned envelope. Decryption happens only in server code.
- Read APIs return a `hasApiKey` flag and a masked hint only. Updating a credential replaces it; an omitted key preserves the existing encrypted value. Deleting a connection removes its secret.
- Never log API keys, authorization headers, or full provider request objects. Error responses are sanitized.
- User-defined endpoints must use HTTPS in production and pass SSRF controls: reject localhost, link-local, private/reserved IPs, credentials in URLs, unsupported schemes, and redirects; resolve and validate DNS results before requests. A managed internal Ollama/llama.cpp endpoint is trusted server configuration and does not pass through the user-defined endpoint path. Development may allow loopback explicitly.
- Apply request timeouts, response-size limits, per-user rate limits, and allow only the required chat-completion/model-list paths. Do not expose arbitrary URL fetches.

### Infinite canvas

- Provide an infinite 2D workspace with pan, zoom, fit-to-content, node selection, drag, keyboard delete, undo/redo, and autosave with visible save/error status.
- Initial node types: text/note, image or uploaded-file reference, and AI prompt/result. Users can connect nodes to express input/context relationships; AI actions use only connected or explicitly selected context.
- AI generation creates a new result node and never destroys source nodes. A model selector is available in the canvas toolbar for each generation, with a shortcut to configure or test model connections without leaving the canvas.
- Support creating, renaming, duplicating, and deleting canvases; export a canvas as JSON and a readable Markdown summary. Imported content must be validated and size-limited.
- No multiplayer sync, executable nodes, arbitrary embedded HTML, or external URL fetching in the first release.

## Architecture and data

- Add a server-side model connection service that resolves the authenticated user's selected connection, decrypts its credential, validates its provider configuration, and makes streaming/non-streaming model requests. Keep provider-specific details behind this service; do not reuse or mutate the global environment model pool for per-user credentials.
- Store model profiles in a new MySQL table keyed by `userId`, with encrypted secret material, provider kind, base URL, model ID, default state, and timestamps. Enforce ownership in every read/update/delete operation and at most one default profile per user through transactional service logic.
- Store workbench projects and text files in user-owned MySQL tables. Use bounded file sizes, normalized relative paths, and a documented set of allowed text extensions. Uploaded binary objects should use the existing object-storage abstraction when available; database records store metadata and object keys, not unbounded binary blobs.
- Store canvas documents as versioned JSON scene data in a user-owned MySQL table. Validate the document schema, node/edge references, and maximum size before writes and AI use. Save with optimistic `updatedAt` conflict detection to avoid silently clobbering concurrent tabs.
- Add foreign keys/cascade rules and indexes for `userId` and parent records. Use additive Prisma migrations; do not rewrite unrelated migrations or change existing auth/conversation records.
- Reuse existing authentication helpers and dashboard layout. All API routes must return 401 when unauthenticated and 404 for resources not owned by the caller.

## Failure and security behavior

- Missing/malformed provider settings fail before contacting a model. Provider timeout, invalid credentials, unavailable model, and quota errors get distinct sanitized UI messages.
- Streaming cancellation aborts the upstream request and must not persist a partial assistant response as a completed message.
- Encryption key absence or invalid length prevents saving/decrypting user API secrets and is reported as a server configuration error; never fall back to plaintext.
- Invalid or oversized canvas/project payloads fail with 400/413 and do not partially persist.
- Model connection list and settings responses never include encrypted or plaintext secret fields.

## Verification

- Unit tests: secret encryption/decryption/tamper rejection; masking and update-preserve semantics; provider URL validation including private IP, redirect, and DNS cases; canvas schema/size/reference validation; project path and file-size validation.
- API tests: unauthenticated access, cross-user ownership denial, CRUD isolation, model test sanitization, streaming cancellation, canvas save conflicts, and invalid payload rejection.
- UI checks: desktop and mobile navigation, provider setup and test flows, file create/edit/save and proposed-change apply/discard, canvas create/edit/connect/undo/redo/autosave/export, loading/empty/error states, and keyboard accessibility.
- Run Prisma validation/generation, focused tests, ESLint, TypeScript/build checks, and inspect the local browser preview at desktop and mobile sizes. Do not deploy during this phase.

## Delivery sequence

1. Secure model-connection data model, encryption, and provider service, including tests.
2. AI workbench project/file surface and explicit AI proposal review/apply flow.
3. Infinite canvas persistence and interaction surface, reusing the provider service.
4. Integrate navigation, responsive behavior, verify all paths, then start local preview for user review.

Desktop command/MCP execution remains a separate future subsystem requiring a privileged, isolated Agent Host and an explicit permission model.
