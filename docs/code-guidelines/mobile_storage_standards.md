# Mobile Storage: SQLite DB vs Shared Preferences

## Use Shared Preferences for:

Small, simple key-value data such as:

* Theme / dark mode
* Feature flags
* First launch completed
* Last sync timestamp
* Selected language
* User settings
* Small primitive user state like number of times a user has been shown a specific prompt or reminder

**Best when:**

* Few values
* Simple reads/writes
* No querying needed
* UI State

---

## Use SQLite DB for:

Structured, growing, or queryable data such as:

* Lists of records
* Forms / submissions
* Messages / history
* Downloaded content
* Offline business data
* Queues / retries
* Parent-child relationships
* Data from Server APIs (generally structured persistent data)

**Best when:**

* Many records
* Need search / sort / filter
* Need transactions
* Data evolves over time

---

## Quick Decision Rule

| If data is...           | Use                |
| ----------------------- | ------------------ |
| App settings / flags    | Shared Preferences |
| Single values           | Shared Preferences |
| Records / lists         | SQLite             |
| Searchable / filterable | SQLite             |
| Growing over time       | SQLite             |

---

## Avoid

* Using Shared Preferences for non-volatile data that might need backward compatibility or migrations across app versions
* Using SQLite for isolated flags or intermediate properties

---

## Team Principle

* If data is state → Shared Preferences
* If data is records → SQLite**
