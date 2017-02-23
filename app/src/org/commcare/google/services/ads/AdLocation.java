package org.commcare.google.services.ads;

/**
 * Represents all of the unique views in the app that can show an AdMob ad. This is important
 * because AdMob stipulates that there must be a separate AdMob unit ID for each place in your
 * app that an ad is shown.
 *
 * @author Aliza Stone on 2/20/17.
 */
public enum AdLocation {
    EntityDetail, EntitySelect, MenuList, MenuGrid
}
