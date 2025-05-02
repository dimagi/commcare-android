package org.commcare.views.widgets;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.commcare.android.javarosa.AndroidXFormExtensions;
import org.commcare.android.javarosa.IntentCallout;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.AndroidArrayDataSource;
import org.javarosa.core.model.ComboboxFilterRule;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FuzzyMatchFilterRule;
import org.javarosa.core.model.MultiWordFilterRule;
import org.javarosa.core.model.QuestionDataExtension;
import org.javarosa.core.model.StandardFilterRule;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xform.util.CalendarUtils;

/**
 * Convenience class that handles creation of widgets.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class WidgetFactory {

    private final FormDef formDef;
    private final PendingCalloutInterface pendingCalloutInterface;

    public WidgetFactory(FormDef formDef, PendingCalloutInterface pendingCalloutInterface) {
        this.formDef = formDef;
        this.pendingCalloutInterface = pendingCalloutInterface;
    }

    /**
     * Returns the appropriate QuestionWidget for the given FormEntryPrompt.
     *  @param fep     prompt element to be rendered
     * @param context Android context
     */
    public QuestionWidget createWidgetFromPrompt(FormEntryPrompt fep, Context context, boolean inCompactGroup) {
        QuestionWidget questionWidget;
        String appearance = fep.getAppearanceHint();
        switch (fep.getControlType()) {
            case Constants.CONTROL_INPUT:
                if (appearance != null && appearance.startsWith("intent:")) {
                    questionWidget = buildIntentWidget(appearance, fep, context);
                    break;
                }
            case Constants.CONTROL_SECRET:
                questionWidget = buildBasicWidget(appearance, fep, context, inCompactGroup);
                break;
            case Constants.CONTROL_IMAGE_CHOOSE:
                if (appearance != null && appearance.equals("signature")) {
                    questionWidget = new SignatureWidget(context, fep, pendingCalloutInterface);
                } else {
                    questionWidget = new ImageWidget(context, fep, pendingCalloutInterface);
                }
                break;
            case Constants.CONTROL_AUDIO_CAPTURE:
                if (appearance != null && appearance.contains("legacy")) {
                    questionWidget = new AudioWidget(context, fep, pendingCalloutInterface);
                } else {
                    questionWidget = new CommCareAudioWidget(context, fep, pendingCalloutInterface);
                }
                break;
            case Constants.CONTROL_VIDEO_CAPTURE:
                questionWidget = new VideoWidget(context, fep, pendingCalloutInterface);
                break;
            case Constants.CONTROL_DOCUMENT_UPLOAD:
                questionWidget = new DocumentWidget(context, fep, pendingCalloutInterface);
                break;
            case Constants.CONTROL_SELECT_ONE:
                questionWidget = buildSelectOne(appearance, fep, context);
                break;
            case Constants.CONTROL_SELECT_MULTI:
                questionWidget = buildSelectMulti(appearance, fep, context);
                break;
            case Constants.CONTROL_TRIGGER:
                questionWidget = new TriggerWidget(context, fep, appearance);
                break;
            default:
                questionWidget = new StringWidget(context, fep, false, inCompactGroup);
                break;
        }

        // Apply all of the QuestionDataExtensions registered with this widget's associated
        // QuestionDef to the widget
        for (QuestionDataExtension extension : fep.getQuestion().getExtensions()) {
            questionWidget.applyExtension(extension);
        }
        return questionWidget;
    }

    private QuestionWidget buildBasicWidget(String appearance, FormEntryPrompt fep,
                                            Context context, boolean inCompactGroup) {
        switch (fep.getDataType()) {
            case Constants.DATATYPE_DATE_TIME:
                return new DateTimeWidget(context, fep);
            case Constants.DATATYPE_DATE:
                // Need to override CalendarUtil's month localizer
                CalendarUtils.setArrayDataSource(new AndroidArrayDataSource(context));
                if (appearance != null && appearance.toLowerCase().equals("ethiopian")) {
                    return new EthiopianDateWidget(context, fep);
                } else if (appearance != null && appearance.toLowerCase().equals("nepali")) {
                    return new NepaliDateWidget(context, fep);
                } else if(appearance != null && appearance.toLowerCase().contains("gregorian")){
                    return new DatePrototypeFactory().getWidget(context, fep, appearance.toLowerCase());
                } else {
                    return new DateWidget(context, fep);
                }
            case Constants.DATATYPE_TIME:
                return new TimeWidget(context, fep);
            case Constants.DATATYPE_LONG:
                return new IntegerWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET, 2, inCompactGroup);
            case Constants.DATATYPE_DECIMAL:
                return new DecimalWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET, inCompactGroup);
            case Constants.DATATYPE_INTEGER:
                return new IntegerWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET, 1, inCompactGroup);
            case Constants.DATATYPE_GEOPOINT:
                return new GeoPointWidget(context, fep, pendingCalloutInterface);
            case Constants.DATATYPE_BARCODE:
                return new BarcodeWidget(context, fep, pendingCalloutInterface, appearance, formDef);
            case Constants.DATATYPE_TEXT:
                if (appearance != null && (appearance.equalsIgnoreCase("numbers") || appearance.equalsIgnoreCase("numeric"))) {
                    return new StringNumberWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET, inCompactGroup);
                } else {
                    return new StringWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET, inCompactGroup);
                }
            default:
                return new StringWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET, inCompactGroup);
        }
    }

    private IntentWidget buildIntentWidget(String appearance, FormEntryPrompt fep, Context context) {
        String intentId = appearance.substring("intent:".length());
        IntentCallout ic = formDef.getExtension(AndroidXFormExtensions.class).getIntent(intentId, formDef);
        //Hm, so what do we do if no callout is found? Error? For now, fail fast
        if (ic == null) {
            throw new RuntimeException("No intent callout could be found for requested id " + intentId + "!");
        }
        //NOTE: No path specific stuff for now
        Intent i = ic.generate(new EvaluationContext(formDef.getEvaluationContext(),fep.getIndex().getReference()));
        return new IntentWidget(context, fep, i, ic, pendingCalloutInterface);
    }

    private static QuestionWidget buildSelectOne(String appearance, FormEntryPrompt fep, Context context) {
        if (appearance != null && appearance.contains("compact")) {
            return buildCompactSelectOne(appearance, fep, context);
        } else if (appearance != null && appearance.equals("minimal")) {
            return new SpinnerWidget(context, fep);
        } else if (appearance != null && appearance.contains("combobox")) {
            return buildComboboxSelectOne(appearance, fep, context);
        } else if (appearance != null && appearance.equals("quick")) {
            return new SelectOneAutoAdvanceWidget(context, fep);
        } else if (appearance != null && appearance.equals("list")) {
            return new ListWidget(context, fep, true);
        } else if (appearance != null && appearance.equals("list-nolabel")) {
            return new ListWidget(context, fep, false);
        } else if (appearance != null && appearance.equals("label")) {
            return new LabelWidget(context, fep);
        } else {
            return new SelectOneWidget(context, fep);
        }
    }

    private static QuestionWidget buildCompactSelectOne(String appearance,
                                                        FormEntryPrompt fep,
                                                        Context context) {
        int numColumns = -1;
        try {
            numColumns =
                    Integer.parseInt(appearance.substring(appearance.indexOf('-') + 1));
        } catch (Exception e) {
            // Do nothing, leave numColumns as -1
            Log.e("WidgetFactory", "Exception parsing numColumns");
        }

        if (appearance.contains("quick")) {
            return new GridWidget(context, fep, numColumns, true);
        } else {
            return new GridWidget(context, fep, numColumns, false);
        }
    }

    private static QuestionWidget buildComboboxSelectOne(String appearance,
                                                         FormEntryPrompt fep,
                                                         Context context) {
        ComboboxFilterRule filterRule;
        if (appearance.contains("multiword")) {
            filterRule = new MultiWordFilterRule();
        } else if (appearance.contains("fuzzy")) {
            filterRule = new FuzzyMatchFilterRule();
        } else {
            filterRule = new StandardFilterRule();
        }
        return new ComboboxWidget(context, fep, filterRule);
    }

    private static QuestionWidget buildSelectMulti(String appearance, FormEntryPrompt fep, Context context) {
        if (appearance != null && appearance.contains("compact")) {
            int numColumns = -1;
            try {
                numColumns =
                        Integer.parseInt(appearance.substring(appearance.indexOf('-') + 1));
            } catch (Exception e) {
                // Do nothing, leave numColumns as -1
                Log.e("WidgetFactory", "Exception parsing numColumns");
            }

            return new GridMultiWidget(context, fep, numColumns);
        } else if (appearance != null && appearance.equals("minimal")) {
            return new SpinnerMultiWidget(context, fep);
        } else if (appearance != null && appearance.equals("list")) {
            return new ListMultiWidget(context, fep, true);
        } else if (appearance != null && appearance.equals("list-nolabel")) {
            return new ListMultiWidget(context, fep, false);
        } else if (appearance != null && appearance.equals("label")) {
            return new LabelWidget(context, fep);
        } else {
            return new SelectMultiWidget(context, fep);
        }
    }
}
