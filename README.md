# Labs

A collection of small, self-contained projects created to explore implementation
techniques around time, concurrency, and correctness.

The focus is on clarity and learning, not on providing production-ready libraries.

---

## Repository structure

```text
labs
└── projects
    ├── rate-limiter
    │   └── java
    └── ...
```

Each directory under `projects/` represents a separate topic.

A topic may have one or more language implementations.
Each language implementation is fully independent:
- its own build
- its own tooling
- its own tests

---

## Conventions

### Code style
- Common editor settings live in `.editorconfig`
- Line endings and diffs are normalized via `.gitattributes`

### Commits
- Small, focused commits
- Formatting changes are kept separate from logic changes

---

## Projects

- **Rate limiter**
  Token Bucket and Spacing (Leaky) Bucket implementations with tests
  `projects/rate-limiter/java`

---

## License

MIT — see `LICENSE`.
