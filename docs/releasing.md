# Release checklist

- [ ] Check that tests pass on CI.
- [ ] Set `VERSION_NAME` in `gradle.properties` to the release version, such as `1.0.0-alpha01`.
- [ ] Update `readme.md` with the new maven coordinates and notable API changes.
- [ ] Commit `Prepare to release v{version}`. Keep the commit local until artifact verification passes.
- [ ] Publish archives to Maven Central:
  ```shell
  build-brief ./gradlew clean :library:publish --no-parallel --no-configuration-cache --stacktrace
  ```
- [ ] Wait for the artifacts to become available:
  ```shell
  dependency-watch await me.saket.fakesystemui:fakesystemui:{version}
  ```
- [ ] Push the release commit.
- [ ] Draft a changelog.
- [ ] Create a GitHub release.
- [ ] Set `VERSION_NAME` to the next development version, such as `1.0.0-alpha02-SNAPSHOT`.
  - [ ] Commit `Prepare next development version` and push.
