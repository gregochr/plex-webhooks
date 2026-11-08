### Removed — Two config read endpoints only the old page used

`GET /api/config/plex-commands/{mediaType}` and `GET /api/config/hue-scenes` are gone (a GET now returns 405 and 404).
The dashboard reads `GET /api/config` and `GET /api/config/sections`; the `PUT` endpoints are unchanged.
