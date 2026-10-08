# Automated test results

Run on 2026-10-08 20:19 | Java 26.0.1 | Windows 11

Produced by `com.bms.AllChecks`. Every number below was read from the output of the real check programs; each program adds its own test rows, tests the rules, and removes its rows again.

| Feature area | Check program | Checks | Passed | Failed | Result | Seconds |
|---|---|---:|---:|---:|---|---:|
| Database rules (constraints) | `SchemaCheck` | 5 | 5 | 0 | PASS | 0,4 |
| Login and passwords | `AuthServiceCheck` | 15 | 15 | 0 | PASS | 1,0 |
| Dashboard | `DashboardServiceCheck` | 18 | 18 | 0 | PASS | 0,9 |
| Customers | `CustomerServiceCheck` | 27 | 27 | 0 | PASS | 0,9 |
| Products | `ProductServiceCheck` | 60 | 60 | 0 | PASS | 1,0 |
| Suppliers | `SupplierServiceCheck` | 43 | 43 | 0 | PASS | 1,0 |
| Employees and user accounts | `EmployeeServiceCheck` | 82 | 82 | 0 | PASS | 2,2 |
| Inventory and stock history | `InventoryServiceCheck` | 44 | 44 | 0 | PASS | 1,0 |
| Orders (create, cancel, permissions) | `OrderServiceCheck` | 75 | 75 | 0 | PASS | 1,9 |
| Reports | `ReportServiceCheck` | 51 | 51 | 0 | PASS | 1,5 |
| Search and filtering | `SearchFilterCheck` | 51 | 51 | 0 | PASS | 1,9 |
| Validation and error handling | `ErrorHandlingCheck` | 59 | 59 | 0 | PASS | 20,4 |
| Volume and speed (thousands of rows) | `VolumeCheck` | 36 | 36 | 0 | PASS | 1,2 |
| Data integrity of the whole database | `DataIntegrityCheck` | 21 | 21 | 0 | PASS | 0,5 |
| **Total** | | **587** | **587** | **0** | **PASS** | |
