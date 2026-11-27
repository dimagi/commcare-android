
# Exception Handling on CommCare Mobile

## Overview

This document outlines our approach towards handling Java Exceptions in our CommCare Mobile code and is meant to guide a developer on the best way to handle different kinds of exceptions on Mobile.

## Types of Exceptions

Our approach to exception handling needs to be different depending on whether an exception is happening due to external reasons out of the control of our application code or not.

### Input/Output or External Errors

These happen when you read/write to a file or network stream and are not dictated by our application code. Example:

```java
file = FileInputStream("file.txt"); // throws IOException
```

These typically represent external conditions that the code has no control over but must be dealt with.

### Code Logic Errors

Errors originating from our code itself as a way to propagate errors up the method stack chain. Example:

```java
private void writeToFile(String filePath) {
    if (StringUtils.isEmpty(filePath)) {
        throw new IllegalArgumentException("filePath can't be null or empty");
    }
    // proceed to write
}
```

Other data validation exceptions like `MalformedURLException`, `JSONException`, `NumberFormatException`, `ParseException`, and `IllegalStateException` are similar examples of code logic errors.

## General Best Practices

- As a rule of thumb, **we should not catch code logic exceptions in general code** unless there is a very good reason to do so. When you see `try/catch` in code, always eye it with extreme suspicion. Every `try/catch` has the potential to hide a critical programming bug.
- If we do catch code logic exceptions, we should ensure there is a **clear UI indication** for the respective error so that the user is not left unaware that some functionality on the page is not working properly.
- All exceptions should be caught only at the **UI or top-level methods**, not deep within the method call stack. This allows us to provide proper error feedback to the user and potentially incorporate a retry mechanism (automatic or user-initiated).
- For **Input/Output exceptions**, it’s preferable to handle them centrally at the top application level. For example, we may want all network exceptions to be handled in a single place to avoid duplicating similar `catch` blocks in many different places. One example of this is using a default exception handler to centrally handle `SessionUnavailableException` and `LocalizedException`.
- **No exception should be lost. All bugs should get reported to developers.**
- Use exceptions strictly for **error handling only**. Don’t throw exceptions as a workaround to return a result value from a function.

## Exceptions to the Above Guidelines

- It’s acceptable to do a **generic exception catch in non-essential app workflows** to make them fail-safe. These are typically reporting workflows intended for Dimagi’s team rather than the end-user. It must be ensured that user workflows are not affected by errors in such code.
- In some cases, **partial functionality is acceptable**. For example, on a page with 10 different tasks, the user might be allowed to work on the remaining tasks even if one task has an error. In such cases:
  - Tasks should not be inter-dependent.
  - The corrupt item should not introduce unpredictable behavior in other workflows.
  - The corrupt item should be clearly indicated to the user (for example, shown in red).
- **Interacting with legacy APIs/External Libraries:**

Sometimes it is appropriate to deviate from these guidelines when dealing with legacy code or external libraries that do not follow proper exception handling. For example, if an API uses exceptions for conditions that are not logic errors for your code, it is better to:
  
  - Write a **wrapper function** that converts such exceptions to appropriate return values.
  - Use this wrapper in your code.
