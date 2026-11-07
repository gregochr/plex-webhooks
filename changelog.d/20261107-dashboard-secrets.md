### Security — Credentials no longer sent to the public dashboard

The unauthenticated dashboard page (`GET /`) embedded the value of every configuration property, including `hue.application-key` (full control of the Hue bridge) and the admin password, and `/api/hue/status` plus its SSE broadcast included the Hue application key. Anyone on the network could read them. Credential values are now never sent; the UI only shows whether they are set. Rotate your Hue application key (and admin password) if the dashboard was reachable by untrusted devices.
