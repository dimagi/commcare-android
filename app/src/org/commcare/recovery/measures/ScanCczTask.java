package org.commcare.recovery.measures;

import android.os.Environment;
import androidx.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.tasks.templates.CommCareTask;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipFile;

public class ScanCczTask extends CommCareTask<Void, File, File, ExecuteRecoveryMeasuresActivity> {


    private static final String CCZ_EXTENSION = ".ccz";
    private static final String PROFILE_FILE_NAME = "profile.ccpr";
    private static final int MAX_SCAN_DEPTH = 10;

    private File latestProfileFile = null;

    @Override
    protected File doTaskBackground(Void... voids) {
        locateAppCcz(getPathsToScan(), 0);
        return latestProfileFile;
    }

    @Nullable
    private void locateAppCcz(File[] files, int depth) {
        int latestVersion = -1;
        for (File f : files) {
            if (f.isDirectory() && depth <= MAX_SCAN_DEPTH) {
                locateAppCcz(f.listFiles(), depth + 1);
            } else if (f.isFile() && f.getName().endsWith(CCZ_EXTENSION)) {
                try {
                    Pair<String, Integer> profileIdAndVersion = getProfileIdAndVersionFromCcz(f);
                    String currentAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                    if (currentAppId.contentEquals(profileIdAndVersion.first)) {
                        // We have a match, if it's the latest version till now, return the ccz in update
                        if (profileIdAndVersion.second > latestVersion) {
                            latestVersion = profileIdAndVersion.second;
                            latestProfileFile = f;
                            publishProgress(latestProfileFile);
                        }
                    }
                } catch (IOException | InvalidStructureException | XmlPullParserException | UnfullfilledRequirementsException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Unzip profile and parses id and version from it
    private Pair<String, Integer> getProfileIdAndVersionFromCcz(File cczFile) throws IOException, UnfullfilledRequirementsException, XmlPullParserException, InvalidStructureException {
        InputStream profileStream = null;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(cczFile);
            profileStream = zipFile.getInputStream(zipFile.getEntry(PROFILE_FILE_NAME));
            return getProfileParser(profileStream).parse();
        } finally {
            StreamsUtil.closeStream(profileStream);
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    private ElementParser<Pair<String, Integer>> getProfileParser(InputStream profileStream) throws IOException {
        return new ElementParser<Pair<String, Integer>>(ElementParser.instantiateParser(profileStream)) {
            @Override
            public Pair<String, Integer> parse() throws InvalidStructureException {
                int version = parseInt(parser.getAttributeValue(null, "version"));
                String uniqueId = parser.getAttributeValue(null, "uniqueid");
                return new Pair<>(uniqueId, version);
            }
        };
    }


    private File[] getPathsToScan() {
        String lastKnownCczLocation = HiddenPreferences.getLastKnownCczLocation();
        ArrayList<File> filePathsToScan = new ArrayList<>(3);
        if (!StringUtils.isEmpty(lastKnownCczLocation)) {
            filePathsToScan.add(new File(lastKnownCczLocation));
        }
        filePathsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        filePathsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));
        return filePathsToScan.toArray(new File[filePathsToScan.size()]);
    }

    @Override
    protected void deliverResult(ExecuteRecoveryMeasuresActivity activity, File archive) {
        activity.onCczScanComplete();
    }

    @Override
    protected void deliverUpdate(ExecuteRecoveryMeasuresActivity activity, File... archive) {
        activity.updateCcz(archive[0]);
    }

    @Override
    protected void deliverError(ExecuteRecoveryMeasuresActivity activity, Exception e) {
        activity.onCczScanFailed(e);
    }
}
