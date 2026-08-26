# Security Policy

## Reporting vulnerabilities

If you find a security vulnerability, open an issue with the `security` label.

That said — this is an AI agent app that intentionally runs shell commands on the user's
device, connects to user-provided API endpoints, and grants elevated access through Shizuku.
By design, the agent can do whatever the LLM it talks to decides to do.

So "vulnerabilities" here really means:

- **Remote code execution** — something that lets a *third party* execute code on the device
  without the user or the agent initiating it. If the agent itself runs a command, that's
  working as intended.
- **Credential leaks** — API keys, tokens, or identity/memory file contents exposed to
  unintended parties (e.g., logged in plaintext, sent to a third-party server, accessible
  to other apps).
- **Intent hijacking** — a way to make the agent execute something the user didn't ask for
  (e.g., prompt injection via a tool's output that the agent blindly trusts).

Things that are **not** security issues:
- The agent running a shell command you asked it to run
- The agent connecting to an API endpoint you configured
- The agent's LLM saying something weird or wrong
- Shizuku granting elevated access (the user explicitly opted into that)

## Supported versions

Only the latest commit on `master` is supported.
