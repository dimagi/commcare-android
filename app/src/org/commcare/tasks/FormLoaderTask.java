package org.commcare.tasks;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.javarosa.AndroidXFormHttpRequester;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.engine.extensions.XFormExtensionUtils;
import org.commcare.logging.UserCausedRuntimeException;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.logic.AndroidFormController;
import org.commcare.logic.FileReferenceFactory;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.trace.EvaluationTraceReporter;
import org.javarosa.core.model.trace.TraceSerialization;
import org.javarosa.core.model.trace.ReducingTraceReporter;
import org.javarosa.core.model.utils.InstrumentationUtils;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xpath.XPathException;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public abstract class FormLoaderTask<R> extends CommCareTask<Integer, String, FormLoaderTask.FECWrapper, R> {
    public static InstanceInitializationFactory iif;

    private final SecretKeySpec mSymetricKey;
    private final boolean mReadOnly;
    private final boolean recordEntrySession;

    private EvaluationTraceReporter traceReporterForFullForm;
    private final boolean profilingEnabledForFormLoad = false;

    private final R activity;
    private final String formRecordPath;

    private FECWrapper data;

    public static final int FORM_LOADER_TASK_ID = 16;

    public FormLoaderTask(SecretKeySpec symetricKey, boolean readOnly,
                          boolean recordEntrySession, String formRecordPath, R activity) {
        this.mSymetricKey = symetricKey;
        this.mReadOnly = readOnly;
        this.activity = activity;
        this.taskId = FORM_LOADER_TASK_ID;
        this.recordEntrySession = recordEntrySession;
        this.formRecordPath = formRecordPath;
        TAG = FormLoaderTask.class.getSimpleName();
    }

    /**
     * Initialize {@link FormEntryController} with {@link FormDef} from binary or from XML. If given
     * an instance, it will be used to fill the {@link FormDef}.
     */
    @Override
    protected FECWrapper doTaskBackground(Integer... formDefId) {
        FormDef fd = null;
        long start = System.currentTimeMillis();
        FormDefRecord formDefRecord = FormDefRecord.getFormDef(
                CommCareApplication.instance().getAppStorage(FormDefRecord.class),
                formDefId[0]);

        File formXml = new File(formDefRecord.getFilePath());
        String formHash = FileUtil.getMd5Hash(formXml);
        File formBin = getCachedForm(formHash);

        if (formBin.exists()) {
            // if we have binary, deserialize binary
            Log.i(TAG, "Attempting to load " + formXml.getName() +
                    " from cached file: " + formBin.getAbsolutePath());
            fd = deserializeFormDef((Context)activity, formBin);
            if (fd == null) {
                Logger.log(LogTypes.TYPE_RESOURCES,
                        "Deserialization of " + formXml.getName() + " form failed.");
                // Remove the file, and make a new .formdef from xml
                formBin.delete();
            }
        }

        // If we couldn't find a cached version, load the form from the XML
        if (fd == null) {
            fd = loadFormFromFile(formXml);
        }

        // Try to write the form definition to a cached location
        try {
            serializeFormDef(fd, formDefRecord.getFilePath());
        } catch (Exception e) {
            // The cache is a bonus, so if we can't write it, don't crash, but log 
            // it so we can clean up whatever is preventing the cached version from
            // working
            Logger.log(LogTypes.TYPE_RESOURCES, "XForm could not be serialized. Error trace:\n" + ForceCloseLogger.getStackTrace(e));
        }

        FormEntryController fec = initFormDef(fd);

        // Remove previous forms
        ReferenceManager.instance().clearSession();

        setupFormMedia(formDefRecord.getMediaPath(), formXml);

        AndroidFormController formController = new AndroidFormController(fec, mReadOnly);

        data = new FECWrapper(formController);
        Logger.log("profiling", "Form Loading End " + (System.currentTimeMillis() - start));
        return data;
    }

    private FormDef loadFormFromFile(File formXmlFile) {
        FileInputStream fis;
        // no binary, read from xml
        Log.i(TAG, "Attempting to load from: " + formXmlFile.getAbsolutePath());
        try {
            fis = new FileInputStream(formXmlFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error reading XForm file", e);
        }
        XFormAndroidInstaller.registerAndroidLevelFormParsers();
        FormDef fd = XFormExtensionUtils.getFormFromInputStream(fis);
        if (fd == null) {
            throw new RuntimeException("Error reading XForm file: FormDef is null");
        }
        if (DeveloperPreferences.useExpressionCachingInForms()) {
            fd.enableExpressionCaching();
        }
        return fd;
    }

    private String getSystemLocale() {
        Localizer mLocalizer = Localization.getGlobalLocalizerAdvanced();

        if (mLocalizer != null) {
            return mLocalizer.getLocale();
        } else {
            Logger.log(LogTypes.TYPE_ERROR_ASSERTION,
                    "Could not get the localizer during form init");
        }
        return null;
    }

    private FormEntryController initFormDef(FormDef formDef) {
        long start = System.currentTimeMillis();
        setupAndroidPlatformImplementations(formDef);

        // create FormEntryController from formdef
        FormEntryModel fem = new FormEntryModel(formDef);
        FormEntryController fec;
        if (recordEntrySession) {
            fec = FormEntryController.buildRecordingController(fem);
        } else {
            fec = new FormEntryController(fem);
        }

        //TODO: Get a reasonable IIF object

        boolean isNewFormInstance = formRecordPath == null;

        if (!isNewFormInstance) {
            importData(formRecordPath, fec);
        }

        EvaluationTraceReporter reporter = null;
        if (profilingOnFullForm()) {
            reporter = this.traceReporterForFullForm;
        } else if (profilingEnabledForFormLoad) {
            reporter = new ReducingTraceReporter(true);
        }
        if (reporter != null) {
            formDef.getEvaluationContext().setDebugModeOn(reporter);
        }

        try {
            formDef.initialize(isNewFormInstance, iif, getSystemLocale(), mReadOnly);
        } catch (XPathException e) {
            XPathErrorLogger.INSTANCE.logErrorToCurrentApp(e);
            throw new UserCausedRuntimeException(e.getMessage(), e);
        } catch (CommCareInstanceInitializer.FixtureInitializationException e) {
            throw new UserCausedRuntimeException(e.getMessage(), e);
        }

        if (!profilingOnFullForm() && profilingEnabledForFormLoad) {
            InstrumentationUtils.printAndClearTraces(reporter, "FORM LOAD TRACE:", TraceSerialization.TraceInfoType.CACHE_INFO_ONLY);
            InstrumentationUtils.printExpressionsThatUsedCaching(reporter, "FORM LOAD CACHE USAGE:");
        }

        if (mReadOnly) {
            formDef.getInstance().getRoot().setEnabled(false);
        }
        Logger.log("profiling", "initFormDef " + (System.currentTimeMillis() - start));
        return fec;
    }

    private void setupFormMedia(String formMediaPath, File formXmlFile) {
        if (formMediaPath != null) {
            ReferenceManager.instance().addSessionRootTranslator(
                    new RootTranslator("jr://images/", formMediaPath));
            ReferenceManager.instance().addSessionRootTranslator(
                    new RootTranslator("jr://audio/", formMediaPath));
            ReferenceManager.instance().addSessionRootTranslator(
                    new RootTranslator("jr://video/", formMediaPath));
        } else {
            // This should get moved to the Application Class
            if (ReferenceManager.instance().getFactories().length == 0) {
                // this is /sdcard/odk
                Logger.log(LogTypes.SOFT_ASSERT, "Access outside scoped storage in form media setup.");
                ReferenceManager.instance().addReferenceFactory(
                        new FileReferenceFactory(Environment.getExternalStorageDirectory() + "/odk"));
            }

            // set paths to /sdcard/odk/forms/formfilename-media/
            String formFileName = formXmlFile.getName().substring(0, formXmlFile.getName().lastIndexOf("."));

            // Set jr://... to point to /sdcard/odk/forms/filename-media/
            ReferenceManager.instance().addSessionRootTranslator(
                    new RootTranslator("jr://images/", "jr://file/forms/" + formFileName + "-media/"));
            ReferenceManager.instance().addSessionRootTranslator(
                    new RootTranslator("jr://audio/", "jr://file/forms/" + formFileName + "-media/"));
            ReferenceManager.instance().addSessionRootTranslator(
                    new RootTranslator("jr://video/", "jr://file/forms/" + formFileName + "-media/"));
        }
    }

    private boolean importData(String filePath, FormEntryController fec) {
        // convert files into a byte array
        InputStream is;
        try {
            is = EncryptionIO.getFileInputStream(filePath, mSymetricKey);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to open encrypted form instance file: " + filePath);
        }

        // get the root of the saved and template instances
        TreeElement savedRoot;
        try {
            savedRoot = XFormParser.restoreDataModel(is, null).getRoot();
        } catch (IOException e) {
            e.printStackTrace();
            throw new XFormParseException("Bad parsing from byte array " + e.getMessage());
        }
        TreeElement templateRoot = fec.getModel().getForm().getInstance().getRoot().deepCopy(true);

        // weak check for matching forms
        if (!savedRoot.getName().equals(templateRoot.getName()) || savedRoot.getMult() != 0) {
            Log.e(TAG, "Saved form instance does not match template form definition");
            return false;
        } else {
            // populate the data model
            TreeReference tr = TreeReference.rootRef();
            tr.add(templateRoot.getName(), TreeReference.INDEX_UNBOUND);
            templateRoot.populate(savedRoot);

            // populated model to current form
            fec.getModel().getForm().getInstance().setRoot(templateRoot);
            return true;
        }
    }

    /**
     * Read serialized {@link FormDef} from file and recreate as object.
     */
    private static FormDef deserializeFormDef(Context context, File formDefFile) {
        FileInputStream fis = null;
        DataInputStream dis = null;
        FormDef fd;
        try {
            // create new form def
            fd = new FormDef(DeveloperPreferences.useExpressionCachingInForms());
            fis = new FileInputStream(formDefFile);
            dis = new DataInputStream(new BufferedInputStream(fis));

            // read serialized formdef into new formdef
            fd.readExternal(dis, CommCareApplication.instance().getPrototypeFactory(context));
        } catch (Throwable e) {
            e.printStackTrace();
            fd = null;
        } finally {
            StreamsUtil.closeStream(fis);
            StreamsUtil.closeStream(dis);
        }

        return fd;
    }

    /**
     * Write the FormDef to the file system as a binary blob.
     */
    private void serializeFormDef(FormDef fd, String formFilePath) throws IOException {
        // calculate unique md5 identifier for this form
        String hash = FileUtil.getMd5Hash(new File(formFilePath));
        File formDef = getCachedForm(hash);

        // create a serialized form file if there isn't already one at this hash
        if (!formDef.exists()) {
            OutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(formDef);
                DataOutputStream dos;
                outputStream = dos = new DataOutputStream(outputStream);
                fd.writeExternal(dos);
                dos.flush();
            } finally {
                //make sure we clean up the stream
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        // Swallow this. If we threw an exception from inside the 
                        // try, this close exception will trump it on the return 
                        // path, and we care a lot more about that exception
                        // than this one.
                    }
                }
            }
        }
    }

    private File getCachedForm(String hash) {
        return new File(CommCareApplication.instance().getCurrentApp().
                fsPath(GlobalConstants.FILE_CC_CACHE) + "/" + hash + ".formdef");
    }

    public void destroy() {
        if (data != null) {
            data.free();
            data = null;
        }
    }

    /**
     * Configure android specific platform code for the provided formDef Object
     */
    public void setupAndroidPlatformImplementations(FormDef formDef) {
        formDef.setSendCalloutHandler(new AndroidXFormHttpRequester());
    }

    public void setProfilingOnFullForm(EvaluationTraceReporter reporter) {
        this.traceReporterForFullForm = reporter;
    }

    private boolean profilingOnFullForm() {
        return traceReporterForFullForm != null;
    }

    protected static class FECWrapper {
        AndroidFormController controller;

        protected FECWrapper(AndroidFormController controller) {
            this.controller = controller;
        }

        public AndroidFormController getController() {
            return controller;
        }

        protected void free() {
            controller = null;
        }
    }
}
