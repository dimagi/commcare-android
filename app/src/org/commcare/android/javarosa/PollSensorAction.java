package org.commcare.android.javarosa;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import org.commcare.CommCareApplication;
import org.commcare.utils.GeoUtils;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.actions.Action;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.Recalculate;
import org.javarosa.core.model.data.AnswerDataFactory;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * XForms Action extension to periodically poll a sensor and optionally save
 * its value.
 *
 * @author jschweers
 */
public class PollSensorAction extends Action {

    public static final String ELEMENT_NAME = "pollsensor";
    public static final String KEY_UNRESOLVED_XPATH = "unresolved_xpath";
    public static final String XPATH_ERROR_ACTION = "poll_sensor_xpath_error_action";

    private FormDef formDef = null;
    private TreeReference contextRef;
    private TreeReference target;

    public PollSensorAction() {
        super(ELEMENT_NAME);
    }

    public PollSensorAction(TreeReference target) {
        super(ELEMENT_NAME);
        this.target = target;
    }

    /**
     * Deal with a pollsensor action: start getting a GPS fix, and prepare to
     * cancel after maximum amount of time.
     *
     * @param model The FormDef that triggered the action
     */
    @Override
    public TreeReference processAction(FormDef model, TreeReference contextRef, String event) {
        if (Action.EVENT_XFORMS_REVALIDATE.equals(event)) {
            // form is done so stop listening
            PollSensorController.INSTANCE.stopLocationPolling();
        } else {
            formDef = model;
            this.contextRef = contextRef;
            PollSensorController.INSTANCE.startLocationPolling(this);
        }
        return null;
    }

    void updateReference(Location location) {
        if (target != null) {
            String result = GeoUtils.locationToString(location);
            TreeReference qualifiedReference = contextRef == null ? target : target.contextualize(contextRef);
            EvaluationContext context = new EvaluationContext(formDef.getEvaluationContext(), qualifiedReference);
            AbstractTreeElement node = context.resolveReference(qualifiedReference);
            if (node == null) {
                Context applicationContext = CommCareApplication._();
                Intent xpathErrorIntent = new Intent(XPATH_ERROR_ACTION);
                xpathErrorIntent.putExtra(KEY_UNRESOLVED_XPATH, qualifiedReference.toString(true));
                applicationContext.sendStickyBroadcast(xpathErrorIntent);
            } else {
                int dataType = node.getDataType();
                IAnswerData val = Recalculate.wrapData(result, dataType);
                if (val == null) {
                    formDef.setValue(null, qualifiedReference);
                } else {
                    IAnswerData answer =
                            AnswerDataFactory.templateByDataType(dataType).cast(val.uncast());
                    formDef.setValue(answer, qualifiedReference);
                }
            }
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);

        target = (TreeReference)ExtUtil.read(in, new ExtWrapNullable(TreeReference.class), pf);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.write(out, new ExtWrapNullable(target));
    }
}
