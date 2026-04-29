<#import "field.ftl" as field>
<#import "footer.ftl" as loginFooter>
<#macro username>
  <#assign label>
    <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
  </#assign>
  <@field.group name="username" label=label>
    <div class="${properties.kcInputGroup}">
      <div class="${properties.kcInputGroupItemClass} ${properties.kcFill}">
        <span class="${properties.kcInputClass} ${properties.kcFormReadOnlyClass}">
          <input id="kc-attempted-username" value="${auth.attemptedUsername}" readonly>
        </span>
      </div>
      <div class="${properties.kcInputGroupItemClass}">
        <button id="reset-login" class="${properties.kcFormPasswordVisibilityButtonClass} kc-login-tooltip" type="button" 
              aria-label="${msg('restartLoginTooltip')}" onclick="location.href='${url.loginRestartFlowUrl}'">
            <i class="fa-sync-alt fas" aria-hidden="true"></i>
            <span class="kc-tooltip-text">${msg("restartLoginTooltip")}</span>
        </button>
      </div>
    </@field.group>
</#macro>

<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}"<#if realm.internationalizationEnabled> lang="${locale.currentLanguageTag}" dir="${(locale.rtl)?then('rtl','ltr')}"</#if>>

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
    </script>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
    <script type="module">
        import { startSessionPolling } from "${url.resourcesPath}/js/authChecker.js";

        startSessionPolling(
            "${url.ssoLoginInOtherTabsUrl?no_esc}"
        );

        const DARK_MODE_CLASS = "pf-v5-theme-dark";
        const mediaQuery =window.matchMedia("(prefers-color-scheme: dark)");
        updateDarkMode(mediaQuery.matches);
        mediaQuery.addEventListener("change", (event) =>
          updateDarkMode(event.matches),
        );
        function updateDarkMode(isEnabled) {
          const { classList } = document.documentElement;
          if (isEnabled) {
            classList.add(DARK_MODE_CLASS);
          } else {
            classList.remove(DARK_MODE_CLASS);
          }
        }
    </script>

    <style id="finguide-critical-auth-layout">
      :root { color-scheme: light; }
      html, body, #keycloak-bg {
        min-height: 100vh !important;
        margin: 0 !important;
        background: #f4f1eb !important;
      }
      body #keycloak-bg, body, .login-pf, .pf-v5-c-login {
        color: #24202a !important;
        font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
      }
      body .pf-v5-c-login {
        display: flex !important;
        align-items: center !important;
        justify-content: center !important;
        min-height: 100vh !important;
        padding: 24px 16px !important;
        background: #f4f1eb !important;
      }
      body .pf-v5-c-login__container {
        display: flex !important;
        flex-direction: column !important;
        align-items: center !important;
        justify-content: center !important;
        gap: 14px !important;
        width: min(100%, 460px) !important;
        max-width: 460px !important;
        min-height: auto !important;
        margin: 0 auto !important;
        padding: 0 !important;
        grid-template-columns: none !important;
        grid-template-areas: none !important;
      }
      body .pf-v5-c-login__header {
        display: block !important;
        width: 100% !important;
        margin: 0 !important;
        padding: 0 !important;
        text-align: center !important;
        order: 0 !important;
        grid-area: auto !important;
      }
      body #kc-header-wrapper,
      body .pf-v5-c-brand {
        width: 100% !important;
        margin: 0 auto !important;
        color: transparent !important;
        font-size: 0 !important;
        line-height: 1 !important;
        text-align: center !important;
        text-transform: none !important;
      }
      body #kc-header-wrapper::after,
      body .pf-v5-c-brand::after {
        content: "FinGuide" !important;
        display: inline-flex !important;
        align-items: center !important;
        justify-content: center !important;
        width: fit-content !important;
        margin: 0 auto !important;
        padding: 7px 13px !important;
        border: 1px solid rgba(36, 32, 42, 0.14) !important;
        border-radius: 999px !important;
        background: rgba(251, 250, 246, 0.86) !important;
        color: #24202a !important;
        font-size: 18px !important;
        font-weight: 800 !important;
        letter-spacing: -0.03em !important;
        text-transform: none !important;
        box-shadow: 0 2px 8px rgba(34, 29, 22, 0.06) !important;
      }
      body .pf-v5-c-login__main,
      body .card-pf {
        display: block !important;
        width: min(100%, 420px) !important;
        max-width: 420px !important;
        margin: 0 auto !important;
        order: 1 !important;
        grid-area: auto !important;
        border: 1px solid rgba(189, 150, 44, 0.2) !important;
        border-radius: 22px !important;
        background: rgba(251, 250, 246, 0.96) !important;
        box-shadow: 0 10px 28px rgba(34, 29, 22, 0.07), 0 1px 1px rgba(34, 29, 22, 0.05) !important;
        overflow: hidden !important;
      }
      body .pf-v5-c-login__main-header,
      body .pf-v5-c-login__main-body,
      body .pf-v5-c-login__main-footer-band {
        padding-left: 24px !important;
        padding-right: 24px !important;
        background: transparent !important;
      }
      body .pf-v5-c-login__main-header {
        padding-top: 24px !important;
        padding-bottom: 12px !important;
        border-bottom: 1px solid rgba(36, 32, 42, 0.08) !important;
      }
      body .pf-v5-c-login__main-body {
        padding-top: 18px !important;
        padding-bottom: 20px !important;
      }
      body .pf-v5-c-login__main-footer-band {
        padding-top: 16px !important;
        padding-bottom: 18px !important;
        border-top: 1px solid rgba(36, 32, 42, 0.08) !important;
        background: rgba(241, 238, 232, 0.72) !important;
      }
      body #kc-page-title,
      body .pf-v5-c-title {
        color: #24202a !important;
        text-align: center !important;
        font-size: 24px !important;
        line-height: 1.15 !important;
        letter-spacing: -0.03em !important;
      }
      body #kc-page-title::after {
        content: "Финансовый план под вашим контролем";
        display: block;
        margin-top: 7px;
        color: #756f67;
        font-size: 13px;
        font-weight: 500;
        letter-spacing: 0;
      }
      body label,
      body .pf-v5-c-form__label-text,
      body .subtitle,
      body .instruction,
      body .pf-v5-c-helper-text__item-text,
      body #kc-form-options,
      body #kc-info-wrapper { color: #756f67 !important; }
      body .pf-v5-c-form__group { margin-bottom: 13px !important; }
      body .pf-v5-c-form-control,
      body .pf-v5-c-form-control > input,
      body input[type="text"],
      body input[type="password"],
      body input[type="email"],
      body select {
        min-height: 40px !important;
        border-color: rgba(36, 32, 42, 0.14) !important;
        border-radius: 14px !important;
        background: rgba(251, 250, 246, 0.9) !important;
        color: #24202a !important;
        box-shadow: none !important;
      }
      body .pf-v5-c-form-control:focus-within,
      body input:focus,
      body select:focus {
        border-color: rgba(189, 150, 44, 0.65) !important;
        box-shadow: 0 0 0 3px rgba(189, 150, 44, 0.18) !important;
      }
      body a { color: #9b761f !important; font-weight: 700 !important; }
      body .pf-v5-c-button.pf-m-primary,
      body input[type="submit"] {
        min-height: 44px !important;
        border: 0 !important;
        border-radius: 999px !important;
        background: #bd962c !important;
        color: #fff7dc !important;
        font-size: 15px !important;
        font-weight: 700 !important;
        box-shadow: 0 2px 8px rgba(34, 29, 22, 0.06) !important;
      }
      body .pf-v5-c-button.pf-m-primary:hover,
      body input[type="submit"]:hover { background: #a98322 !important; }
      @media (max-width: 640px) {
        body .pf-v5-c-login { padding: 16px 12px !important; }
        body .pf-v5-c-login__main { border-radius: 20px !important; }
        body #kc-header-wrapper::after, body .pf-v5-c-brand::after { font-size: 17px !important; }
        body .pf-v5-c-login__main-header,
        body .pf-v5-c-login__main-body,
        body .pf-v5-c-login__main-footer-band { padding-left: 20px !important; padding-right: 20px !important; }
      }
    </style>

</head>

<body id="keycloak-bg" class="${properties.kcBodyClass!}">

<div class="${properties.kcLogin!}">
  <div class="${properties.kcLoginContainer!}">
    <header id="kc-header" class="pf-v5-c-login__header">
      <div id="kc-header-wrapper"
              class="pf-v5-c-brand">${kcSanitize(msg("loginTitleHtml",(realm.displayNameHtml!'')))?no_esc}</div>
    </header>
    <main class="${properties.kcLoginMain!}">
      <div class="${properties.kcLoginMainHeader!}">
        <h1 class="${properties.kcLoginMainTitle!}" id="kc-page-title"><#nested "header"></h1>
        <#if realm.internationalizationEnabled  && locale.supported?size gt 1>
        <div class="${properties.kcLoginMainHeaderUtilities!}">
          <div class="${properties.kcInputClass!}">
            <select
              aria-label="${msg("languages")}"
              id="login-select-toggle"
              onchange="if (this.value) window.location.href=this.value"
            >
              <#list locale.supported?sort_by("label") as l>
                <option
                  value="${l.url}"
                  ${(l.languageTag == locale.currentLanguageTag)?then('selected','')}
                >
                  ${l.label}
                </option>
              </#list>
            </select>
            <span class="${properties.kcFormControlUtilClass}">
              <span class="${properties.kcFormControlToggleIcon!}">
                <svg
                  class="pf-v5-svg"
                  viewBox="0 0 320 512"
                  fill="currentColor"
                  aria-hidden="true"
                  role="img"
                  width="1em"
                  height="1em"
                >
                  <path
                    d="M31.3 192h257.3c17.8 0 26.7 21.5 14.1 34.1L174.1 354.8c-7.8 7.8-20.5 7.8-28.3 0L17.2 226.1C4.6 213.5 13.5 192 31.3 192z"
                  >
                  </path>
                </svg>
              </span>
            </span>
          </div>
        </div>
        </#if>
      </div>
      <div class="${properties.kcLoginMainBody!}">
        <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
            <#if displayRequiredFields>
                <div class="${properties.kcContentWrapperClass!}">
                    <div class="${properties.kcLabelWrapperClass!} subtitle">
                        <span class="${properties.kcInputHelperTextItemTextClass!}">
                          <span class="${properties.kcInputRequiredClass!}">*</span> ${msg("requiredFields")}
                        </span>
                    </div>
                </div>
            </#if>
        <#else>
            <#if displayRequiredFields>
                <div class="${properties.kcContentWrapperClass!}">
                    <div class="${properties.kcLabelWrapperClass!} subtitle">
                        <span class="${properties.kcInputHelperTextItemTextClass!}">
                          <span class="${properties.kcInputRequiredClass!}">*</span> ${msg("requiredFields")}
                        </span>
                    </div>
                    <div class="${properties.kcFormClass} ${properties.kcContentWrapperClass}">
                        <#nested "show-username">
                        <@username />
                    </div>
                </div>
            <#else>
                <div class="${properties.kcFormClass} ${properties.kcContentWrapperClass}">
                  <#nested "show-username">
                  <@username />
                </div>
            </#if>
        </#if>

        <#-- App-initiated actions should not see warning messages about the need to complete the action -->
        <#-- during login.                                                                               -->
        <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="${properties.kcAlertClass!} pf-m-${(message.type = 'error')?then('danger', message.type)}">
                <div class="${properties.kcAlertIconClass!}">
                    <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                    <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                    <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                    <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                </div>
                <span class="${properties.kcAlertTitleClass!} kc-feedback-text">${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <#nested "form">

        <#if auth?has_content && auth.showTryAnotherWayLink()>
          <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" novalidate="novalidate">
              <input type="hidden" name="tryAnotherWay" value="on"/>
              <a id="try-another-way" href="javascript:document.forms['kc-select-try-another-way-form'].requestSubmit()"
                  class="${properties.kcButtonSecondaryClass} ${properties.kcButtonBlockClass} ${properties.kcMarginTopClass}">
                    ${kcSanitize(msg("doTryAnotherWay"))?no_esc}
              </a>
          </form>
        </#if>

        <#if displayInfo>
          <div id="kc-info" class="${properties.kcSignUpClass!}">
              <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                  <#nested "info">
              </div>
          </div>
        </#if>
      </div>
      <div class="pf-v5-c-login__main-footer">
        <#nested "socialProviders">
      </div>
    </main>

    <@loginFooter.content/>
  </div>
</div>
</body>
</html>
</#macro>
