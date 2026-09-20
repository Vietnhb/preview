# Curriculum approval record

Date: 2026-09-20.

Vietnhb explicitly confirmed that all coverage rows were checked against the
attached source mappings and test evidence. The approval is recorded as an
owner-attested approval, not as an independent third-party academic review.

The reproducible command is:

```powershell
node scripts/attach-curriculum-sources.mjs
node scripts/approve-curriculum.mjs
node scripts/check-curriculum-coverage.mjs
```

The approval script refuses rows without a non-`unverified` source locator or
test ID and writes `owner-attested-approved:Vietnhb` to each approved row.
