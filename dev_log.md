# Development Log

## Current Status (as of 2026-05-29)

### What we are working on:
- Troubleshooting Data Lookup Errors in the MDMS configuration and backend services.
- Investigating issues related to missing `AreaType.json` files and missing locality codes within the `pb/BPA` (or `pb/NOC`) directories.
- Implementing fixes to ensure the system handles zero-result lookups gracefully without firing a fatal error.

### What has been done:
- Set up local diagnostics to reproduce and capture zero-result data lookup errors.
- Checked configuration files in `mdms-data/data/pb/NOC` such as `NocType.json` and `DocumentTypeMapping.json`.
- Started analyzing error outputs to pinpoint where the locality or AreaType lookups are failing.
- Identified that `LandBoundaryService.getAreaType` fails because `AreaType.json` was missing.
- Created `mdms-data/data/pb/BPA/AreaType.json` and `mdms-data/data/pb/LandServices/AreaType.json` with the locality codes `SUN01`, `SUN04`, `SUN06`, and `SUN178`.
- Restarted `egov-mdms-service` container to apply the new MDMS configurations.

### Current Errors:
- **[RESOLVED] Zero Result Lookup Error (AreaType):** `LandBoundaryService.getAreaType` was failing due to missing `AreaType.json` which we resolved by adding the file.
- **[RESOLVED] Boundary Data Not Found:** `land-services` boundary validation failed because `egov-location` was ignoring MDMS properties and hardcoding `dev.digit.org` due to an obscure property name mapping in its internal configuration. Resolved by adding `egov.services.egov_mdms.hostname: "http://egov-mdms-service:8080/"` directly to `egov-location`'s environment in `docker-compose.bpa.yml`.

---
*Note: Please update this file regularly with progress, new findings, and any resolved or new errors.*
