# Translations in CommCare


Strings in CommCare are mananged using 2 distinct strings file - 

1. [android_translatable_strings.txt](https://github.com/dimagi/commcare-android/blob/master/app/assets/locales/android_translatable_strings.txt)

This file is a legacy way to store the translatable strings for CommCare and should be avoided for any new strings. 

2. [Android strings.xml Resource](https://github.com/dimagi/commcare-android/blob/master/app/res/values/strings.xml)

This is where you should add any user-visible strings today using following guidelines - 

- Do not add code only constants to this file which are not user visible
- Any strings which are used inside a CommCare HQ App sandbox (on screens that are behind user login) should be tagged with `cc:translatable="true"`. This tag is used inside the [mobile deploy](https://github.com/dimagi/mobile-deploy/blob/master/update_translations.py#L146) scripts to update the [in-app translations for CommCare HQ]([url](https://dimagi.atlassian.net/wiki/spaces/commcarepublic/pages/3161751555/Setting+Up+Translations+for+CommCare+Mobile+App?atlOrigin=eyJpIjoiNzNhY2NlY2JhZGZmNDlkMGE5MGYyMTMyNjZkMzhhNTIiLCJwIjoiYyJ9))
- All other strings that are used outside the CommCare App sandbox should not define any `cc:translatable` value as it defaults to `false`
- Please name the strings as per the code area they are used in and group strings together as per the screen they are used in (Eg. `personalid_` and `connect_` prefixes)
- Any PersonalId and Connect strings should be machine translated in all supported languages in the app. 
