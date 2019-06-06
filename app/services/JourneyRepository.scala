package services

import javax.inject.Singleton
import com.google.inject.ImplementedBy
import com.typesafe.config.{ConfigObject, ConfigValue}
import config.{AddressLookupFrontendSessionCache, FrontendAppConfig, FrontendServicesConfig}
import model._
import uk.gov.hmrc.http.cache.client.HttpCaching

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

@ImplementedBy(classOf[KeystoreJourneyRepository])
trait JourneyRepository {

  def init(journeyName: String): JourneyData

  def get(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JourneyData]]

  def put(id: String, data: JourneyData)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]

}

@Singleton
class KeystoreJourneyRepository extends JourneyRepository with FrontendServicesConfig {

  val cacheId = "journey-data"

  private val cfg: Map[String, JourneyData] = config("address-lookup-frontend").getObject("journeys").map { journeys =>
    journeys.keySet().asScala.map { key =>
      (key -> journey(key, journeys))
    }.toMap
  }.getOrElse(Map.empty)

  val cache: HttpCaching = AddressLookupFrontendSessionCache

  override def init(journeyName: String): JourneyData = {
    try {
      cfg.get(journeyName).get
    } catch {
      case none: NoSuchElementException => throw new IllegalArgumentException(s"Invalid journey name: '$journeyName'", none)
    }
  }

  override def get(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JourneyData]] = {
      cache.fetchAndGetEntry[JourneyData](cache.defaultSource, cacheId, id)
  }

  override def put(id: String, data: JourneyData)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    cache.cache(cache.defaultSource, cacheId, id, data).map { res =>
      true
    }
  }

  def convertToV2Model(v1: JourneyData): JourneyDataV2 = {
    val journeyConfig = JourneyConfigV2 (
      version = FrontendAppConfig.apiVersion2,
      options = resolveJourneyOptions(v1.config),
      labels = resolveLabels(v1.config.navTitle, v1.config.phaseBannerHtml, v1.config.lookupPage, v1.config.selectPage,
        v1.config.editPage, v1.config.confirmPage)
    )

    JourneyDataV2(journeyConfig, v1.proposals, v1.selectedAddress, v1.confirmedAddress)
  }

  private def resolveJourneyOptions(v1: JourneyConfig): JourneyOptions = {
    JourneyOptions(
      continueUrl = v1.continueUrl,
      homeNavHref = v1.homeNavHref,
      additionalStylesheetUrl =  v1.additionalStylesheetUrl,
      phaseFeedbackLink = v1.phaseFeedbackLink,
      deskProServiceName = v1.deskProServiceName,
      showPhaseBanner = v1.showPhaseBanner,
      alphaPhase = v1.alphaPhase,
      showBackButtons = v1.showBackButtons,
      includeHMRCBranding = v1.includeHMRCBranding,
      ukMode = v1.ukMode,
      allowedCountryCodes = v1.allowedCountryCodes,
      selectPageConfig = resolveSelectPageConfig(v1.selectPage),
      confirmPageConfig = resolveConfirmPageConfig(v1.confirmPage),
      timeoutConfig = resolveTimeoutConfig(v1.timeout)
    )
  }

  private def resolveLabels(optNavTitle: Option[String],
                            optPhaseBannerHtml: Option[String],
                            optLookupPage: Option[LookupPage],
                            optSelectPage: Option[SelectPage],
                            optEditPage: Option[EditPage],
                            optConfirmPage: Option[ConfirmPage]): Option[JourneyLabels] = {

    val appLabels = (optNavTitle, optPhaseBannerHtml) match {
      case (None, None) => None
      case _ => Some(AppLevelLabels(optNavTitle, optPhaseBannerHtml))
    }

    val lookupLabels = optLookupPage map (v1 => LookupPageLabels(
      title = v1.title,
      heading = v1.heading,
      filterLabel = v1.filterLabel,
      postcodeLabel = v1.postcodeLabel,
      submitLabel = v1.submitLabel,
      noResultsFoundMessage = v1.noResultsFoundMessage,
      resultLimitExceededMessage = v1.resultLimitExceededMessage,
      manualAddressLinkText = v1.manualAddressLinkText
    ))

    val selectLabels = optSelectPage map (v1 => SelectPageLabels(
      title = v1.title,
      heading = v1.heading,
      headingWithPostcode = v1.headingWithPostcode,
      proposalListLabel = v1.proposalListLabel,
      submitLabel = v1.submitLabel,
      searchAgainLinkText = v1.searchAgainLinkText,
      editAddressLinkText = v1.editAddressLinkText
    ))

    val editLabels = optEditPage map (v1 => EditPageLabels(
      title = v1.title,
      heading = v1.heading,
      line1Label = v1.line1Label,
      line2Label = v1.line2Label,
      line3Label = v1.line3Label,
      townLabel = v1.townLabel,
      postcodeLabel = v1.postcodeLabel,
      countryLabel = v1.countryLabel,
      submitLabel = v1.submitLabel
    ))

    val confirmLabels = optConfirmPage map (v1 => ConfirmPageLabels(
      title = v1.title,
      heading = v1.heading,
      infoSubheading = v1.infoSubheading,
      infoMessage = v1.infoMessage,
      submitLabel = v1.submitLabel,
      searchAgainLinkText = v1.searchAgainLinkText,
      changeLinkText = v1.changeLinkText,
      confirmChangeText = v1.confirmChangeText
    ))

    (appLabels, lookupLabels, selectLabels, editLabels, confirmLabels) match {
      case (None, None, None, None, None) =>
        None
      case _ =>
        Some(JourneyLabels(
          en = Some(LanguageLabels(appLabels, selectLabels, lookupLabels, editLabels, confirmLabels)))
        )
    }
  }

  private def resolveSelectPageConfig(optSelectPage: Option[SelectPage]): Option[SelectPageConfig] =
    optSelectPage map (v1 => SelectPageConfig(v1.proposalListLimit, v1.showSearchAgainLink))

  private def resolveConfirmPageConfig(optConfirmPage: Option[ConfirmPage]): Option[ConfirmPageConfig] =
    optConfirmPage map (
      v1 => ConfirmPageConfig(v1.showSearchAgainLink, v1.showSubHeadingAndInfo, v1.showChangeLink, v1.showConfirmChangeText)
    )

  private def resolveTimeoutConfig(optTimeout: Option[Timeout]): Option[TimeoutConfig] =
    optTimeout map (v1 => TimeoutConfig(v1.timeoutAmount, v1.timeoutUrl))

  private def maybeString(v: ConfigValue): Option[String] = {
    if (v == null) None
    else Some(v.unwrapped().toString)
  }

  private def maybeInt(v: ConfigValue): Option[Int] = {
    if (v == null) None
    else Some(v.unwrapped().asInstanceOf[Int])
  }

  private def mustBeString(v: ConfigValue, key: String): String = {
    if (v == null) throw new IllegalArgumentException(s"$key must not be null")
    else v.unwrapped().toString
  }

  private def maybeBoolean(v: ConfigValue, default: Boolean): Option[Boolean] = {
    if (v == null) Some(default)
    else Some(v.unwrapped().asInstanceOf[Boolean])
  }

  private def maybeSetOfStrings(v: ConfigValue, key: String): Option[Set[String]] = {
    if (v == null) None
    else v.unwrapped() match {
      case list: java.util.List[_] => Some(list.asScala.map(_.toString).toSet)
      case item: String => Some(Set(item))
      case _ => throw new IllegalArgumentException(s"$key must be a list of strings")
    }
  }

  // TODO ensure all potential config values are mapped
  private def journey(key: String, journeys: ConfigObject): JourneyData = {
    val j = journeys.get(key).asInstanceOf[ConfigObject]
    val l = Option(j.get("lookupPage").asInstanceOf[ConfigObject])
    val s = Option(j.get("selectPage").asInstanceOf[ConfigObject])
    val c = Option(j.get("confirmPage").asInstanceOf[ConfigObject])
    val e = Option(j.get("editPage").asInstanceOf[ConfigObject])
    val lookup = l match {
      case Some(l) => LookupPage(maybeString(l.get("title")), maybeString(l.get("heading")), maybeString(l.get("filterLabel")), maybeString(l.get("postcodeLabel")), maybeString(l.get("submitLabel")), maybeString(l.get("resultLimitExceededMessage")), maybeString(l.get("noResultsFoundMessage")), maybeString(l.get("manualAddress")))
      case None => LookupPage()
    }
    val select = s match {
      case Some(s) => SelectPage(maybeString(s.get("title")), maybeString(s.get("heading")), maybeString(s.get("headingWithPostcode")), maybeString(s.get("proposalListLabel")), maybeString(s.get("submitLabel")), maybeInt(s.get("proposalListLimit")), maybeBoolean(s.get("showSearchAgainLink"), false), maybeString(s.get("searchAgainLinkText")), maybeString(s.get("editAddressLinkText")))
      case None => SelectPage()
    }
    val confirm = c match {
      case Some(c) => ConfirmPage(maybeString(c.get("title")), maybeString(c.get("heading")), maybeBoolean(c.get("showSubHeadingAndInfo"), false), maybeString(c.get("infoSubheading")), maybeString(c.get("infoMessage")), maybeString(c.get("submitLabel")), maybeBoolean(c.get("showSearchAgainLink"), false), maybeString(c.get("searchAgainLinkText")), maybeBoolean(c.get("showChangeLink"), true), maybeString(c.get("changeLinkText")))
      case None => ConfirmPage()
    }
    val edit = e match {
      case Some(e) => EditPage(maybeString(e.get("title")), maybeString(e.get("heading")), maybeString(e.get("line1Label")), maybeString(e.get("line2Label")), maybeString(e.get("line3Label")), maybeString(e.get("townLabel")), maybeString(e.get("postcodeLabel")), maybeString(e.get("countryLabel")), maybeString(e.get("submitLabel")))
      case None => EditPage()
    }
    JourneyData(
      config = JourneyConfig(
        continueUrl = mustBeString(j.get("continueUrl"), "continueUrl"),
        homeNavHref = maybeString(j.get("homeNavHref")),
        navTitle = maybeString(j.get("navTitle")),
        additionalStylesheetUrl = maybeString(j.get("additionalStylesheetUrl")),
        lookupPage = Some(lookup),
        selectPage = Some(select),
        confirmPage = Some(confirm),
        editPage = Some(edit),
        showPhaseBanner = maybeBoolean(j.get("showPhaseBanner"), false),
        alphaPhase = maybeBoolean(j.get("alphaPhase"), false),
        phaseFeedbackLink = maybeString(j.get("phaseFeedbackLink")),
        phaseBannerHtml = maybeString(j.get("phaseBannerHtml")),
        showBackButtons = maybeBoolean(j.get("showBackButtons"), false),
        includeHMRCBranding = maybeBoolean(j.get("includeHMRCBranding"), true),
        deskProServiceName = maybeString(j.get("deskProServiceName")),
        allowedCountryCodes = maybeSetOfStrings(j.get("allowedCountryCodes"), "allowedCountryCodes")
      )
    )
  }

}
