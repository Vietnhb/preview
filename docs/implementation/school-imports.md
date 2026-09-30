# School CSV imports

School data uses one preview and commit protocol for four resources. The interface is available in the school user directory, class directory, class members, and reports. A department head can import enrollments and teacher assignments for their own school. Only SCHOOL can create classes; SCHOOL and MANAGER can import STAFF/STUDENT accounts in an authorized school.

## Templates and columns

Download the template from the import dialog, or `GET /api/schools/{schoolId}/imports/template?kind=USERS`. Each UTF-8 template includes a BOM, all supported columns, and an example row. The USERS template includes both student and teacher examples. Replace example emails with actual addresses before importing.

- `USERS`: `fullName,dateOfBirth,email,initialPassword,role,staffType,classCode,schoolYear,avatarUrl`. Name, date of birth, email, initial-password header, and role headers are required. Date is ISO `yyyy-MM-dd`, e.g. `Nguyễn Minh An,2010-09-15,minhan@example.edu.vn,ChangeMe2026!,STUDENT,,,,`. Password may be blank to generate a random initial password. Role must be `STUDENT` or `STAFF`. STAFF defaults to `TEACHER`; `DEPARTMENT_HEAD` creates a department head. `staffType` is blank for students. Avatar is an optional HTTP(S) URL. Class and year are optional, but must both be supplied to enroll the new student or assign the new teacher.
- `CLASSES`: `classCode,gradeLevel,schoolYear,subject`, e.g. `10A1,10,2026-2027,Vật lý`. Subject is optional; other columns are required. Grade must be 10, 11, or 12. Class code is the existing class name, unique within the school and academic year. Archived names cannot be reused for the same year.
- `ENROLLMENTS`: `studentEmail,classCode,schoolYear`, e.g. `minhan@example.edu.vn,10A1,2026-2027`. All columns are required. The active student and class must already exist in this school. Students already enrolled in the year must use the separate transfer workflow.
- `TEACHER_ASSIGNMENTS`: `teacherEmail,classCode,schoolYear`, e.g. `giaovien@example.edu.vn,10A1,2026-2027`. All columns are required. The active STAFF account and class must already exist in this school. An existing active assignment is reported as a row error.

Academic years consist of two consecutive four-digit years. Headers are case-insensitive; underscores are accepted (`full_name`), and `password` is accepted as the initial-password header. Unknown and duplicate headers are rejected. Commas, escaped quotes, and line breaks inside quoted fields are supported. CSV must use UTF-8.

Profile and reference fields are trimmed. Supplied initial passwords preserve leading and trailing whitespace exactly through parsing, preview, account creation, and credential export. A password cell containing only whitespace is treated as blank and generates a random initial password.

## Preview, edit, and commit

1. Send `POST /api/schools/{schoolId}/imports/preview` as multipart: query parameter `kind`, file part `file`. This parses and validates without writing to the database.
2. The response returns `{kind,columns,rows,validRows,invalidRows,canCommit,previewToken}`. Each row contains `{row,data,errors,warnings,matches}`. The editable table can change fields or remove rows. Edits clear the authorization token and disable commit.
3. Revalidate edited rows with JSON `POST .../preview`: `{kind,rows:[{row:2,data:{...}}]}`. Successful preview returns normalized rows, generated passwords for blank password cells, and a signed token valid for 15 minutes.
4. Confirm once with JSON `POST .../commit`: `{kind,rows:[{row:2,data:{...}}],previewToken}`. Use exactly the reviewed row data and order. The signature binds the complete batch to the actor, school, and resource. Changing rows, using another actor/school, expiration, or restarting the server requires a new preview.
5. The response is `{kind,imported,credentials}`. Account imports include initial credentials for an explicit CSV download in the dialog. Other resources return an empty credential list. Credentials are held only in dialog memory and discarded when it closes; no plaintext password is persisted. AccountService hashes passwords and requires the new account to change its initial password on first login.

Email uniqueness is checked without case sensitivity against existing users and within the file. Accounts with the same normalized name and birth date but different emails remain importable; preview shows warnings and matching school accounts. It never merges existing accounts. Passwords require at least eight characters and at most 72 UTF-8 bytes. Invalid dates, missing references, role mismatches, duplicate class/year pairs, quotas, and school boundaries are row errors.

## Bounds and transaction behavior

Credential CSV preserves the exact initial password, including spaces and leading punctuation. When opening it in a spreadsheet, import the `initialPassword` column as text. Profile/reference fields escape spreadsheet formula prefixes; generated passwords begin with `Pl!`.

Each request is limited to 200 rows and 1 MiB of CSV or row values. Fields are bounded to 10,000 characters, and only the resource's documented columns are accepted. School IDs and database IDs cannot be supplied as CSV columns.

Commit locks and refreshes the school, then refreshes referenced class and user rows under pessimistic write locks. It revalidates all rows, quotas, active status, and references before the first write. Creation and assignment reuse the existing account/class services inside one transaction. Any row failure rolls back the entire batch; no partial-success import remains. An expired school license prevents SCHOOL or department-head commits. MANAGER retains its administrative license bypass but cannot create classes.

The former immediate `POST /reports/users/import` endpoint is removed. Tests use synthetic entities and CSV files; they never import into the live database. Parser tests cover BOM/Unicode, quoted fields, multiline cells, malformed input, and bounds. Service tests cover duplicates, role/school scope, signatures, changed quota/classes, account metadata, and rollback through Spring transaction advice.
