# Context Path Support Design

## Summary

Add full support for `server.servlet.context-path` across the application. The system must work correctly both when deployed at the root path `/` and when deployed under a non-root prefix such as `/fm`.

The implementation will use Spring Boot's standard `server.servlet.context-path` setting only. No custom `context_path` key will be introduced. When a non-root context path is configured, the application will only be accessible through that prefixed path. Root-path compatibility redirects are explicitly out of scope.

## Goals

- Support reading the deployment prefix from `server.servlet.context-path`.
- Keep the default behavior equivalent to `/`.
- Ensure all UI pages, API requests, static assets, generated download/upload URLs, and client-side navigations work under a non-root context path.
- Avoid duplicating context-path logic in multiple places.

## Non-Goals

- Support a second custom config key such as `context_path`.
- Keep `/` accessible when `server.servlet.context-path` is set to a non-root value.
- Introduce reverse-proxy-specific path rewriting beyond what Spring already provides.

## Current Problems

The current codebase mixes Spring-managed routing with many hard-coded absolute URLs:

- Static HTML files reference assets as `/css/...`, `/js/...`, `/favicon...`, `/vendor/...`.
- Front-end code uses fixed API prefixes such as `/api`.
- Some page switches use `window.location.href='/lite'`.
- Download and transfer helpers compare paths against fixed `/api/...` strings.
- Back-end services generate relative URLs that begin with `/api/...`.
- MVC config manually registers resource handlers and view forwards rooted at `/`.

This makes the app fail when served from any non-root prefix because the browser resolves these absolute paths from the host root rather than from the configured servlet context path.

## Design

### 1. Configuration Model

The application will use Spring Boot's built-in `server.servlet.context-path`.

Configuration rules:

- Default value remains `/` by simply omitting the property.
- Operators can set values such as `/fm` in `config/application.yml`, `config/application-dev.yml`, or `config/application-prod.yml`.
- No app-specific mirror setting will be added.

This keeps deployment behavior aligned with standard Spring conventions and avoids split configuration sources.

### 2. Server-Side Routing

Spring controllers already use path mappings like `/api/file` and `/lite`. With `server.servlet.context-path=/fm`, Spring will automatically expose them as `/fm/api/file` and `/fm/lite`.

To avoid conflicts and redundant manual path assumptions:

- Keep controller `@RequestMapping` values unchanged.
- Remove or minimize MVC path wiring that unnecessarily re-declares root/static routes in a context-path-sensitive way.
- Keep root controller methods such as `/`, `/full`, and `/lite` relative to the servlet context. Under `/fm`, these become `/fm`, `/fm/full`, and `/fm/lite`.

The server should rely on Spring's servlet context handling rather than trying to emulate it manually.

### 3. Runtime Context Prefix for Front-End

The browser must learn the active context prefix at runtime.

Recommended mechanism:

- Render a lightweight runtime value into each HTML entry page using a relative script snippet or a `<meta>`/global variable that derives from `window.location.pathname` and/or from server-provided request context.
- Centralize front-end path joining in one helper rather than scattering string concatenation.

The front-end will use a single normalized context prefix with these rules:

- `/` becomes an empty browser prefix for path joining.
- `/fm` becomes `/fm`.
- Joining must never produce `//api` or `/fm//api`.

### 4. Static Asset Paths

All static asset references in entry HTML pages must stop using host-root absolute paths.

Affected pages:

- `src/main/resources/static/index.html`
- `src/main/resources/static/lite.html`
- `src/main/resources/static/share.html`
- `src/main/resources/static/test.html` if it is still used

Required change:

- Convert asset references to context-safe relative paths such as `./js/app.js`, `./css/app.css`, `./vendor/...`, or equivalent prefix-aware rendering.

This ensures the browser requests assets from `/fm/...` when the page itself is served under `/fm/...`.

### 5. Front-End API and Navigation

All front-end API and navigation paths must be generated through a shared helper.

Front-end rules:

- Axios `baseURL` must become `joinContextPath('/api')` instead of hard-coded `/api`.
- Direct page navigation such as the lite/full switch must become `joinContextPath('/lite')`.
- URL checks for download/session/stream endpoints must compare against prefixed paths, not raw `/api/...`.
- Any helper returning API-relative paths must become context-aware.

This logic should live in one reusable helper module or in a small shared function block already loaded by the entry pages.

### 6. Back-End Generated URLs

Server-side code that returns URL strings to the browser must prepend the current servlet context path.

Primary affected area:

- `TransferProxyService`

Required rule:

- Any returned app-local URL such as `/api/file/download?...` must become `contextPath + "/api/file/download?..."`.

Preferred source of truth:

- Use Spring's request context utilities or a dedicated helper that reads the current servlet context path per request.

The service must not guess the prefix from config files directly because the active request context is the authoritative runtime value.

### 7. Error Handling and Edge Cases

- Context path `/` must behave exactly like today's deployment.
- Non-root prefixes must tolerate trailing-slash normalization cleanly.
- URL joining must preserve query strings and avoid double slashes.
- Share-page downloads, archive task downloads, upload endpoints, and stream endpoints must all resolve against the active context path.
- If operators misconfigure `server.servlet.context-path` with an invalid value, startup behavior remains Spring's responsibility; no custom validation layer is required.

## Testing Strategy

### Automated Back-End Tests

Add focused tests for generated URLs and routing assumptions:

- Verify local URLs returned by transfer/download services include the servlet context path when present.
- Verify default behavior remains unchanged when context path is empty/root.

### Automated Front-End Tests

Add focused tests for path helpers:

- Joining `/` with `/api` yields `/api`.
- Joining `/fm` with `/api` yields `/fm/api`.
- Joining `/fm` with `/lite` yields `/fm/lite`.
- Download-path recognition logic accepts prefixed API routes.

### Manual Verification

Verify these scenarios under both `/` and `/fm`:

- Open main UI.
- Switch between full and lite UI.
- Load share page.
- Load static JS/CSS/assets without 404.
- Browse buckets/files.
- Single-file download.
- Batch/archive download.
- Share download.

## File Impact

Likely files to modify:

- `config/application.yml`
- `config/application-dev.yml`
- `config/application-prod.yml`
- `src/main/java/com/lan4jn/file/config/WebMvcConfig.java`
- `src/main/java/com/lan4jn/file/controller/UIController.java`
- `src/main/java/com/lan4jn/file/service/TransferProxyService.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/lite.html`
- `src/main/resources/static/share.html`
- `src/main/resources/static/test.html` if still relevant
- `src/main/resources/static/js/app.js`
- `src/main/resources/static/js/lite.js`
- `src/main/resources/static/js/download-manager.js`
- `src/main/resources/static/js/transfer-ui-helpers.js`
- `src/main/resources/static/js/url-helpers.js`
- related test files in `src/test/java` and `src/test/js`

## Implementation Notes

- Prefer one shared client-side `joinContextPath()` helper over repeated string concatenation.
- Prefer one shared server-side helper for local app URLs over repeated `request.getContextPath()` usage.
- Keep changes narrow: this is a path-resolution fix, not a routing redesign.

## Acceptance Criteria

- With no explicit `server.servlet.context-path`, the app works exactly as before from `/`.
- With `server.servlet.context-path=/fm`, the following all work from `/fm`:
  - main UI
  - lite UI
  - share UI
  - API calls
  - single-file download
  - batch/archive download
  - transfer task URLs
- No root-path redirect or fallback is provided when a non-root context path is configured.
