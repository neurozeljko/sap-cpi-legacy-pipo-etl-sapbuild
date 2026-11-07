import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.UUID

def Message processData(Message message) {
    String body = message.getBody(String.class)
    def jsonSlurper = new JsonSlurper()
    def etlData = jsonSlurper.parseText(body)

    String correlationId = (message.getProperty("CorrelationID") ?: UUID.randomUUID().toString()).toString()
    def metadata = etlData.PipelineMetadata ?: [:]
    def items = etlData.CleansedItems ?: []

    def sapBuildPayload = [
        "definitionId": "legacy.pipo.migration.batch.approval",
        "context": [
            "batchTrackingId"     : "BATCH-ETL-" + correlationId.take(8).toUpperCase(),
            "sourceSystem"        : "LEGACY_ONPREMISE_JDBC",
            "targetSystem"        : "S4HANA_CLOUD_FIN_POSTING",
            "batchSummary"        : [
                "recordCount"     : metadata.BatchRecordCount ?: items.size(),
                "validRecords"    : metadata.ValidRecords ?: 0,
                "invalidRecords"  : metadata.InvalidRecords ?: 0,
                "totalBatchValue" : metadata.TotalBatchValue ?: 0.0,
                "currency"        : metadata.BatchCurrency ?: "EUR"
            ],
            "approvalGovernance"  : [
                "requiresManualReview" : metadata.RequiresApproval ?: false,
                "approvalThresholdEUR" : 50000.0,
                "targetPortal"         : "SAP Build Work Zone - Finance Tasks"
            ],
            "stagingRecords"      : items
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(sapBuildPayload)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("Accept", "application/json")

    return message
}
