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
      @font-face { font-family: 'Inter'; font-style: normal; font-weight: 300; font-display: swap; src: url('${url.resourcesPath}/fonts/inter-300.ttf') format('truetype'); }
      @font-face { font-family: 'Inter'; font-style: normal; font-weight: 400; font-display: swap; src: url('${url.resourcesPath}/fonts/inter-400.ttf') format('truetype'); }
      @font-face { font-family: 'Inter'; font-style: normal; font-weight: 500; font-display: swap; src: url('${url.resourcesPath}/fonts/inter-500.ttf') format('truetype'); }
      @font-face { font-family: 'Inter'; font-style: normal; font-weight: 600; font-display: swap; src: url('${url.resourcesPath}/fonts/inter-600.ttf') format('truetype'); }
      @font-face { font-family: 'Inter'; font-style: normal; font-weight: 700; font-display: swap; src: url('${url.resourcesPath}/fonts/inter-700.ttf') format('truetype'); }
      @font-face { font-family: 'Inter'; font-style: normal; font-weight: 800; font-display: swap; src: url('${url.resourcesPath}/fonts/inter-800.ttf') format('truetype'); }
:root {
  --fg-bg: #f4f1eb;
  --fg-card: #fbfaf6;
  --fg-surface: #f1eee8;
  --fg-text: #24202a;
  --fg-muted: #756f67;
  --fg-border: rgba(36, 32, 42, 0.14);
  --fg-primary: #bd962c;
  --fg-primary-hover: #a98322;
  --fg-primary-text: #fff7dc;
  --fg-shadow-soft: 0 2px 8px rgba(34, 29, 22, 0.06);
}

*,
*::before,
*::after,
input,
button,
select,
textarea {
  font-family: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
}

html,
body,
#keycloak-bg,
.login-pf,
.pf-v5-c-login {
  min-height: 100vh !important;
  margin: 0 !important;
  background: var(--fg-bg) !important;
  color: var(--fg-text) !important;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
}

.pf-v5-c-login {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 18px 12px !important;
}

.pf-v5-c-login__container {
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 10px !important;
  width: min(100%, 408px) !important;
  max-width: 408px !important;
  min-height: auto !important;
  margin: 0 auto !important;
  padding: 0 !important;
  grid-template-columns: none !important;
  grid-template-areas: none !important;
}

.pf-v5-c-login__header {
  grid-area: auto !important;
  width: 100% !important;
  margin: 0 !important;
  padding: 0 !important;
  text-align: center !important;
  order: 0 !important;
}

#kc-header-wrapper,
.pf-v5-c-brand {
  width: 100% !important;
  margin: 0 auto !important;
  color: transparent !important;
  font-size: 0 !important;
  line-height: 1 !important;
  text-align: center !important;
  text-transform: none !important;
}

#kc-header-wrapper::after,
.pf-v5-c-brand::after {
  content: "FinGuide" !important;
  display: inline-block !important;
  color: var(--fg-text) !important;
  font-size: 17px !important;
  font-weight: 800 !important;
  letter-spacing: -0.03em !important;
  text-transform: none !important;
}

.pf-v5-c-login__main,
.card-pf {
  grid-area: auto !important;
  width: min(100%, 392px) !important;
  max-width: 392px !important;
  margin: 0 auto !important;
  order: 1 !important;
  border: 1px solid var(--fg-border) !important;
  border-radius: 20px !important;
  background: rgba(251, 250, 246, 0.98) !important;
  box-shadow: var(--fg-shadow-soft) !important;
  overflow: hidden !important;
}

.pf-v5-c-login__main-header,
.pf-v5-c-login__main-body,
.pf-v5-c-login__main-footer-band {
  padding-left: 20px !important;
  padding-right: 20px !important;
  background: transparent !important;
}

.pf-v5-c-login__main-header {
  padding-top: 20px !important;
  padding-bottom: 8px !important;
  border-bottom: 0 !important;
}

.pf-v5-c-login__main-body {
  padding-top: 12px !important;
  padding-bottom: 18px !important;
}

.pf-v5-c-login__main-footer-band {
  padding-top: 14px !important;
  padding-bottom: 16px !important;
  border-top: 1px solid rgba(36, 32, 42, 0.08) !important;
  background: rgba(241, 238, 232, 0.52) !important;
}

#kc-page-title,
.pf-c-title,
.pf-v5-c-title {
  color: var(--fg-text) !important;
  text-align: center !important;
  font-size: 22px !important;
  line-height: 1.18 !important;
  letter-spacing: -0.03em !important;
}

#kc-page-title::after {
  content: "" !important;
  display: none !important;
}

.subtitle,
.instruction,
#kc-info-wrapper,
#kc-form-options,
.pf-v5-c-helper-text__item-text,
.pf-v5-c-form__label-text,
label,
.login-pf-signup {
  color: var(--fg-muted) !important;
}

.pf-v5-c-form__label-required {
  color: var(--fg-primary) !important;
}

#kc-register-form .pf-v5-c-form__group,
#kc-reset-password-form .pf-v5-c-form__group,
#kc-passwd-update-form .pf-v5-c-form__group,
#kc-form-login .pf-v5-c-form__group,
.pf-v5-c-form__group {
  margin-bottom: 11px !important;
}

.pf-v5-c-form__label {
  margin-bottom: 4px !important;
}

.pf-v5-c-form-control,
.pf-v5-c-form-control > input,
input[type="text"],
input[type="password"],
input[type="email"],
select {
  min-height: 38px !important;
  border-color: var(--fg-border) !important;
  border-radius: 12px !important;
  background: #fffdf8 !important;
  color: var(--fg-text) !important;
  box-shadow: none !important;
}

.pf-v5-c-form-control:focus-within,
input[type="text"]:focus,
input[type="password"]:focus,
input[type="email"]:focus,
select:focus {
  border-color: rgba(189, 150, 44, 0.58) !important;
  box-shadow: 0 0 0 2px rgba(189, 150, 44, 0.16) !important;
}

::placeholder {
  color: rgba(117, 111, 103, 0.68) !important;
}

a,
.pf-c-button.pf-m-link,
.pf-v5-c-button.pf-m-link {
  color: #9b761f !important;
  font-weight: 650 !important;
}

a:hover,
.pf-v5-c-button.pf-m-link:hover {
  color: var(--fg-primary-hover) !important;
}

.pf-c-button.pf-m-primary,
.pf-v5-c-button.pf-m-primary,
input[type="submit"] {
  min-height: 42px !important;
  border: 0 !important;
  border-radius: 999px !important;
  background: var(--fg-primary) !important;
  color: var(--fg-primary-text) !important;
  font-size: 14px !important;
  font-weight: 700 !important;
  box-shadow: var(--fg-shadow-soft) !important;
}

.pf-c-button.pf-m-primary:hover,
.pf-v5-c-button.pf-m-primary:hover,
input[type="submit"]:hover {
  background: var(--fg-primary-hover) !important;
}

.pf-c-alert,
.pf-v5-c-alert,
.alert-error,
.kc-feedback-text,
.pf-v5-c-form__helper-text {
  border-radius: 12px !important;
}

#kc-locale,
#kc-locale-dropdown,
#kc-locale-dropdown-menu,
.pf-v5-c-login__main-header .pf-v5-c-menu-toggle {
  border-radius: 12px !important;
  background: #fffdf8 !important;
  color: var(--fg-text) !important;
}

@media (max-width: 640px) {
  .pf-v5-c-login { padding: 14px 10px !important; }
  .pf-v5-c-login__container { width: min(100%, 390px) !important; }
  .pf-v5-c-login__main, .card-pf { border-radius: 18px !important; }
  #kc-header-wrapper::after, .pf-v5-c-brand::after { font-size: 16px !important; }
  .pf-v5-c-login__main-header,
  .pf-v5-c-login__main-body,
  .pf-v5-c-login__main-footer-band { padding-left: 18px !important; padding-right: 18px !important; }
}


/* FinGuide app-level polish: same font, no external badge, no square default controls. */
.pf-v5-c-login__header,
#kc-header,
#kc-header-wrapper,
.pf-v5-c-brand {
  display: none !important;
}

.pf-v5-c-login__container {
  gap: 0 !important;
}

.pf-v5-c-login__main-header-utilities,
#kc-locale,
#kc-locale-dropdown,
#kc-locale-dropdown-menu,
#login-select-toggle {
  display: none !important;
}

.pf-v5-c-check__input,
input[type="checkbox"] {
  width: 16px !important;
  height: 16px !important;
  border: 1px solid var(--fg-border) !important;
  border-radius: 5px !important;
  background-color: #fffdf8 !important;
  accent-color: var(--fg-primary) !important;
}

.pf-v5-c-input-group,
.pf-v5-c-input-group__item,
.pf-v5-c-form-control,
.pf-v5-c-form-control::before,
.pf-v5-c-form-control::after,
.pf-v5-c-button,
button,
select {
  border-radius: 12px !important;
}

button[aria-label*="password"],
button[aria-label*="Password"],
button[aria-label*="парол"],
.pf-v5-c-form-control .pf-v5-c-button,
.pf-v5-c-input-group .pf-v5-c-button {
  border-radius: 0 12px 12px 0 !important;
  background: #fffdf8 !important;
  color: var(--fg-muted) !important;
}

.pf-v5-c-login__main {
  box-shadow: 0 2px 8px rgba(34, 29, 22, 0.05) !important;
}

#kc-page-title {
  margin-bottom: 0 !important;
}


/* Hide broken Keycloak/PF icon-font controls; keep fields clean like FinGuide app. */
button[data-password-toggle],
.pf-v5-c-input-group__item:not(.pf-m-fill),
.pf-v5-c-form__helper-text .pf-v5-c-helper-text__item-icon,
.pf-v5-c-alert__icon,
.fa,
.fas,
.far,
.fal,
.fab,
.pf-icon,
.pf-v5-svg {
  display: none !important;
}

.pf-v5-c-input-group,
.pf-v5-c-input-group__item.pf-m-fill,
.pf-v5-c-input-group__item.pf-m-fill > .pf-v5-c-form-control {
  width: 100% !important;
}

.pf-v5-c-input-group .pf-v5-c-form-control,
.pf-v5-c-input-group input {
  border-radius: 12px !important;
}


/* Registration page must fit into a typical laptop viewport. */
.pf-v5-c-login:has(#kc-register-form) {
  align-items: flex-start !important;
  padding-top: 10px !important;
  padding-bottom: 10px !important;
}

.pf-v5-c-login__container:has(#kc-register-form) {
  width: min(100%, 368px) !important;
  max-width: 368px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) {
  width: min(100%, 360px) !important;
  max-width: 360px !important;
  max-height: calc(100vh - 20px) !important;
  border-radius: 16px !important;
  overflow-y: auto !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-login__main-header {
  padding-top: 12px !important;
  padding-bottom: 4px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-login__main-body {
  padding-top: 8px !important;
  padding-bottom: 10px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-login__main-footer-band {
  padding-top: 8px !important;
  padding-bottom: 10px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) #kc-page-title {
  font-size: 19px !important;
  line-height: 1.12 !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .subtitle,
.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-u-mb-md-on-md {
  display: none !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-form__group {
  margin-bottom: 7px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-form__label {
  margin-bottom: 2px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-form__label-text,
.pf-v5-c-login__main:has(#kc-register-form) label,
.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-helper-text__item-text {
  font-size: 12px !important;
  line-height: 1.2 !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-form-control,
.pf-v5-c-login__main:has(#kc-register-form) input[type="text"],
.pf-v5-c-login__main:has(#kc-register-form) input[type="password"],
.pf-v5-c-login__main:has(#kc-register-form) input[type="email"] {
  min-height: 32px !important;
  border-radius: 10px !important;
  font-size: 13px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-form__actions,
.pf-v5-c-login__main:has(#kc-register-form) #kc-form-buttons {
  margin-top: 4px !important;
}

.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-button.pf-m-primary,
.pf-v5-c-login__main:has(#kc-register-form) input[type="submit"] {
  min-height: 36px !important;
  font-size: 13px !important;
}

.pf-v5-c-login__main:has(#kc-register-form)::-webkit-scrollbar {
  width: 6px;
}

.pf-v5-c-login__main:has(#kc-register-form)::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(36, 32, 42, 0.18);
}


/* Critical visual alignment with /fg/login reference. */
:root {
  --pf-v5-global--FontFamily--text: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  --pf-v5-global--FontFamily--heading: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  --pf-v5-global--FontFamily--text--vf: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  --pf-v5-global--FontFamily--heading--vf: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  --pf-v5-c-title--FontFamily: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
}

html,
body,
body *,
body *::before,
body *::after,
.pf-v5-c-login,
.pf-v5-c-login *,
.pf-v5-c-title,
.pf-v5-c-button,
.pf-v5-c-form-control,
input,
button,
select,
textarea {
  font-family: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
}

#kc-page-title,
.pf-v5-c-title,
.pf-c-title {
  font-family: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  font-size: 24px !important;
  font-weight: 700 !important;
  line-height: 1.2 !important;
}

.pf-v5-c-login__main:has(#kc-register-form) #kc-page-title {
  font-size: 24px !important;
  font-weight: 700 !important;
  line-height: 1.2 !important;
}

.pf-c-button.pf-m-primary,
.pf-v5-c-button.pf-m-primary,
.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-button.pf-m-primary,
input[type="submit"],
.pf-v5-c-login__main:has(#kc-register-form) input[type="submit"] {
  box-sizing: border-box !important;
  height: 48px !important;
  min-height: 48px !important;
  padding: 0 20px !important;
  border-radius: 999px !important;
  font-family: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  font-size: 16px !important;
  font-weight: 700 !important;
  line-height: 1 !important;
}

.pf-v5-c-form-control,
.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-form-control,
input[type="text"],
input[type="password"],
input[type="email"],
.pf-v5-c-login__main:has(#kc-register-form) input[type="text"],
.pf-v5-c-login__main:has(#kc-register-form) input[type="password"],
.pf-v5-c-login__main:has(#kc-register-form) input[type="email"] {
  font-family: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  font-size: 14px !important;
  font-weight: 400 !important;
}

.pf-v5-c-form__label-text,
.pf-v5-c-login__main:has(#kc-register-form) .pf-v5-c-form__label-text,
label,
.pf-v5-c-login__main:has(#kc-register-form) label {
  font-family: "Inter", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
  font-size: 13px !important;
  font-weight: 500 !important;
}

/* Password recovery screens: compact FinGuide auth card, aligned with /fg/login. */
.pf-v5-c-login:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) {
  background: #fbf9f3 !important;
}

.pf-v5-c-login__container:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) {
  width: min(100%, 520px) !important;
  max-width: 520px !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) {
  width: min(100%, 520px) !important;
  max-width: 520px !important;
  border-color: rgba(26, 20, 30, 0.12) !important;
  border-radius: 20px !important;
  background: #fbf9f3 !important;
  box-shadow: 0 10px 34px rgba(26, 20, 30, 0.08) !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-header {
  padding: 28px 32px 8px !important;
  position: relative !important;
  text-align: left !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-header::before {
  content: "FinPlan";
  display: flex !important;
  align-items: center !important;
  min-height: 44px !important;
  margin-bottom: 28px !important;
  padding-left: 58px !important;
  color: #1a141e !important;
  font-size: 21px !important;
  font-weight: 700 !important;
  line-height: 1 !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-header::after {
  content: "";
  display: block !important;
  position: absolute !important;
  top: 28px !important;
  left: 32px !important;
  width: 44px !important;
  height: 44px !important;
  border-radius: 12px !important;
  background:
    linear-gradient(#dcc57f, #dcc57f) 15px 13px / 14px 18px no-repeat,
    linear-gradient(#dcc57f, #dcc57f) 30px 20px / 8px 11px no-repeat,
    linear-gradient(#1a141e, #1a141e) left center / 44px 44px no-repeat;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) #kc-page-title {
  color: #1a141e !important;
  text-align: left !important;
  font-size: 24px !important;
  font-weight: 700 !important;
  line-height: 1.2 !important;
  letter-spacing: 0 !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-body {
  padding: 12px 32px 28px !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .instruction,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) #kc-info-wrapper,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-helper-text__item-text,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-form__label-text,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) label {
  color: #807688 !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .instruction {
  max-width: 420px !important;
  margin-bottom: 22px !important;
  font-size: 13px !important;
  line-height: 1.6 !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-form__group {
  margin-bottom: 18px !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-form-control,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="text"],
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="password"],
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="email"] {
  min-height: 44px !important;
  border-color: rgba(26, 20, 30, 0.12) !important;
  border-radius: 12px !important;
  background: #f5f3ed !important;
  color: #1a141e !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-form-control:focus-within,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="text"]:focus,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="password"]:focus,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="email"]:focus {
  border-color: rgba(26, 20, 30, 0.22) !important;
  box-shadow: 0 0 0 3px rgba(26, 20, 30, 0.08) !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-form__actions,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) #kc-form-buttons {
  margin-top: 2px !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-button.pf-m-primary,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="submit"] {
  height: 48px !important;
  min-height: 48px !important;
  border-radius: 999px !important;
  background: #1a141e !important;
  color: #ffffff !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-button.pf-m-primary:hover,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) input[type="submit"]:hover {
  background: #281534 !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-footer-band {
  padding: 16px 32px 24px !important;
  border-top: 0 !important;
  background: transparent !important;
}

.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) a,
.pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-button.pf-m-link {
  color: #1a141e !important;
  font-weight: 700 !important;
}

@media (max-width: 640px) {
  .pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-header {
    padding: 24px 22px 8px !important;
  }

  .pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-header::after {
    top: 24px !important;
    left: 22px !important;
  }

  .pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-body {
    padding: 12px 22px 24px !important;
  }

  .pf-v5-c-login__main:has(:is(#kc-reset-password-form, #kc-passwd-update-form)) .pf-v5-c-login__main-footer-band {
    padding: 14px 22px 22px !important;
  }
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
