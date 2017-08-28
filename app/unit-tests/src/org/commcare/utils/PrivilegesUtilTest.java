package org.commcare.utils;

import org.commcare.modern.util.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Tests for processing privilege responses from the server
 *
 * @author Clayton Sims (csims@dimagi.com)
 */
public class PrivilegesUtilTest {

    //This is the public key from a private key that is stored in the test_assets directory under key_for_tests
    public static final String TEST_CERT_STRING = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCfn1CCWi3U8Im3cFs0Q8Uw8+fR4fAAEuytzABgLrRQAZLMLrpxHkpJQ80L4KV9upSnAw69IKYXdGKtrn5ne40XIPYWIHAsUnZTqQn9talWAolSHVYMpMWxi+6o1eJr2YbPLJ3yOYmb1lhU0o6FnmBfANsQk/RWV+QtRlO86ARq3wIDAQAB";

    @Test
    public void parseV1Payload() throws Exception {
        //Test Payload: [{"username":"privilege_test"},{"flag":"multiple_apps_unlimited"}]
        Pair<String, String[]> privilegesEnabled = new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flag:'multiple_apps_unlimited'," +
                        "signature:'c0GcoHux0dDME3510bR4jm1FEGY7rY5ISf5HvReeVM4G4pHucmo12juhEimofR41NsQLUmHmCDvh9ikG1gbiFTx037eM7nh8sU7HK+oV3Dbi+BokuDcG3rIDA9AIkoU/iFABdnLKtohqo3brk5RVFcZaSn+hBxOL/QNYy5BF60U='"+
                        "}"
        );

        Assert.assertEquals("privilege_test", privilegesEnabled.first);
        Assert.assertEquals("multiple_apps_unlimited", privilegesEnabled.second[0]);
    }

    @Test
    public void parseV2PayloadSingleArgument() throws Exception {
        //Test Payload: [{"username":"privilege_test"},{"flags":["multiple_apps_unlimited"]}]
        Pair<String, String[]> privilegesEnabled = new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flags:['multiple_apps_unlimited']," +
                        "multiple_flags_signature:'D6BBGW7dwlm1hpk0mSnWe/O/R2s8LIxFJmV6J3iTG6tGphjOWJ1i9MLDHMZ0UblFlHzIylfkYZts3wrAjc6JZQ5AhejQ8AMjVfSQsRqNx4oxVDVjvMQh/tYSi4YljHewGBD2BEuKzLx/QNZ9jI/N9ALSXes+t4gPip0NblLLyf8=',"+
                        "version:2"+
                        "}"
        );

        Assert.assertEquals("privilege_test", privilegesEnabled.first);
        Assert.assertEquals("multiple_apps_unlimited", privilegesEnabled.second[0]);
    }

    @Test
    public void parseV2PayloadBackwardsCompatible() throws Exception {
        //Test Payload: [{"username":"privilege_test"},{"flags":["multiple_apps_unlimited"]}]
        Pair<String, String[]> privilegesEnabled = new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flag:'multiple_apps_unlimited',"+
                        "flags:['multiple_apps_unlimited']," +
                        "signature:'c0GcoHux0dDME3510bR4jm1FEGY7rY5ISf5HvReeVM4G4pHucmo12juhEimofR41NsQLUmHmCDvh9ikG1gbiFTx037eM7nh8sU7HK+oV3Dbi+BokuDcG3rIDA9AIkoU/iFABdnLKtohqo3brk5RVFcZaSn+hBxOL/QNYy5BF60U=',"+
                        "multiple_flags_signature:'D6BBGW7dwlm1hpk0mSnWe/O/R2s8LIxFJmV6J3iTG6tGphjOWJ1i9MLDHMZ0UblFlHzIylfkYZts3wrAjc6JZQ5AhejQ8AMjVfSQsRqNx4oxVDVjvMQh/tYSi4YljHewGBD2BEuKzLx/QNZ9jI/N9ALSXes+t4gPip0NblLLyf8=',"+
                        "version:2"+
                        "}"
        );

        Assert.assertEquals("privilege_test", privilegesEnabled.first);
        Assert.assertEquals("multiple_apps_unlimited", privilegesEnabled.second[0]);
    }

    @Test
    public void parseV2PayloadMultiple() throws Exception {
        //Test Payload: [{"username":"privilege_test"},{"flags":["multiple_apps_unlimited","advanced_settings_access"]}]
        Pair<String, String[]> privilegesEnabled = new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flag:'multiple_apps_unlimited',"+
                        "flags:['multiple_apps_unlimited','advanced_settings_access']," +
                        "signature:'c0GcoHux0dDME3510bR4jm1FEGY7rY5ISf5HvReeVM4G4pHucmo12juhEimofR41NsQLUmHmCDvh9ikG1gbiFTx037eM7nh8sU7HK+oV3Dbi+BokuDcG3rIDA9AIkoU/iFABdnLKtohqo3brk5RVFcZaSn+hBxOL/QNYy5BF60U=',"+
                        "multiple_flags_signature:'T4PHZuYpL8hkPxw2R34hM9ZCYUN72SlJRC7lvG71W6xQpctrpCCV2wMnkDcooXQTfEA/MTB02CXYuBc0Rih69cAg75r6T0nwqrnHnS7kQja7aycENUiFeC1FDGJLKGzlQj8bzoI5b8aoes28Qzr7KyGIFluG0NOvp4EpkmcECIs=',"+
                        "version:2"+
                        "}"
        );

        Assert.assertEquals("privilege_test", privilegesEnabled.first);
        Set pSet = new HashSet(Arrays.asList(privilegesEnabled.second));
        Assert.assertTrue(pSet.contains("multiple_apps_unlimited"));
        Assert.assertTrue(pSet.contains("advanced_settings_access"));
    }

    @Test
    public void parseV2PayloadConfusing() throws Exception {
        //Test Payload: [{"username":"privilege_test"},{"flags":["advanced_settings_access"]}]
        Pair<String, String[]> privilegesEnabled = new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flag:'multiple_apps_unlimited',"+
                        "flags:['advanced_settings_access']," +
                        "signature:'c0GcoHux0dDME3510bR4jm1FEGY7rY5ISf5HvReeVM4G4pHucmo12juhEimofR41NsQLUmHmCDvh9ikG1gbiFTx037eM7nh8sU7HK+oV3Dbi+BokuDcG3rIDA9AIkoU/iFABdnLKtohqo3brk5RVFcZaSn+hBxOL/QNYy5BF60U=',"+
                        "multiple_flags_signature:'lekChRfk4hCpvrGv0jAUCiDl4fx2oKXiFAOvVbx9gj9XeJl1WI6WF7N72dBaweEgBo6ykJqQItVQ6TVAj9FuaSJXeRIrtBOHa8LTweAprqGIz0KtnbNnZohN9dM3QLW9uNNuUYTvHYaNFFtunFVf6w6IK/XK6WT5XLyZg2xl+bg=',"+
                        "version:2"+
                        "}"
        );

        Assert.assertEquals("privilege_test", privilegesEnabled.first);
        Set pSet = new HashSet(Arrays.asList(privilegesEnabled.second));
        Assert.assertFalse(pSet.contains("multiple_apps_unlimited"));
        Assert.assertTrue(pSet.contains("advanced_settings_access"));
    }

    @Test(expected = PrivilegesUtility.UnrecognizedPayloadVersionException.class)
    public void errorNewVersion() throws Exception {
        Pair<String, String[]> privilegesEnabled = new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flags:['multiple_apps_unlimited']," +
                        "multiple_flags_signature:'D6BBGW7dwlm1hpk0mSnWe/O/R2s8LIxFJmV6J3iTG6tGphjOWJ1i9MLDHMZ0UblFlHzIylfkYZts3wrAjc6JZQ5AhejQ8AMjVfSQsRqNx4oxVDVjvMQh/tYSi4YljHewGBD2BEuKzLx/QNZ9jI/N9ALSXes+t4gPip0NblLLyf8=',"+
                        "version:3"+
                        "}"
        );
    }
    @Test(expected = PrivilegesUtility.MalformedPayloadException.class)
    public void errorBadParse() throws Exception {
        new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{{{{{"
        );
    }


    @Test(expected = PrivilegesUtility.MalformedPayloadException.class)
    public void errorMissingArg() throws Exception {
        new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flags:['multiple_apps_unlimited']," +
                        "signature:'D6BBGW7dwlm1hpk0mSnWe/O/R2s8LIxFJmV6J3iTG6tGphjOWJ1i9MLDHMZ0UblFlHzIylfkYZts3wrAjc6JZQ5AhejQ8AMjVfSQsRqNx4oxVDVjvMQh/tYSi4YljHewGBD2BEuKzLx/QNZ9jI/N9ALSXes+t4gPip0NblLLyf8=',"+
                        "version:2"+
                        "}"
        );
    }


    @Test(expected = PrivilegesUtility.InvalidPrivilegeSignatureException.class)
    public void errorBadSignature() throws Exception {
        new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flags:['multiple_apps_unlimited']," +
                        "multiple_flags_signature:'c0GcoHux0dDME3510bR4jm1FEGY7rY5ISf5HvReeVM4G4pHucmo12juhEimofR41NsQLUmHmCDvh9ikG1gbiFTx037eM7nh8sU7HK+oV3Dbi+BokuDcG3rIDA9AIkoU/iFABdnLKtohqo3brk5RVFcZaSn+hBxOL/QNYy5BF60U=',"+
                        "version:2"+
                        "}"
        );
    }

    @Test(expected = PrivilegesUtility.InvalidPrivilegeSignatureException.class)
    public void errorMalformedSignature() throws Exception {
        new PrivilegesUtility(TEST_CERT_STRING).processPrivilegePayloadForActivatedPrivileges(
                "{username:'privilege_test'," +
                        "flags:['multiple_apps_unlimited']," +
                        "multiple_flags_signature:'Ceci n`est pas une signature',"+
                        "version:2"+
                        "}"
        );
    }


}
