# Contributing to Flipjack

Thanks for your interest! Contributions of any size are welcome: code, tests, documentation,
or just trying Flipjack and reporting what's confusing.

## What Flipjack aims for

Flipjack is a **well-designed, lightweight** feature-toggle library where feature code needs
**no if-else**: a proxy decides which implementation runs. Changes are weighed against that goal:

- `flipjack-core` stays small and has no runtime dependencies.
- Integrations (Spring Boot, flag platforms) live in their own modules.
- Misconfiguration should fail when the proxy is built, at startup, not on a live call.
- Platform features such as admin UIs, rollout rules and experiments belong in flag services
  that plug in through `FeatureToggles`, not in Flipjack itself.

## Finding something to work on

- Issues labelled [`good first issue`](https://github.com/kamranzafar/flipjack/labels/good%20first%20issue)
  are small and self-contained.
- Issues labelled [`help wanted`](https://github.com/kamranzafar/flipjack/labels/help%20wanted)
  are larger; please share your plan in the issue before starting.
- **Comment on an issue before you start**, so two people don't work on the same thing.
- For a new idea, open an issue first to discuss it.

## Building

You need Java 17 or later and Maven 3.9+.

```sh
git clone https://github.com/kamranzafar/flipjack.git
cd flipjack
mvn verify
```

This builds both modules and runs every test. CI runs the same command on Java 17, 21 and 25.

## Pull requests

- Keep each pull request small and focused on one change.
- Add or update tests for any behaviour change. `ReadmeExamplesTest` checks the README's
  examples, so update it if you change an example.
- Update the README and `CHANGELOG.md` (under **Unreleased**) for user-visible changes.
- Match the style of the surrounding code: naming, comment density, and short Javadoc that says
  what something is for.
- Keep the Apache 2.0 license header at the top of every new source file; copy it from any
  existing file.
- Make sure `mvn verify` passes before opening the pull request.

## Reporting bugs

Open an issue with the Flipjack and Java versions, the Spring Boot version if relevant, a short
code sample that reproduces the problem, and what you expected to happen.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the same license as the project.
