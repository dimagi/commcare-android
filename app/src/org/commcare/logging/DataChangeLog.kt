package org.commcare.logging


enum class DataChangeLog(val params: String) {

    COMMCARE_APP_INSTALL("App installed"),
    COMMCARE_APP_UNINSTALL("Uninstalling App"),
    COMMCARE_APP_UPDATE_STAGED("App update staged"),
    COMMCARE_APP_UPDATE_SUCCESSFUL("App updated"),
    COMMCARE_INSTALL("CommCare installed"),
    COMMCARE_UPDATE("CommCare udpated"),
    APP_DB_UPGRADE_START("Starting App DB upgrade"),
    APP_DB_UPGRADE_COMPLETE("Completed App DB upgrade"),
    USER_DB_UPGRADE_START("Starting User DB upgrade"),
    USER_DB_UPGRADE_COMPLETED("Completed User DB upgrade"),
    GLOBAL_DB_UPGRADE_START("Starting Global DB upgrade"),
    GLOBAL_DB_UPGRADE_COMPLETED("Completed Global DB upgrade"),
    CLEAR_USER_DATA("Clearing user data");


}