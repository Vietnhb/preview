# Local visual fixture

Run the regular Vite dev server, then open `/tests/ui-preview/index.html?role=STUDENT`.
Supported roles: ADMIN, MANAGER, REVIEWER, SCHOOL, STUDENT. Set `route` to inspect
another permitted page, for example `?role=STUDENT&route=/community`.

This mounts the production routes with synthetic users, classes, curriculum,
assignments and library records. An Axios adapter intercepts every API call;
there is no connection to a database. A temporary fixture token is restored or
removed when the page is closed. This entry point is outside the production build.

Use this fixture to check search, scope and curriculum filters, assignment
prediction gating, navigation, dialogs, typography and responsive layouts.
Reviewer and management mutations are deliberately not implemented.
# Account capabilities

Use `role=REVIEWER&mode=edit` or `mode=review` to preview separate reviewer permissions. The default reviewer has both.

Use `role=STAFF&staff=head&route=/department` for the department workspace. Regular STAFF keeps its current teacher workspace.

Add `pending=1` to any role to inspect the mandatory first-login password form. Its password request is handled by the local adapter and changes only fixture state.

