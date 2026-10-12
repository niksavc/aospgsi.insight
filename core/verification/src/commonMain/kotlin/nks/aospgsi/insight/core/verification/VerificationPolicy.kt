package nks.aospgsi.insight.core.verification

data class VerificationReport(
  val archiveReadable: Boolean,
  val signatureVerified: Boolean,
  val sha256: String?,
  val warnings: List<String>,
)

object VerificationPolicy {
  fun shouldAllowInstall(report: VerificationReport): Boolean =
    report.archiveReadable && report.signatureVerified
}
