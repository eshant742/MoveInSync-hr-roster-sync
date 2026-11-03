# MoveInSync SDE Intern Case Study 5: HR Roster Sync
# Complete Interview Preparation & Master Notes File

---

## 📌 QUICK REFERENCE CHEAT SHEET
* **Project Directory:** `D:\miscasestudy`
* **Docker Start Command:** `docker compose up -d`
* **Docker Check Command:** `docker compose ps`
* **Swagger UI URL:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
* **Prometheus Metrics:** [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)
* **Admin Credentials (Full Access):** Username: `admin` | Password: `admin_access_key`
* **Operator Credentials (Actuator / Recon):** Username: `operator` | Password: `operator_access_key`
* **GitHub Repository:** `https://github.com/eshant742/MoveInSync-hr-roster-sync`

---

## 📑 TABLE OF CONTENTS
1. [MoveInSync Company & Business Context](#1-company--business-context)
2. [What is Case Study 5: HR Roster Sync?](#2-what-is-case-study-5-hr-roster-sync)
3. [The 4 Core Real-World Catastrophes Solved](#3-the-4-core-real-world-catastrophes-solved)
4. [Step-by-Step Pre-Interview Runbook](#4-step-by-step-pre-interview-runbook)
5. [Complete Swagger UI Guide (All 10 Endpoints)](#5-complete-swagger-ui-guide)
6. [The 6 Core Technical Pillars & Implementation Details](#6-the-6-core-technical-pillars)
7. [Top 25 Technical Interview Questions & Model Answers](#7-top-25-technical-interview-questions--answers)
8. [Live Demo Walkthrough Script](#8-live-demo-walkthrough-script)

---

## 1. Company & Business Context

### What Does MoveInSync Do?
MoveInSync is the global pioneer and largest SaaS platform for **enterprise employee commute automation**. Major clients include **Google, Microsoft, Amazon, Infosys, TCS, and Standard Chartered**.

### Key Product Suite:
1. **Employee Transport Solution (ETS) — Flagship Product:**
   * Automated routing and clustering: Groups employees living in the same neighborhood into a single cab to minimize commute time and fuel burn.
   * Automated driver dispatching, turn-by-turn navigation, and digital trip sheets.
2. **Safety & Security Suite:**
   * Real-time GPS tracking of every vehicle.
   * In-app SOS panic buttons.
   * **"Safe Drop" confirmation:** Cab drivers cannot depart until female employees confirm safe entry into their homes late at night via OTP or call confirmation. Female security marshals accompany night-shift cabs.
3. **MoveInSync Shuttle:** Fixed-route corporate buses and AC shuttles connecting metro stations to tech campuses.
4. **MoveInSync Ion:** 100% electric vehicle (EV) corporate fleets to help enterprises meet Net-Zero carbon goals.
5. **MoveInSync Workplace:** Hot-desking, parking bay booking, and cafeteria management for hybrid workforces.

### 💡 What to say to the interviewer if asked: "What do you know about MoveInSync?"
> *"MoveInSync is the global leader in enterprise employee commute automation. While the core flagship product is automated cab and shuttle routing for daily home-to-office commutes with an emphasis on employee safety, MoveInSync also provides EV fleets (Ion), office shuttle tracking, and workplace management solutions like desk and parking booking for hybrid teams."*

---

## 2. What is Case Study 5: HR Roster Sync?

* **The Problem:** In corporate commute operations, the client company’s **HR system (e.g., Workday, Darwinbox)** is the master source of truth for employment (hires, resignations, department transfers, shift changes).
* **MoveInSync’s Role:** MoveInSync must reflect these changes in real-time and nightly so the cab dispatch engine knows **who gets a cab tomorrow, at what time, and at which house**.
* **Why This is Not a Simple CRUD App:** Real-world enterprise pipelines suffer from network timeouts, duplicate event retries, out-of-order event delivery, truncated nightly files, and crash failures between database commits and message broker publishing.

---

## 3. The 4 Core Real-World Catastrophes Solved

### Catastrophe 1: Duplicate Event Retries (Idempotency)
* **The Danger:** Network timeouts cause the HR system to retry the same HTTP event 3 times. A naive system would create 3 duplicate employee rows or corrupt audit trails.
* **Our Solution:** Logical version check in `EmployeeEventService.java`:
  `if (incoming.version == existing.version) -> Return DUPLICATE_SKIPPED`.

### Catastrophe 2: Out-of-Order Packet Delivery
* **The Danger:** An employee's transfer to Bangalore (v7) arrives *before* their promotion (v6) due to internet routing delays. A naive system applies v6 last, moving the employee back to the old office.
* **Our Solution:** Logical version comparison:
  `if (incoming.version < existing.version) -> Return STALE_SKIPPED`. The database only accepts newer versions (`v > existing.version`).

### Catastrophe 3: Truncated Nightly Export (The 50,000 Cabs Disaster)
* **The Danger:** HR uploads a nightly CSV of 200,000 employees. An SFTP network glitch cuts the file at 120,000 rows. A naive reconciliation script assumes the missing 80,000 people were fired and deactivates them, canceling all their morning cabs!
* **Our Solution (Safety Brake Circuit Breaker):**
  We calculate `missingPercentage = (activeInStore - matchedInExport) / activeInStore`.
  If `missingPercentage > 0.30` (30% threshold), the system freezes all deactivations, trips status `SAFETY_BRAKE_TRIGGERED`, and halts until a human operator inspects the file and calls `/api/v1/reconciliation/approve`.

### Catastrophe 4: Conflicting Field Ownership (Wheelchair & Pickup Points)
* **The Danger:** HR only knows corporate fields (Name, Dept, Shift). HR knows nothing about cabs. MoveInSync Transport Admins configure physical attributes: Gate 2 pickup, wheelchair ramps. If HR sends an update for an employee, a naive system overwrites the whole row, erasing the wheelchair ramp request!
* **Our Solution:** Strict field ownership isolation. HR events update *only* HR fields (`firstName`, `department`, `shiftCode`). Transport fields (`pickupPoint`, `specialAssistanceNeeds`) are managed via an independent endpoint (`PUT /api/v1/employees/transport-fields`) and are never overwritten by HR data.

---

## 4. Step-by-Step Pre-Interview Runbook

### 10 Minutes Before Your Interview:
1. **Open Docker Desktop:** Confirm the bottom-left whale icon shows **`Engine running`** (green dot).
2. **Open PowerShell & Start Containers:**
   ```powershell
   cd D:\miscasestudy
   docker compose up -d
   ```
3. **Verify Status:**
   ```powershell
   docker compose ps
   ```
   Confirm both `roster-sync-db` and `roster-sync-app` show **`Up (healthy)`**.
4. **Open Chrome:**
   Go to: `http://localhost:8080/swagger-ui.html`
5. **Log In (Authorize):**
   Click the green **`Authorize 🔓`** button at the top-right of Swagger UI.
   * **Username:** `admin`
   * **Password:** `admin_access_key`
   * Click **Authorize** -> Click **Close**.
6. **Leave this tab open on your screen!** When the interviewer asks you to share your screen, Swagger UI is already live, logged in, and ready to demonstrate!

---

## 5. Complete Swagger UI Guide

### 📂 Group 1: Employee Events & Profile

#### 1. 🟢 `POST /api/v1/employees/events`
* **Purpose:** Daytime real-time stream of employee lifecycle changes (CREATE, UPDATE, DEACTIVATE).
* **Role Required:** `ADMIN`
* **Key Feature:** Partial batch resilience (if record 5 has an invalid email, records 1-4 and 6-500 still succeed).

#### 2. 🟠 `PUT /api/v1/employees/transport-fields`
* **Purpose:** Updates transport-admin customizations (`pickupPoint`, `specialAssistanceNeeds`).
* **Role Required:** `ADMIN` (Transport Admin)
* **Key Feature:** Strictly updates *only* transport columns; HR fields and HR versions are untouched.

---

### 📂 Group 2: Nightly Reconciliation

#### 3. 🟢 `POST /api/v1/reconciliation/upload/{tenantId}`
* **Purpose:** Uploads the 200,000-record nightly CSV export file for database reconciliation.
* **Role Required:** `ADMIN` or `OPERATOR`
* **Key Feature:** Streaming bounded-memory diff ($O(1)$ memory). Automatically trips the **Safety Brake** if missing records exceed 30%.

#### 4. 🟢 `POST /api/v1/reconciliation/approve`
* **Purpose:** Human-in-the-loop approval endpoint to unpause a safety-braked reconciliation run.
* **Role Required:** `OPERATOR`

#### 5. 🔵 `GET /api/v1/reconciliation/history/{tenantId}`
* **Purpose:** Audit log showing past nightly reconciliation runs, statistics, and safety brake triggers.

---

### 📂 Group 3: Employee Queries

#### 6. 🔵 `GET /api/v1/employees/{tenantId}`
* **Purpose:** Paginated list of all employees in a tenant (`page`, `size`, `sortBy`).

#### 7. 🔵 `GET /api/v1/employees/{tenantId}/{employeeId}`
* **Purpose:** Fetch a single employee profile.
* **Key Feature:** Backed by **Caffeine in-memory cache** for sub-millisecond response time during morning peak cab routing.

#### 8. 🔵 `GET /api/v1/employees/{tenantId}/errors`
* **Purpose:** Audit log of rejected/failed ingestion records.
* **Key Feature:** **Zero PII leakage** — stores only `employeeId` and technical error reason. Never stores names, emails, or phone numbers.

---

### 📂 Group 4: Operations & Diagnostics

#### 9. 🔵 `GET /api/v1/operations/status`
* **Purpose:** System health, version, and outbox queue depth (`outboxPendingCount: 0`).

#### 10. 🟢 `POST /api/v1/operations/outbox/publish`
* **Purpose:** Emergency manual flush of the Transactional Outbox table.

---

## 6. The 6 Core Technical Pillars

| Pillar | Technical Mechanism | Code Location |
|---|---|---|
| **1. Idempotency** | Logical `version` check + DB unique constraint `(employee_id, tenant_id)` | `EmployeeEventService.java` (L201-218) |
| **2. Field Ownership** | `applyHrFields()` updates HR columns only; `TransportFieldsService` updates transport columns only | `Employee.java` (L133-160), `TransportFieldsService.java` |
| **3. Bounded Memory** | Apache Commons CSV streaming parser + chunked JDBC batches (`batch_size=50`) with `entityManager.clear()` | `ReconciliationService.java` |
| **4. Safety Brake** | `missingPercentage = missing / activeStore`. If $\ge 30\%$, halt deactivations | `ReconciliationService.java` (L194-227) |
| **5. Transactional Outbox** | Writes `OutboxEvent` in same `@Transactional` boundary; `@Scheduled` poller dispatches | `OutboxPublisherService.java`, `OutboxEvent.java` |
| **6. Privacy (No PII)** | Error entity stores only `employee_id` and error codes; regex sanitization in exception handler | `GlobalExceptionHandler.java`, `IngestionError.java` |

---

## 7. Top 25 Technical Interview Questions & Answers

### Q1: Why did you choose PostgreSQL over MongoDB?
> **Answer:** "HR roster synchronization demands strict ACID transactions. We require transactional consistency for the Transactional Outbox Pattern (writing the employee and outbox event in the same atomic commit). Furthermore, PostgreSQL's composite unique constraints (`employee_id, tenant_id`) guarantee idempotency under high concurrency at the storage engine level."

### Q2: What is the Dual-Write problem, and how did you solve it?
> **Answer:** "If an application updates a database and then publishes to Kafka/RabbitMQ in two separate network calls, a crash between the two calls causes permanent data drift—the database updated, but the cab dispatch engine never received the notification.
> We solve this using the Transactional Outbox Pattern: the employee update and the `OutboxEvent` are committed in the exact same local database transaction. A background poller reads unpublished outbox events and dispatches them, guaranteeing At-Least-Once Delivery."

### Q3: What is the difference between your logical `version` and JPA's `@Version`?
> **Answer:** "They serve two completely different purposes:
> - **Logical `version` (`hrVersion`):** An application-level sequence number from the HR system that models real-world business event order (v1 = Onboarding, v2 = Shift change, v3 = Transfer). We use it to detect out-of-order and duplicate events across network retries.
> - **JPA `@Version` (`jpaVersion`):** A technical optimistic locking mechanism used by Hibernate to detect concurrent database row updates within our microservice. If two threads fetch the same row and try to commit at the same millisecond, Hibernate throws an `OptimisticLockException`."

### Q4: How did you ensure 200,000 records can be reconciled without causing an OutOfMemoryError (OOM)?
> **Answer:** "A naive approach that loads 200k CSV rows and 200k DB rows into Java heap as `List<Employee>` would consume hundreds of megabytes of RAM and trigger GC pauses. Instead, we stream the CSV file record-by-record using Apache Commons CSV, process records sequentially, and execute database operations in JDBC batches of 50 while periodically clearing the Hibernate persistence context (`entityManager.clear()`). This keeps memory consumption bounded under 64MB regardless of file size."

### Q5: How do you protect employee privacy (PII)?
> **Answer:** "MoveInSync handles sensitive employee data (names, emails, phone numbers, home pickup points). In accordance with GDPR and DPDP privacy regulations, our `ingestion_errors` table and exception handler persist ONLY `employeeId`, `tenantId`, `errorType`, and sanitized error codes. Personal names, emails, and phone numbers are never stored in error logs."

### Q6: How does your system handle two identical events arriving at the exact same millisecond?
> **Answer:** "If two concurrent threads attempt to insert the same new employee simultaneously, the second thread trips the database unique constraint `uk_employee_tenant (employee_id, tenant_id)` with a `DataIntegrityViolationException`. If it is an update, Hibernate's `@Version` optimistic lock throws `OptimisticLockException`. We catch these exceptions, reload the committed record, verify the event is already applied, and return a clean idempotent response with `duplicateCount: 1`."

### Q7: What happens when an employee is deactivated?
> **Answer:** "Deactivation triggers critical downstream side effects via `DeactivationSideEffectService`:
> 1. An outbox event `EMPLOYEE_DEACTIVATED` is emitted so the cab scheduling engine cancels future scheduled cab rides.
> 2. An operational alert is logged to notify transport dispatchers to re-route shared cabs.
> 3. The employee record is marked `active = false` with an updated timestamp, preserving historical auditability."

### Q8: How would you scale this system if the CSV file had 10 million rows instead of 200,000?
> **Answer:** "For 10M rows, in-memory processing on a single node is no longer suitable. I would transition to **External Sort Merge Diff**:
> 1. Upload CSV to S3 / Object Store.
> 2. Stream and sort both the export file and active database IDs using disk-backed chunked merge sort.
> 3. Perform a linear two-pointer streaming diff in $O(N)$ time with $O(1)$ memory.
> 4. Dispatch diff chunks into Kafka to be processed by a distributed worker pool."

---

## 8. Live Demo Walkthrough Script

When the interviewer asks you to run the project live, follow this exact sequence:

### Step 1: Show System Status
* In Swagger UI, expand **`Operations & Diagnostics`** -> **`GET /api/v1/operations/status`**.
* Click **Try it out** -> Click **Execute**.
* Response returns `200 OK`:
  ```json
  {
    "status": "UP",
    "version": "1.0.0",
    "outboxPendingCount": 0
  }
  ```
* **Say:** *"This confirms the service is live, connected to PostgreSQL, and the Transactional Outbox has 0 pending lag."*

### Step 2: Ingest an Employee Event
* Expand **`Employee Events & Profile`** -> **`POST /api/v1/employees/events`**.
* Click **Try it out**. Paste payload:
  ```json
  {
    "events": [
      {
        "employeeId": "EMP-900",
        "tenantId": "tenant-001",
        "eventType": "CREATE",
        "version": 1,
        "firstName": "Siddharth",
        "lastName": "Mehta",
        "email": "siddharth.mehta@company.com",
        "phone": "+919876543210",
        "managerId": "MGR-01",
        "officeCode": "BLR-01",
        "department": "Engineering",
        "shiftCode": "SHIFT-M"
      }
    ]
  }
  ```
* Click **Execute** -> Returns `200 OK` (`status: CREATED, successCount: 1`).

### Step 3: Demonstrate Idempotency (Duplicate Skipping)
* Without changing anything, click **Execute** again on the exact same payload.
* Response returns `200 OK`:
  ```json
  {
    "totalReceived": 1,
    "successCount": 0,
    "duplicateCount": 1,
    "results": [
      {
        "employeeId": "EMP-900",
        "status": "DUPLICATE",
        "message": "Record already exists with identical version 1"
      }
    ]
  }
  ```
* **Say:** *"Notice that duplicate network retries are detected and safely skipped without corrupting the database state."*

### Step 4: Update Transport Fields (Wheelchair & Pickup)
* Expand **`Employee Events & Profile`** -> **`PUT /api/v1/employees/transport-fields`**.
* Click **Try it out**. Paste payload:
  ```json
  {
    "employeeId": "EMP-900",
    "tenantId": "tenant-001",
    "pickupPoint": "Gate 3, Tech Park Outer Ring Road",
    "specialAssistanceNeeds": "Wheelchair ramp required"
  }
  ```
* Click **Execute** -> Returns `200 OK` showing the transport attributes updated.
* **Say:** *"This demonstrates field ownership separation. Transport admins configure physical cab pickup locations and accessibility needs independently of HR systems."*

### Step 5: Demonstrate the Safety Brake
* Expand **`Nightly Reconciliation`** -> **`POST /api/v1/reconciliation/upload/{tenantId}`**.
* Click **Try it out**.
* Set `tenantId`: `tenant-001`.
* In `file`, select **`sample_broken_export.csv`** from your project folder.
* Click **Execute**.
* Response returns:
  ```json
  {
    "status": "SAFETY_BRAKE_TRIGGERED",
    "safetyBrakeTriggered": true,
    "missingPercentage": 0.40,
    "message": "Safety brake triggered: missing employee percentage exceeds threshold. Awaiting human operator approval."
  }
  ```
* **Say:** *"Because this file had a sudden 40% drop in active headcount, the safety brake circuit breaker automatically tripped, halting all deactivations to prevent thousands of employees from losing their morning office cabs."*

---

*End of Notes File. All code and configurations are verified and running live.*
