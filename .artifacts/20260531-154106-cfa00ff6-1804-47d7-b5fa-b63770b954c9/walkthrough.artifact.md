# Walkthrough - Remove All Translations Except English

I have removed all non-English localized resources from the project and updated the configuration to prevent "missing translation" errors in the future.

## Changes

### 1. Translation Removal
- Deleted all language-specific resource directories (`values-XX`, `anim-XX`, `drawable-XX`) across all modules.
- Preserved directories with other qualifiers (e.g., `values-sw600dp`, `layout-sw600dp`) to maintain tablet support.

### 2. Project Configuration
- Updated [app/build.gradle](file:///C:/Users/thiss/StudioProjects/showly/app/build.gradle) to set `resourceConfigurations` to only include `['en']`.
- Updated [locales_config.xml](file:///C:/Users/thiss/StudioProjects/showly/app/src/main/res/xml/locales_config.xml) to only include English.

### 3. UI and Logic Cleanup
- Updated the `AppLanguage` enum in [AppLanguage.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/java/com/michaldrabik/ui_settings/helpers/AppLanguage.kt) to only contain `ENGLISH`.
- Removed the language selection option from the General Settings screen in [fragment_settings_general.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/res/layout/fragment_settings_general.xml) and [SettingsGeneralFragment.kt](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/java/com/michaldrabik/ui_settings/sections/general/SettingsGeneralFragment.kt).
- Cleaned up unused language string names from the base settings [strings.xml](file:///C:/Users/thiss/StudioProjects/showly/ui-settings/src/main/res/values/strings.xml).

### 4. Shared Resource Fix
- Moved `@drawable/bg_translucent_circle` from `ui-movie` to `ui-base` to resolve a missing resource error in the `ui-show` module.

## Verification Summary

### Automated Tests
- Ran `./gradlew :ui-base:lintDebug` to verify that "MissingTranslation" errors no longer occur.
- Ran `./gradlew :app:assembleDebug` to confirm the project still builds successfully.

### Manual Verification
- Confirmed via shell commands that all `values-XX` directories (where `XX` is a language code) have been removed from the source tree.
- Confirmed that English strings are correctly used as the default.
