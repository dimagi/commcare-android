/**
 *
 */
package org.commcare.views.notifications;

/**
 * This is an interface for tagging types (almost always enumerations)
 * as representing a notification message. To quickly implement message
 * tags for any of your enumerations, add this code to the type (called "MyEnum")
 *
 * MyEnum(String root) {this.root = root;}
 * private final String root;
 * public String getLocaleKeyBase() { return root;}
 * public String getCategory() { return "stock"; }
 *
 * Then all enumerated states (and must!) can contain a locale key which corresponds to the
 * base for the 2 to 3 fields in a notification message. If your key is
 *
 * my.message.key
 *
 * CommCare will search for the components
 *
 * my.message.key.title
 * my.message.key.detail
 * my.message.key.action (optional)
 *
 *
 * MessageTag enabled enums can be sent up for display with the NotificationMessageFactory.
 *
 * @author ctsims
 */
public interface MessageTag {

    /**
     * @return The suffix of the locale strings containing the notification message info
     */
    String getLocaleKeyBase();

    /**
     * @return A key corresponding to the category of the message. Messages with the same
     * category keys can be cleared systematically.
     */
    String getCategory();
}
