### Fixed — Sunrise/sunset error responses no longer throw, and every class meets the coverage bar

A sunrise-sunset.org error response (empty `results`) now reads as a response with the provider's status and no result, and `SunriseSunsetTimeFactory` returns an empty `Optional` for it instead of failing. Every class now holds 95% line and 90% branch coverage with no per-class overrides, the current time comes from an injected `Clock`, periodic tasks run on an injected `TaskScheduler`, and the mutation-score gate rises from 70 to 98.
