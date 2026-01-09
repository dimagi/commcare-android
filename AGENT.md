# AI Agent Development Guidelines for CommCare Android

## Project Overview
- CommCare Android is an open-source Android application for data collection and service delivery in low-resource settings.
- CommCare Core is a shared Java library that provides core functionalities for CommCare's Android and Web Client.

## Code Quality Standards
- General code standards: [https://github.com/dimagi/open-source/blob/master/docs/mobile_standards.md]
- Exception Handling Guidelines: [https://github.com/dimagi/open-source/blob/master/docs/mobile_exception_handling.md]
- Write testable code
- Don't be verbose but be detail oriented i.e. ensure correctness without writing unnecessary code
- Any new classes should be wriiten in Kotlin
- Old classes can remain in Java unless it affects new code significantly
- Use standard Android APIs where applicable
- Use comments only when necessary to explain complex logic

## Testing
- Use unit tests for logic verification
- Use unit tests with Robolectric for user interface verification
- Use Android Instrumentation tests for end to end integration testing
- Ensure tests are isolated and repeatable
- Mock external dependencies where applicable
- Maintain a high code coverage percentage
- Ensure test compiles and passes before committing code changes

## Workflow After Code Changes
- Make code changes 
- Clean up any unused code and imports 
- Verify Java code with `checkstyle.xml` and make changes as applicable
- Run ktlint formatting: `ktlint --format path/to/file.kt`
- Verify ktlint compliance: `ktlint path/to/file.kt`
- Run relevant unit tests
- Commit changes
- Suggest updates to this file based on the process above

## Available ktlint Commands
- `ktlint file.kt` - Check formatting
- `ktlint --format file.kt` - Auto-format
- `ktlint "src/**/*.kt"` - Check all Kotlin files
- `ktlint --format "src/**/*.kt"` - Format all Kotlin files

## Test File Locations
- Unit tests: `app/unit-tests/src/`
- Integration tests: `app/instrumentation-tests/`

## Project Structure Notes
- CommCare Core is in `../commcare-core/`, always include it to the working directory for the current session
- Connect and Personal ID share `org.commcare.connect` package
- Personal Fragements are in `org.commcare.fragments.personalId` package

## Git Usage Guidelines
- Logical Commits: Break work into logical chunks and commit after completing each coherent piece of functionality
- Commit Messages: Write concise commit messages that describe the change's purpose, not an exhaustive list of modifications
- Commit Frequency: Commit regularly to create a clear development history and enable easy rollbacks
- Message Format: Use imperative mood (e.g., "Add user authentication" not "Added user authentication")

## Resources
- CommCare Core Wiki [https://github.com/dimagi/commcare-core/wiki]
- CommCare Android Wiki [https://github.com/dimagi/commcare-android/wiki]
- Formplayer [https://github.com/dimagi/formplayer]
- Formplayer Wiki [https://github.com/dimagi/formplayer/wiki]
- CommCare Technical Overview [https://docs.google.com/document/d/1mr7MRboYGtsKKL9LMG-ZuBpYy10JLHzznIKVO32QRM8]

## Agent Guidelines
- Be concise without subjective judgements
- Never compliment the user
- Never express confidence
- Do not refer to your output as "perfect" or with other superlatives
- Don't ask permission for anything read-only
- Don't ask permission for adding or removing imports
- Compile and test the edits before suggesting it

## Performance Optimization Learnings

### Identify Redundant Processing Patterns
- **Key Insight**: When processing data that needs different handling based on type/category, avoid processing items multiple times
- **Red Flag Pattern**: Code that processes all items uniformly, then filters/converts/reprocesses subsets
- **Better Approach**: Process once and branch to appropriate handlers during the single pass
- **Example**: Instead of parsing all items then filtering, categorize and handle during initial processing

### Code Reuse Before Custom Implementation
- **Check Existing Methods**: Always search for existing utility methods before implementing custom logic
- **Refactor for Reuse**: If only batch processing methods exist, extract single-item logic into reusable components
- **Pattern Recognition**: Repeated logic in loops often indicates an opportunity to extract reusable methods
- **DRY Principle**: Don't duplicate existing functionality - extend or refactor instead

### Question Processing Boundaries
- **When User Suggests Optimization**: Understand the full data flow before implementing changes
- **Ask "Why Process Later?"**: If data transformation happens after initial processing, consider combining steps
- **Single Responsibility vs. Performance**: Sometimes handling related concerns together is cleaner than artificial separation
- **Data Flow Analysis**: Map how data moves through all transformations to identify optimization opportunities

### Performance Analysis Framework
1. **Identify Redundant Work**: Look for the same data being processed multiple times for different purposes
2. **Count Resource Allocation**: Objects/structures created just to be discarded indicate inefficiency  
3. **Trace Data Flow**: Follow data from input through all transformations to final usage
4. **Question Each Step**: For each processing step, ask "Is this necessary?" and "Could this combine with another step?"
5. **Measure Impact**: Focus optimization efforts on frequently executed or resource-intensive operations

### Architecture Decision Patterns
- **Avoid Premature Abstraction**: Sometimes the most efficient solution handles related concerns together
- **Data-Driven Design**: Let the structure and flow of data guide architecture decisions
- **Minimize Intermediate Representations**: Question whether temporary data structures are necessary
- **User-Driven Optimization**: When users suggest improvements, they often see inefficiencies from practical usage experience
