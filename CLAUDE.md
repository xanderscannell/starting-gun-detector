# Starting Gun Detector

> An Android app that listens via microphone for a gunshot, captures the exact timestamp at the moment of detection, and displays it clearly — for use in track & field race timing.

## Context System

This project uses a context framework in `.context/` to prevent context degradation across sessions. You MUST follow these instructions every session.

### Every Session — Start

Before writing any code, read these files in order:

1. **`.context/CURRENT_STATUS.md`** — what was accomplished last session, what's in progress, what's next
2. **`.context/MASTER_PLAN.md`** — full roadmap; confirm which phase is active and which tasks are already checked off
3. **`.context/CONVENTIONS.md`** — tooling and environment setup
4. **`.context/ARCHITECTURE.md`** — system design and how components connect

Read as needed:
- `.context/DECISIONS.md` — past architectural decisions, to avoid re-debating settled questions

### Every Session — During Work

- **Follow CONVENTIONS.md** for tooling and build commands
- **Check DECISIONS.md** before proposing architectural changes — the decision may already be made
- **Record new decisions** in `DECISIONS.md` when significant technical choices are made
- **Tick off completed tasks** in `MASTER_PLAN.md` as soon as they are done — change `- [ ]` to `- [x]`

### Every Session — End

Before the session ends:

1. **Update `.context/CURRENT_STATUS.md`** with what was completed, what's in progress, what's next, and any blockers
2. **Update `.context/MASTER_PLAN.md`** — ensure all tasks completed this session are checked off
3. **Update this file's Current Focus section** if priorities changed
4. **Suggest a commit message** that includes both code and context changes — never commit or push automatically

## Current Focus

**Phase**: Phase 1 — Project Scaffold
**Working on**: Android project setup not yet started
**Key constraint**: Millisecond timing accuracy — timestamp must be captured at buffer receipt, with sub-buffer peak detection for best precision

## Reference

| File | Purpose |
|------|---------|
| `.context/CURRENT_STATUS.md` | Where the project stands right now |
| `.context/MASTER_PLAN.md` | Implementation roadmap |
| `.context/ARCHITECTURE.md` | System design and components |
| `.context/DECISIONS.md` | Architecture Decision Records |
| `.context/CONVENTIONS.md` | Tooling, environment, and build commands |
