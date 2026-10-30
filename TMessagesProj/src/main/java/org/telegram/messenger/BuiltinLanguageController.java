package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class BuiltinLanguageController {

    private static final String DEFAULT_LANG_CODE = "zhcncc";
    private static final String DEFAULT_LANG_PACK = "android";
    private static final String PREF_APPLIED = "builtin_startup_lang_applied_" + DEFAULT_LANG_CODE;

    private static final BuiltinLanguageController INSTANCE = new BuiltinLanguageController();

    private boolean applying;

    public static void bootstrap() {
        INSTANCE.bootstrapInternal();
    }

    private void bootstrapInternal() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (preferences.getBoolean(PREF_APPLIED, false)) {
            return;
        }

        LocaleController localeController = LocaleController.getInstance();
        LocaleController.LocaleInfo currentLocale = localeController.getCurrentLocaleInfo();
        if (currentLocale != null && DEFAULT_LANG_CODE.equalsIgnoreCase(currentLocale.shortName)) {
            preferences.edit().putBoolean(PREF_APPLIED, true).apply();
            return;
        }

        if (applying) {
            return;
        }
        applying = true;

        int currentAccount = Math.max(UserConfig.selectedAccount, 0);
        TLRPC.TL_langpack_getLanguage request = new TLRPC.TL_langpack_getLanguage();
        request.lang_pack = DEFAULT_LANG_PACK;
        request.lang_code = DEFAULT_LANG_CODE;
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            applying = false;
            if (!(response instanceof TLRPC.TL_langPackLanguage)) {
                return;
            }

            LocaleController.LocaleInfo localeInfo = createLocaleInfo((TLRPC.TL_langPackLanguage) response);
            localeController.applyLanguage(localeInfo, true, false, false, true, currentAccount,
                    () -> preferences.edit().putBoolean(PREF_APPLIED, true).apply());
        }), ConnectionsManager.RequestFlagWithoutLogin);
    }

    private LocaleController.LocaleInfo createLocaleInfo(TLRPC.TL_langPackLanguage language) {
        language.lang_code = language.lang_code.replace('-', '_').toLowerCase();
        language.plural_code = language.plural_code.replace('-', '_').toLowerCase();
        if (language.base_lang_code != null) {
            language.base_lang_code = language.base_lang_code.replace('-', '_').toLowerCase();
        }

        String key = (language.official ? "remote_" : "unofficial_") + language.lang_code;
        LocaleController localeController = LocaleController.getInstance();
        LocaleController.LocaleInfo localeInfo = localeController.getLanguageFromDict(key);
        if (localeInfo == null) {
            localeInfo = new LocaleController.LocaleInfo();
        }

        localeInfo.name = language.native_name;
        localeInfo.nameEnglish = language.name;
        localeInfo.shortName = language.lang_code;
        localeInfo.baseLangCode = language.base_lang_code;
        localeInfo.pluralLangCode = language.plural_code;
        localeInfo.isRtl = language.rtl;
        localeInfo.pathToFile = language.official ? "remote" : "unofficial";
        return localeInfo;
    }
}
