# Contributing to MarkUltra

Thank you for contributing to MarkUltra!

## Guidelines
- MarkUltra is pure Java 8 without Kotlin or Jetpack Compose runtime overhead.
- All Markdown parsing passes through `flexmark-java` and `md/GfmHtml.java`.
- Rendering is performed 100% offline via bundled assets served through `https://appassets.androidplatform.net/`.
- Pull requests must compile cleanly with `./gradlew :app:assembleDebug` and pass headless assertions in `tools/GfmSelfTest.java`.
