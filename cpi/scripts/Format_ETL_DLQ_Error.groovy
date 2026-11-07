import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant
import java.util.UUID

def Message processData(Message message) {
    def properties = message.getProperties()
    def ex = properties.get("CamelExceptionCaught")
    
    String rawError = ex ? ex.getMessage() : "Legacy PI/PO ETL Pipeline Transformation or Dispatch Failure"
    String rawPayload = properties.get("Original_Raw_Data") ? properties.get("Original_Raw_Data").toString() : "{}"
    String corrId = (properties.get("CorrelationID") ?: UUID.randomUUID().toString()).toString()
    String srcSystem = (properties.get("SourceSystem") ?: "LEGACY_SQL_DB").toString()

    def dlqEnvelope = [
        "DLQ_Header": [
            "Category"      : "LEGACY_PIPO_ETL_EXCEPTION",
            "Timestamp"     : Instant.now().toString(),
            "CorrelationId" : corrId,
            "SourceSystem"  : srcSystem,
            "TargetSystem"  : "SAP_BUILD_WORKFLOW"
        ],
        "ErrorDetails": [
            "FaultReason"   : rawError,
            "ActionRequired": "Inspect database extract columns or verify SAP Build tenant OAuth credentials"
        ],
        "QuarantinedPayload": rawPayload
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(dlqEnvelope)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("DLQ_Status", "FAILED_ROUTED_TO_SFTP")
    return message
}
