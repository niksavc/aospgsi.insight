package nks.aospgsi.insight.core.monetization

/** A place where people can support the project (donations, sponsorship). */
data class SupportLink(val label: String, val url: String)

/**
 * Where to send supporters. Fill these in before release; the support card stays hidden while the
 * list is empty, so nothing points at a page that does not exist. Money is for the manager itself
 * (its code, Pro features and support), never for downloading proprietary software.
 */
object ProjectLinks {
  val support: List<SupportLink> = emptyList()

  /** Links that are well-formed https URLs; anything else is dropped. */
  fun valid(links: List<SupportLink> = support): List<SupportLink> = links.filter {
    it.label.isNotBlank() && it.url.startsWith("https://")
  }
}
