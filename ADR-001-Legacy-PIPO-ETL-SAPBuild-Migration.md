# ADR-001: Legacy PI/PO Decommissioning via Modern ETL Data Pipeline and SAP Build Automation

## Status
Accepted

## Context
Legacy SAP PI/PO interfaces using direct on-premise JDBC extraction and synchronous BAPIs must be retired to align with SAP Clean Core architecture and eliminate technical debt.

## Decision
1. **Decoupled Extraction:** JDBC connections are tunneled through SAP Cloud Connector using virtual mappings.
2. **Groovy ETL Engine:** In-flight normalization, mathematical precision handling, and validation are executed on SAP Integration Suite.
3. **Governance:** Batches exceeding threshold limits require manual review inside SAP Build Work Zone via SAP Build Process Automation.
4. **Clean Core Posting:** Approved entries post to S/4HANA via asynchronous OData V4 services rather than legacy synchronous RFCs.
