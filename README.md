# Enterprise Integration Architecture: Legacy PI/PO Migration & Modern ETL Data Pipeline (JDBC & SAP Build)

[![SAP Integration Suite](https://img.shields.io/badge/SAP%20Integration%20Suite-Cloud%20Integration-0A85EA?style=for-the-badge&logo=sap&logoColor=white)](https://www.sap.com)
[![Legacy Migration](https://img.shields.io/badge/Migration-SAP%20PI%2FPO%20Decommissioning-red?style=for-the-badge&logo=sap&logoColor=white)](https://www.sap.com)
[![SAP Build](https://img.shields.io/badge/Automation-SAP%20Build%20Process%20Automation-purple?style=for-the-badge&logo=sap&logoColor=white)](https://www.sap.com)
[![ETL / ELT](https://img.shields.io/badge/Data%20Pipeline-ETL%20%2F%20Cleansing-brightgreen?style=for-the-badge)](https://www.sap.com)
[![Clean Core](https://img.shields.io/badge/Clean%20Core-OData%20V4%20%7C%20No%20Z--Tables-success?style=for-the-badge&logo=sap&logoColor=white)](https://www.sap.com)

---

## Executive Summary

This production-grade integration blueprint modernizes and replaces mission-critical legacy **SAP Process Integration / Process Orchestration (PI/PO)** data feeds. Historically, transactional batches were extracted via on-premise JDBC direct database queries and posted synchronously through legacy ABAP BAPI calls, causing tight ERP coupling and technical debt.

This architecture deploys a decoupled, cloud-native **ETL Data Pipeline** on **SAP Integration Suite**:
1. **Extraction:** Secure polled extraction from on-premise relational databases via **SAP Cloud Connector** and JDBC.
2. **Transformation:** In-flight batch cleansing, sanitization, whitespace elimination, and schema validation executed via optimized streaming Groovy scripts.
3. **Governance:** Human-in-the-loop task routing via **SAP Build Process Automation** and **SAP Build Work Zone** dashboards for high-value transactional audit.
4. **Target Dispatch:** Clean Core asynchronous execution posting into **SAP S/4HANA Cloud** via standardized OData V4 services.

---

## Architectural Topology

```
[Legacy On-Premise Relational DB]
       │
       │ Polled SQL Extract (JDBC / Direct DB Connection)
       ▼
[SAP Cloud Connector (Encrypted Secure DMZ Tunnel)]
       │
       │ Mutual TLS (mTLS)
       ▼
[SAP Integration Suite (Cloud Integration Pipeline)]
 │
 ├── 1. Inbound Ingestion Layer (/legacy/pipo/pipeline/ingest):
 │    └─ Content Modifier: CM_Init_ETL_Context
 │         ├─ Preserves original database extract in property
 │         └─ Injects correlation headers (SAP_MessageProcessingLogID)
 │
 ├── 2. Data Cleansing & Validation Engine:
 │    └─ Groovy Script: Cleanse_Validate_Legacy_Data.groovy
 │         ├─ Strips non-numeric noise from GL accounts & normalizes cost centers
 │         ├─ Rounds amounts with BigDecimal precision (Half-Up)
 │         └─ Sets approval thresholds (> 50,000 EUR or schema discrepancies)
 │
 ├── 3. SAP Build Context Generation:
 │    └─ Groovy Script: Prepare_SAP_Build_Trigger.groovy
 │         ├─ Normalizes staging records for Workflow API (/v1/workflow-instances)
 │         └─ Injects target Work Zone UI metadata and business tracking keys
 │
 ├── 4. Workflow Instantiation:
 │    └─ HTTP POST -> SAP Build Process Automation REST Service
 │
 └── 5. Exception Subprocess (ETL Quarantine DLQ):
      ├─ Catches schema malformations or downstream ERP timeouts
      ├─ Groovy Formatter: Format_ETL_DLQ_Error.groovy
      └─ Outbound SFTP Quarantine -> /dlq/pipo/migration/failed/
```

---

## Technical Specifications

| Architectural Dimension | Legacy Implementation (SAP PI/PO) | Modern BTP Implementation (Integration Suite) |
| :--- | :--- | :--- |
| **Integration Platform** | SAP Dual-Stack PI 7.5 / PO Single-Stack | SAP Integration Suite (Cloud Integration multi-tenant) |
| **Database Connectivity** | Classical JDBC Adapter with local DB drivers | Secure Cloud Connector tunnel with virtual mapping |
| **Data Cleansing** | User-Defined Functions (Java UDF in Message Mapping) | High-performance Groovy Streaming with BigDecimal accuracy |
| **Approval Flow** | Custom ABAP Workflow / Business Workplace (SBWP) | SAP Build Process Automation & SAP Build Work Zone portal |
| **ERP Persistence** | Deprecated Synchronous RFC / BAPI calls | Asynchronous Clean Core OData V4 Services |
| **Exception Handling** | Manual SXMB_MONI / Alert Inbox triage | Automated Exception Subprocess routing to SFTP DLQ |

---

## Cleansed Target Data Contract (JSON)

```json
{
  "ItemIndex": 1,
  "LegacyReference": "DOC-SQL-991204",
  "CleanGLAccount": "0000400100",
  "TargetCostCenter": "CC_FIN_2026",
  "NormalizedAmount": 12500.50,
  "Currency": "EUR",
  "ValidationStatus": "PASSED",
  "RequiresAuditFlag": true
}
```

---

## Repository Structure

```
├── README.md                                          # Architectural blueprint and runbook
├── ADR-001-Legacy-PIPO-ETL-SAPBuild-Migration.md      # Architecture Decision Record
├── IF_Legacy_PIPO_JDBC_ETL_SAPBuild_Migration.zip     # Production iFlow runtime bundle export
├── cpi/
│   └── scripts/
│       ├── Cleanse_Validate_Legacy_Data.groovy        # High-performance ETL cleansing engine
│       ├── Prepare_SAP_Build_Trigger.groovy          # SAP Build Process Automation payload formatter
│       └── Format_ETL_DLQ_Error.groovy                # Dead-letter queue incident transformer
├── tests/
│   └── sample_legacy_jdbc_records.json                # Sample legacy database extract payload
└── .gitignore                                         # Git exclusion rules
```

---

## Verification & Deployment Runbook

### 1. Inbound Endpoint Verification
Verify runtime status in **Monitor** ──► **Manage Integration Content**:
```text
https://<tenant-runtime>.cfapps.<region>[.hana.ondemand.com/http/legacy/pipo/pipeline/ingest](https://.hana.ondemand.com/http/legacy/pipo/pipeline/ingest)
```

### 2. Dispatched cURL Test
Execute an authorized POST request using sample legacy records:

```bash
curl -X POST \
  'https://<tenant-runtime>.cfapps.<region>[.hana.ondemand.com/http/legacy/pipo/pipeline/ingest](https://.hana.ondemand.com/http/legacy/pipo/pipeline/ingest)' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <BTP_OAUTH_TOKEN>' \
  --data-binary '@tests/sample_legacy_jdbc_records.json'
```

### 3. Monitoring Trace
* Inspect message execution in **Monitor Message Processing**.
* Verify data validation statistics appended in message properties.
* Confirm mock SAP Build Process Automation response returns HTTP 200 with status `RUNNING`.

---

## Author

**Željko Popov**  
*Integration Architect | SAP Integration Suite & Clean Core Specialist*  
[GitHub Repositories](https://github.com/neurozeljko)
