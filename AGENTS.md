# AI Agent Development Guidelines for CommCare Android

## Build
-  Always use the Gradle wrapper (`./gradlew`) from the commcare-android directory for building commcare-core

## Project Overview
- CommCare Android is an open-source Android application for data collection and service delivery in low-resource settings
- CommCare Core is a shared Java library that provides core functionalities for CommCare's Android and Web Client

## Code Quality Standards
- [General code standards](https://github.com/dimagi/open-source/blob/master/docs/mobile_standards.md)
- [Exception Handling Guidelines](https://github.com/dimagi/open-source/blob/master/docs/mobile_exception_handling.md)
- Write testable code
- Don't be verbose but be detail oriented (i.e. ensure correctness without writing unnecessary code)
- Any new classes should be written in Kotlin
- Old classes can remain in Java unless it affects new code architecture significantly
- Use standard Android APIs where applicable
- Use comments sparingly only to explain complex logic

## Testing
- Use unit tests for logic verification
- Use unit tests with Robolectric for user interface verification
- Use Android Instrumentation tests for end-to-end integration testing
- Mock external dependencies when needed
- Ensure test compiles and passes before committing code changes
- When adding tests, ensure comprehensive coverage of all public methods in the class
- When appropriate, assert specific expected values rather than just non-null or truthy checks

## Workflow After Code Changes
All the changes below should be part of a separate commit after the main code changes:
- Clean up any unused code and imports
- Verify Java code with `checkstyle.xml` and make changes as applicable
- Run ktlint format and verify: `./gradlew ktlintFile -PfilePath=path/to/file.kt`
- Run relevant unit tests to ensure no regressions
- Commit changes

## AI Workflow: ktlint
After editing or creating a Kotlin file, always run the Gradle ktlint task:
```bash
./gradlew ktlintFile -PfilePath=<relative-path-to-file>
```
This task auto-formats the file and then verifies it is clean. If any violations remain that cannot be auto-fixed, resolve them manually. This should be done before committing.

## Test File Locations
- Unit tests: `app/unit-tests/src/`
- Instrumentation tests: `app/instrumentation-tests/`

## Project Structure Notes
- CommCare Core is in `../commcare-core/`, always include it to the working directory for the current session
- Connect and PersonalID share `org.commcare.connect` package
- PersonalID Fragments are in `org.commcare.fragments.personalId` package
- This is a multi-module Android project. When asked to find a file or class, search across all modules (commcare-android, commcare-core, commcare-support-library) rather than assuming the default module.
  
## Git Usage Guidelines
- Logical Commits: Break work into logical chunks and commit after completing each coherent piece of functionality
- Commit Messages: Write concise commit messages that describe the change's purpose, not an exhaustive list of modifications
- Commit Frequency: Commit regularly to create a clear development history and enable easy rollbacks
- Message Format: Use imperative mood (e.g., "Add user authentication" not "Added user authentication")
- Tag any commits with [AI] in the commit message

## Resources
- [CommCare Core Wiki](https://github.com/dimagi/commcare-core/wiki)
- [CommCare Android Wiki](https://github.com/dimagi/commcare-android/wiki)
- [Formplayer](https://github.com/dimagi/formplayer)
- [Formplayer Wiki](https://github.com/dimagi/formplayer/wiki)
- [CommCare Technical Overview](https://docs.google.com/document/d/1mr7MRboYGtsKKL9LMG-ZuBpYy10JLHzznIKVO32QRM8)

## Agent Tone Guidelines
- Be concise without subjective judgements
- Never compliment the user
- Never express confidence
- Do not refer to your output as "perfect" or with other superlatives
