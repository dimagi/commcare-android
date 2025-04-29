package org.commcare.android.tests.processing;

import org.commcare.CommCareTestApplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.models.AndroidClassHasher;
import org.commcare.models.AndroidPrototypeFactory;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xform.util.XFormUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the serializaiton and deserialzation of XForms.
 *
 * @author ctsims
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FormStorageTest {
    private boolean noSerializiationExceptions;

    // Contains the names of all externalizable classes that have ever existed in CommCare, so as
    // to ensure that users on any prior version of CommCare will be able to load their saved
    // forms upon upgrading. When a class is migrated, it should NOT be removed from this list,
    // but should be moved to the bottom with a comment as to what version it was migrated in
    private static final List<String> completeHistoryOfExternalizableClasses = Arrays.asList(
            // current class names:
            "org.commcare.android.database.app.models.ResourceV13"
            , "org.commcare.android.database.app.models.UserKeyRecord"
            , "org.commcare.android.database.app.models.UserKeyRecordV1"
            , "org.commcare.android.database.global.models.AndroidSharedKeyRecord"
            , "org.commcare.android.database.global.models.ApplicationRecord"
            , "org.commcare.android.database.global.models.ApplicationRecordV1"
            , "org.commcare.android.database.user.models.ACase"
            , "org.commcare.android.database.user.models.ACasePreV6Model$CaseIndexUpdater"
            , "org.commcare.android.database.user.models.ACasePreV6Model"
            , "org.commcare.android.database.user.models.AUser"
            , "org.commcare.android.database.user.models.FormRecord"
            , "org.commcare.android.database.user.models.FormRecordV1"
            , "org.commcare.android.database.user.models.FormRecordV2"
            , "org.commcare.android.database.user.models.GeocodeCacheModel"
            , "org.commcare.android.database.user.models.SessionStateDescriptor"
            , "org.commcare.android.javarosa.AndroidLogEntry"
            , "org.commcare.android.javarosa.AndroidXFormExtensions"
            , "org.commcare.android.javarosa.IntentCallout"
            , "org.commcare.android.javarosa.PollSensorAction"
            , "org.commcare.android.javarosa.DeviceReportRecord"
            , "org.commcare.android.logging.ForceCloseLogEntry"
            , "org.commcare.android.resource.installers.LocaleAndroidInstaller"
            , "org.commcare.android.resource.installers.MediaFileAndroidInstaller"
            , "org.commcare.android.resource.installers.OfflineUserRestoreAndroidInstaller"
            , "org.commcare.android.resource.installers.ProfileAndroidInstaller"
            , "org.commcare.android.resource.installers.SuiteAndroidInstaller"
            , "org.commcare.android.resource.installers.XFormAndroidInstaller"
            , "org.commcare.android.storage.framework.Persisted"
            , "org.commcare.cases.instance.CaseDataInstance"
            , "org.commcare.cases.ledger.Ledger"
            , "org.commcare.cases.model.Case"
            , "org.commcare.cases.model.CaseIndex"
            , "org.commcare.cases.model.StorageIndexedTreeElementModel"
            , "org.commcare.logging.XPathErrorEntry"
            , "org.commcare.resources.model.Resource"
            , "org.commcare.resources.model.ResourceLocation"
            , "org.commcare.resources.model.installers.BasicInstaller"
            , "org.commcare.resources.model.installers.CacheInstaller"
            , "org.commcare.resources.model.installers.LocaleFileInstaller"
            , "org.commcare.resources.model.installers.LoginImageInstaller"
            , "org.commcare.resources.model.installers.MediaInstaller"
            , "org.commcare.resources.model.installers.OfflineUserRestoreInstaller"
            , "org.commcare.resources.model.installers.ProfileInstaller"
            , "org.commcare.resources.model.installers.SuiteInstaller"
            , "org.commcare.resources.model.installers.XFormInstaller"
            , "org.commcare.session.SessionFrame"
            , "org.commcare.suite.model.Action"
            , "org.commcare.suite.model.AssertionSet"
            , "org.commcare.suite.model.Callout"
            , "org.commcare.suite.model.ComputedDatum"
            , "org.commcare.suite.model.Detail"
            , "org.commcare.suite.model.DetailField"
            , "org.commcare.suite.model.Global"
            , "org.commcare.suite.model.GeoOverlay"
            , "org.commcare.suite.model.DisplayUnit"
            , "org.commcare.suite.model.EntityDatum"
            , "org.commcare.suite.model.Entry"
            , "org.commcare.suite.model.FormEntry"
            , "org.commcare.suite.model.FormIdDatum"
            , "org.commcare.suite.model.Menu"
            , "org.commcare.suite.model.OfflineUserRestore"
            , "org.commcare.suite.model.Profile"
            , "org.commcare.suite.model.PropertySetter"
            , "org.commcare.suite.model.RemoteQueryDatum"
            , "org.commcare.suite.model.SessionDatum"
            , "org.commcare.suite.model.StackFrameStep"
            , "org.commcare.suite.model.StackOperation"
            , "org.commcare.suite.model.Suite"
            , "org.commcare.suite.model.RemoteRequestEntry"
            , "org.commcare.suite.model.PostRequest"
            , "org.commcare.suite.model.Text"
            , "org.commcare.suite.model.ViewEntry"
            , "org.commcare.suite.model.graph.Annotation"
            , "org.commcare.suite.model.graph.BubbleSeries"
            , "org.commcare.suite.model.graph.Graph"
            , "org.commcare.suite.model.graph.XYSeries"
            , "org.javarosa.core.log.LogEntry"
            , "org.javarosa.core.model.FormDef"
            , "org.javarosa.core.model.GroupDef"
            , "org.javarosa.core.model.ItemsetBinding"
            , "org.javarosa.core.model.QuestionDef"
            , "org.javarosa.core.model.QuestionString"
            , "org.javarosa.core.model.SelectChoice"
            , "org.javarosa.core.model.SubmissionProfile"
            , "org.javarosa.core.model.UploadQuestionExtension"
            , "org.javarosa.core.model.User"
            , "org.javarosa.core.model.actions.Action"
            , "org.javarosa.core.model.actions.ActionController"
            , "org.javarosa.core.model.actions.SetValueAction"
            , "org.javarosa.core.model.condition.Condition"
            , "org.javarosa.core.model.condition.Constraint"
            , "org.javarosa.core.model.condition.Recalculate"
            , "org.javarosa.core.model.condition.Triggerable"
            , "org.javarosa.core.model.data.BooleanData"
            , "org.javarosa.core.model.data.DateData"
            , "org.javarosa.core.model.data.DateTimeData"
            , "org.javarosa.core.model.data.DecimalData"
            , "org.javarosa.core.model.data.GeoPointData"
            , "org.javarosa.core.model.data.IntegerData"
            , "org.javarosa.core.model.data.LongData"
            , "org.javarosa.core.model.data.PointerAnswerData"
            , "org.javarosa.core.model.data.SelectMultiData"
            , "org.javarosa.core.model.data.SelectOneData"
            , "org.javarosa.core.model.data.StringData"
            , "org.javarosa.core.model.data.TimeData"
            , "org.javarosa.core.model.data.UncastData"
            , "org.javarosa.core.model.data.helper.Selection"
            , "org.javarosa.core.model.instance.DataInstance"
            , "org.javarosa.core.model.instance.ExternalDataInstance"
            , "org.javarosa.core.model.instance.FormInstance"
            , "org.javarosa.core.model.instance.TreeElement"
            , "org.javarosa.core.model.instance.TreeReference"
            , "org.javarosa.core.model.instance.TreeReferenceLevel"
            , "org.javarosa.core.reference.ReferenceDataSource"
            , "org.javarosa.core.reference.RootTranslator"
            , "org.javarosa.core.services.locale.Localizer"
            , "org.javarosa.core.services.locale.TableLocaleSource"
            , "org.javarosa.core.services.properties.Property"
            , "org.javarosa.core.services.transport.payload.ByteArrayPayload"
            , "org.javarosa.core.services.transport.payload.DataPointerPayload"
            , "org.javarosa.core.services.transport.payload.MultiMessagePayload"
            , "org.javarosa.core.util.SortedIntSet"
            , "org.javarosa.core.util.externalizable.ExtWrapIntEncoding"
            , "org.javarosa.core.util.externalizable.ExtWrapIntEncodingSmall"
            , "org.javarosa.core.util.externalizable.ExtWrapIntEncodingUniform"
            , "org.javarosa.core.util.externalizable.ExtWrapList"
            , "org.javarosa.core.util.externalizable.ExtWrapListPoly"
            , "org.javarosa.core.util.externalizable.ExtWrapMap"
            , "org.javarosa.core.util.externalizable.ExtWrapMapPoly"
            , "org.javarosa.core.util.externalizable.ExtWrapNullable"
            , "org.javarosa.core.util.externalizable.ExtWrapTagged"
            , "org.javarosa.core.util.externalizable.ExternalizableWrapper"
            , "org.javarosa.form.api.FormEntryAction"
            , "org.javarosa.form.api.FormEntrySession"
            , "org.javarosa.model.xform.XPathReference"
            , "org.javarosa.xpath.XPathConditional"
            , "org.javarosa.xpath.expr.XPathArithExpr"
            , "org.javarosa.xpath.expr.XPathBinaryOpExpr"
            , "org.javarosa.xpath.expr.XPathBoolExpr"
            , "org.javarosa.xpath.expr.XPathCmpExpr"
            , "org.javarosa.xpath.expr.XPathEqExpr"
            , "org.javarosa.xpath.expr.XPathExpression"
            , "org.javarosa.xpath.expr.XPathFilterExpr"
            , "org.javarosa.xpath.expr.XPathFuncExpr"
            , "org.javarosa.xpath.expr.XPathNumNegExpr"
            , "org.javarosa.xpath.expr.XPathNumericLiteral"
            , "org.javarosa.xpath.expr.XPathOpExpr"
            , "org.javarosa.xpath.expr.XPathPathExpr"
            , "org.javarosa.xpath.expr.XPathQName"
            , "org.javarosa.xpath.expr.XPathStep"
            , "org.javarosa.xpath.expr.XPathStringLiteral"
            , "org.javarosa.xpath.expr.XPathUnaryOpExpr"
            , "org.javarosa.xpath.expr.XPathUnionExpr"
            , "org.javarosa.xpath.expr.XPathVariableReference"
            , "org.javarosa.xpath.expr.XPathAbsFunc"
            , "org.javarosa.xpath.expr.XPathAcosFunc"
            , "org.javarosa.xpath.expr.XPathAsinFunc"
            , "org.javarosa.xpath.expr.XPathAtanFunc"
            , "org.javarosa.xpath.expr.XPathAtanTwoFunc"
            , "org.javarosa.xpath.expr.XPathBooleanFromStringFunc"
            , "org.javarosa.xpath.expr.XPathBooleanFunc"
            , "org.javarosa.xpath.expr.XPathCeilingFunc"
            , "org.javarosa.xpath.expr.XPathChecklistFunc"
            , "org.javarosa.xpath.expr.XPathConcatFunc"
            , "org.javarosa.xpath.expr.XPathCondFunc"
            , "org.javarosa.xpath.expr.XPathContainsFunc"
            , "org.javarosa.xpath.expr.XPathCosFunc"
            , "org.javarosa.xpath.expr.XPathCountFunc"
            , "org.javarosa.xpath.expr.XPathCountSelectedFunc"
            , "org.javarosa.xpath.expr.XPathCustomRuntimeFunc"
            , "org.javarosa.xpath.expr.XPathDateFunc"
            , "org.javarosa.xpath.expr.XPathDependFunc"
            , "org.javarosa.xpath.expr.XPathDistanceFunc"
            , "org.javarosa.xpath.expr.XPathDoubleFunc"
            , "org.javarosa.xpath.expr.XPathEndsWithFunc"
            , "org.javarosa.xpath.expr.XPathExpFunc"
            , "org.javarosa.xpath.expr.XPathExpression"
            , "org.javarosa.xpath.expr.XPathFalseFunc"
            , "org.javarosa.xpath.expr.XPathFloorFunc"
            , "org.javarosa.xpath.expr.XPathFormatDateForCalendarFunc"
            , "org.javarosa.xpath.expr.XPathFormatDateFunc"
            , "org.javarosa.xpath.expr.XPathFuncExpr"
            , "org.javarosa.xpath.expr.XPathIfFunc"
            , "org.javarosa.xpath.expr.XPathIntFunc"
            , "org.javarosa.xpath.expr.XPathJoinFunc"
            , "org.javarosa.xpath.expr.XPathLogFunc"
            , "org.javarosa.xpath.expr.XPathLogTenFunc"
            , "org.javarosa.xpath.expr.XPathLowerCaseFunc"
            , "org.javarosa.xpath.expr.XPathMaxFunc"
            , "org.javarosa.xpath.expr.XPathMinFunc"
            , "org.javarosa.xpath.expr.XPathNotFunc"
            , "org.javarosa.xpath.expr.XPathNowFunc"
            , "org.javarosa.xpath.expr.XPathNumberFunc"
            , "org.javarosa.xpath.expr.XPathPathExpr"
            , "org.javarosa.xpath.expr.XPathPiFunc"
            , "org.javarosa.xpath.expr.XPathPositionFunc"
            , "org.javarosa.xpath.expr.XPathPowFunc"
            , "org.javarosa.xpath.expr.XPathRandomFunc"
            , "org.javarosa.xpath.expr.XPathRegexFunc"
            , "org.javarosa.xpath.expr.XPathReplaceFunc"
            , "org.javarosa.xpath.expr.XPathRoundFunc"
            , "org.javarosa.xpath.expr.XPathSelectedAtFunc"
            , "org.javarosa.xpath.expr.XPathSelectedFunc"
            , "org.javarosa.xpath.expr.XPathSinFunc"
            , "org.javarosa.xpath.expr.XPathSqrtFunc"
            , "org.javarosa.xpath.expr.XPathStartsWithFunc"
            , "org.javarosa.xpath.expr.XPathStringFunc"
            , "org.javarosa.xpath.expr.XPathStringLengthFunc"
            , "org.javarosa.xpath.expr.XPathSubstrFunc"
            , "org.javarosa.xpath.expr.XPathSubstringAfterFunc"
            , "org.javarosa.xpath.expr.XPathSubstringBeforeFunc"
            , "org.javarosa.xpath.expr.XPathSumFunc"
            , "org.javarosa.xpath.expr.XPathTanFunc"
            , "org.javarosa.xpath.expr.XPathTodayFunc"
            , "org.javarosa.xpath.expr.XPathTranslateFunc"
            , "org.javarosa.xpath.expr.XPathTrueFunc"
            , "org.javarosa.xpath.expr.XPathUpperCaseFunc"
            , "org.javarosa.xpath.expr.XPathUuidFunc"
            , "org.javarosa.xpath.expr.XPathWeightedChecklistFunc"
            , "org.javarosa.xpath.expr.XpathCoalesceFunc"

            // Migrated in 2.28
            , "org.odk.collect.android.jr.extensions.AndroidXFormExtensions"
            , "org.odk.collect.android.jr.extensions.IntentCallout"
            , "org.odk.collect.android.jr.extensions.PollSensorAction"

            // Removed in 2.32, but doesn't require migration because it was
            // never actually serialized
            //, "org.javarosa.core.model.DataBinding"

            // Added in 2.35
            , "org.javarosa.xpath.expr.XPathJoinChunkFunc"
            , "org.javarosa.xpath.expr.XPathIdCompressFunc"

            // Added in 2.36
            , "org.commcare.heartbeat.UpdateToPrompt"
            , "org.commcare.android.database.global.models.AppAvailableToInstall"
            , "org.javarosa.xpath.expr.XPathChecksumFunc"

            // Added in 2.37
            , "org.commcare.core.graph.suite.Graph"
            , "org.commcare.core.graph.suite.Annotation"
            , "org.commcare.core.graph.suite.BubbleSeries"
            , "org.commcare.core.graph.suite.XYSeries"

            // Removed in 2.37
            //, "org.commcare.xml.DummyGraphParser$DummyGraphDetailTemplate";

            // Added in 2.38
            , "org.javarosa.xpath.expr.XPathSortFunc"
            , "org.javarosa.xpath.expr.XPathSortByFunc"

            // Added in 2.39
            , "org.commcare.android.database.user.models.FormRecordV3"


            // Added in 2.40
            , "org.javarosa.core.model.actions.SendAction"

            // Added in 2.41
            , "org.javarosa.xpath.expr.XPathDistinctValuesFunc"
            , "org.javarosa.xpath.expr.XPathSleepFunc"

            // Added in 2.44
            , "org.commcare.android.database.user.models.FormRecordV4"
            , "org.commcare.android.database.app.models.FormDefRecord"
            , "org.commcare.android.resource.installers.XFormAndroidInstallerV8"
            , "org.commcare.android.resource.installers.XFormUpdateInfoInstaller"
            , "org.commcare.android.database.user.models.ACasePreV24Model"

            // Added in 2.45
            , "org.commcare.recovery.measures.RecoveryMeasure"
            , "org.javarosa.xpath.expr.CacheableExprState"

            // Added in 2.47
            , "org.commcare.android.database.app.models.FormDefRecordV12"

            // Added in 2.49
            , "org.javarosa.xpath.expr.XPathIndexOfFunc"

            // Added in 2.50
            , "org.commcare.suite.model.QueryPrompt"

            // Added in 2.51
            , "org.commcare.suite.model.Endpoint"
            , "org.javarosa.xpath.expr.XPathEncryptStringFunc"

            // Added in 2.52
            , "org.commcare.android.database.user.models.FormRecordV5"
            , "org.commcare.android.database.user.models.ACasePreV28Model"

            // Added in 2.53
            , "org.javarosa.xpath.expr.XPathDecryptStringFunc"
            , "org.javarosa.xpath.expr.XPathJsonPropertyFunc"
            , "org.commcare.suite.model.AndroidPackageDependency"

            // Added in 2.54
            , "org.commcare.suite.model.QueryPromptCondition"
            , "org.commcare.suite.model.MultiSelectEntityDatum"
            , "org.commcare.suite.model.ListQueryData"
            , "org.commcare.suite.model.ValueQueryData"
            , "org.javarosa.core.util.externalizable.ExtWrapMultiMap"
            , "org.javarosa.core.model.instance.ExternalDataInstanceSource"
            , "org.commcare.suite.model.DetailGroup"
            , "org.commcare.services.FCMMessageData"
            , "org.commcare.suite.model.EndpointArgument"
            , "org.commcare.suite.model.EndpointAction"
            , "org.commcare.suite.model.QueryGroup"

            // Added in 2.55
            , "org.javarosa.core.model.FormIndex"
            , "org.commcare.models.database.InterruptedFormState"
            , "org.commcare.android.database.connect.models.ConnectLinkedAppRecordV3"
            , "org.commcare.android.database.connect.models.ConnectLinkedAppRecordV8"
            , "org.commcare.android.database.connect.models.ConnectLinkedAppRecordV9"
            , "org.commcare.android.database.connect.models.ConnectLinkedAppRecord"
            , "org.commcare.android.database.connect.models.ConnectUserRecordV5"
            , "org.commcare.android.database.connect.models.ConnectUserRecord"
            , "org.commcare.android.database.connect.models.ConnectAppRecord"
            , "org.commcare.android.database.connect.models.ConnectJobDeliveryRecordV2"
            , "org.commcare.android.database.connect.models.ConnectJobDeliveryRecord"
            , "org.commcare.android.database.connect.models.ConnectJobPaymentRecordV3"
            , "org.commcare.android.database.connect.models.ConnectJobPaymentRecord"
            , "org.commcare.android.database.connect.models.ConnectJobRecordV2"
            , "org.commcare.android.database.connect.models.ConnectJobRecordV4"
            , "org.commcare.android.database.connect.models.ConnectJobRecordV7"
            , "org.commcare.android.database.connect.models.ConnectJobRecord"
            , "org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord"
            , "org.commcare.android.database.connect.models.ConnectJobLearningRecord"
            , "org.commcare.android.database.connect.models.ConnectJobAssessmentRecord"
            , "org.commcare.android.database.global.models.ConnectKeyRecord"
            , "org.commcare.android.database.global.models.ConnectKeyRecordV6"
            , "org.commcare.android.database.connect.models.ConnectPaymentUnitRecord"
    );


    @Before
    public void setup() {
        XFormAndroidInstaller.registerAndroidLevelFormParsers();
    }

    @Test
    public void testAllExternalizablesInPrototypeFactory() {
        PrototypeFactory pf = TestUtils.getStaticPrototypeFactory();
        List<String> extClassesInPF =
                CommCareTestApplication.getTestPrototypeFactoryClasses();

        // Ensure all externalizable classes are present in list of classes.
        // Enforcing this keeps the list up-to-date, which is crucial for the loop check below
        for (String className : extClassesInPF) {
            // Should fail if a new class implementing externalizable is added
            // without updating the list used by this test.
            assertTrue(
                    "Please keep test list up-to-date by adding '" + className + "' to list",
                    completeHistoryOfExternalizableClasses.contains(className));
        }

        // Ensure that any renamed externalizable classes are properly migrated
        for (String className : completeHistoryOfExternalizableClasses) {
            Assert.assertNotNull(
                    "The class '" + className + "' wasn't properly migrated in the prototype factory. " +
                            "A migration strategy for this class should be added in AndroidPrototypeFactory.",
                    pf.getClass(AndroidClassHasher.getInstance().getClassnameHash(className)));
        }

        // For completeness, make sure that migrated classes are present in the test class list used
        for (String className : AndroidPrototypeFactory.getMigratedClassNames()) {
            assertTrue("The migrated class '" + className + "' isn't represented in the test list",
                    completeHistoryOfExternalizableClasses.contains(className));
        }

    }

    @Test
    public void testRegressionXFormSerializations() {
        try {
            InputStream is = getClass().getResourceAsStream("/forms/placeholder.xml");
            FormDef def = XFormUtils.getFormFromInputStream(is);
            ExtUtil.deserialize(ExtUtil.serialize(def), FormDef.class,
                    TestUtils.getStaticPrototypeFactory());
        } catch (IOException | DeserializationException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Ensure that a form that has intent callouts can be serialized and deserialized
     */
    @Test
    public void testCalloutSerializations() {
        try {
            InputStream is = getClass().getResourceAsStream("/forms/intent_callout_serialization_test.xml");
            FormDef def = XFormUtils.getFormFromInputStream(is);
            ExtUtil.deserialize(ExtUtil.serialize(def), FormDef.class, TestUtils.getStaticPrototypeFactory());
        } catch (IOException | DeserializationException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    @Test
    public void parallelFormRecordSerializationTest() {
        noSerializiationExceptions = true;
        // Make sure that Persited externalization works in a mult-thread setting
        // Important because setting field accessibility can lead to to throws of IllegalAccessException
        Thread t1 = new BulkFormRecordSerializer();
        Thread t2 = new BulkFormRecordSerializer();
        Thread t3 = new BulkFormRecordSerializer();
        t1.start();
        t2.start();
        t3.start();
        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        assertTrue(noSerializiationExceptions);
    }

    private class BulkFormRecordSerializer extends Thread {
        @Override
        public void run() {
            int i = 10;
            while (i-- > 0) {
                try {
                    serializeFormRecord();
                } catch (Exception e) {
                    e.printStackTrace();
                    noSerializiationExceptions = false;
                }
            }
        }
    }

    private static void serializeFormRecord() throws IOException, DeserializationException {
        FormRecord r = new FormRecord(FormRecord.STATUS_UNSTARTED, "some form",
                new byte[]{1, 2, 3}, null, new Date(0), "some app id");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        r.writeExternal(new DataOutputStream(bos));
        FormRecord newRecord = new FormRecord();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(bos.toByteArray());
        newRecord.readExternal(new DataInputStream(inputStream), TestUtils.getStaticPrototypeFactory());
    }
}
