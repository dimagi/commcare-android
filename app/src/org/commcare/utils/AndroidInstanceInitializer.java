package org.commcare.utils;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.DriftHelper;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.commcare.cases.ledger.Ledger;
import org.commcare.core.interfaces.UserSandbox;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.data.xml.VirtualInstances;
import org.commcare.engine.cases.AndroidIndexedFixtureInstanceTreeElement;
import org.commcare.engine.cases.AndroidLedgerInstanceTreeElement;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.ConcreteInstanceRoot;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.InstanceRoot;

/**
 * @author ctsims
 */
public class AndroidInstanceInitializer extends CommCareInstanceInitializer {

    /**
     * For testing: allows for data instanced backed evaluation when an app
     * isn't present.
     */
    public AndroidInstanceInitializer() {
        this(null, null, null);
    }

    public AndroidInstanceInitializer(SessionWrapper session) {
        this(session, new AndroidSandbox(CommCareApplication.instance()), CommCareApplication.instance().getCommCarePlatform());
    }

    public AndroidInstanceInitializer(SessionWrapper session, UserSandbox sandbox, CommCarePlatform platform) {
        super(session, sandbox, platform);
    }

    @Override
    protected InstanceRoot setupLedgerData(ExternalDataInstance instance) {
        if (stockbase == null) {
            SqlStorage<Ledger> storage = (SqlStorage<Ledger>)mSandbox.getLedgerStorage();
            stockbase = new AndroidLedgerInstanceTreeElement(instance.getBase(), storage);
        } else {
            //re-use the existing model if it exists.
            stockbase.rebase(instance.getBase());
        }
        return new ConcreteInstanceRoot(stockbase);
    }

    @Override
    protected InstanceRoot setupCaseData(ExternalDataInstance instance) {
        if (casebase == null) {
            SqlStorage<ACase> storage = (SqlStorage<ACase>)mSandbox.getCaseStorage();
            casebase = new CaseInstanceTreeElement(instance.getBase(), storage, new AndroidCaseIndexTable());
        } else {
            //re-use the existing model if it exists.
            casebase.rebase(instance.getBase());
        }
        instance.setCacheHost(casebase);
        return new ConcreteInstanceRoot(casebase);
    }

    @Override
    protected InstanceRoot setupFixtureData(ExternalDataInstance instance) {
        AbstractTreeElement indexedFixture = AndroidIndexedFixtureInstanceTreeElement.get(
                mSandbox,
                VirtualInstances.getReferenceId(instance.getReference()),
                instance.getBase());

        if (indexedFixture != null) {
            return new ConcreteInstanceRoot(indexedFixture);
        } else {
            return new ConcreteInstanceRoot(loadFixtureRoot(instance, instance.getReference()));
        }
    }

    @Override
    public String getVersionString() {
        return AppUtils.getCurrentVersionString();
    }

    @Override
    public String getDeviceId() {
        String phoneId = CommCareApplication.instance().getPhoneId();
        if (phoneId == null) {
            return super.getDeviceId();
        }
        return phoneId;
    }

    @Override
    protected long getCurrentDrift() {
        return DriftHelper.getCurrentDrift();
    }
}
