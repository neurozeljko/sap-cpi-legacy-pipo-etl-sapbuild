import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.math.BigDecimal
import java.math.RoundingMode

def Message processData(Message message) {
    String body = message.getBody(String.class)
    def jsonSlurper = new JsonSlurper()
    def legacyRecords = jsonSlurper.parseText(body)

    def recordsList = legacyRecords instanceof List ? legacyRecords : [legacyRecords]
    def normalizedBatch = []
    BigDecimal totalBatchAmount = BigDecimal.ZERO
    int validCount = 0
    int invalidCount = 0

    recordsList.eachWithIndex { record, idx ->
        String rawAccount = (record.GL_ACCOUNT ?: "").toString().trim().replaceAll("[^0-9]", "")
        String costCenter = (record.COST_CENTER ?: "").toString().trim().toUpperCase()
        String rawAmount = (record.AMOUNT ?: "0.0").toString().trim().replace(",", ".")
        String currency = (record.CURRENCY ?: "EUR").toString().trim().toUpperCase()
        String referenceDoc = (record.LEGACY_REF_NO ?: "LEGACY-REF-${idx + 1}").toString().trim()

        BigDecimal parsedAmount
        try {
            parsedAmount = new BigDecimal(rawAmount).setScale(2, RoundingMode.HALF_UP)
        } catch (Exception ex) {
            parsedAmount = BigDecimal.ZERO
        }

        boolean isValid = rawAccount.length() >= 6 && costCenter.length() > 0 && parsedAmount > BigDecimal.ZERO
        if (isValid) {
            validCount++
            totalBatchAmount = totalBatchAmount.add(parsedAmount)
        } else {
            invalidCount++
        }

        normalizedBatch.add([
            "ItemIndex"         : idx + 1,
            "LegacyReference"   : referenceDoc,
            "CleanGLAccount"    : rawAccount.padLeft(10, "0"),
            "TargetCostCenter"  : costCenter ?: "CC_DEFAULT",
            "NormalizedAmount"  : parsedAmount.doubleValue(),
            "Currency"          : currency,
            "ValidationStatus"  : isValid ? "PASSED" : "FAILED_SCHEMA",
            "RequiresAuditFlag" : parsedAmount > 10000.0
        ])
    }

    def etlSummary = [
        "PipelineMetadata": [
            "SourceEngine"      : "LEGACY_JDBC_SQL",
            "TargetWorkflow"    : "SAP_BUILD_PROCESS_AUTOMATION",
            "BatchRecordCount"  : recordsList.size(),
            "ValidRecords"      : validCount,
            "InvalidRecords"    : invalidCount,
            "TotalBatchValue"   : totalBatchAmount.doubleValue(),
            "BatchCurrency"     : "EUR",
            "RequiresApproval"  : totalBatchAmount > 50000.0 || invalidCount > 0
        ],
        "CleansedItems": normalizedBatch
    ]

    message.setProperty("BatchValidCount", validCount)
    message.setProperty("RequiresApproval", etlSummary.PipelineMetadata.RequiresApproval)
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(etlSummary)))
    message.setHeader("Content-Type", "application/json")

    return message
}
