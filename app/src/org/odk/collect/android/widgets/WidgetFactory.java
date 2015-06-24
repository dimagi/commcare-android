/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.widgets;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.jr.extensions.AndroidXFormExtensions;
import org.odk.collect.android.jr.extensions.IntentCallout;

/**
 * Convenience class that handles creation of widgets.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class WidgetFactory {
    
    FormDef form;
    
    public WidgetFactory(FormDef form) {
        this.form = form;
    }

    /**
     * Returns the appropriate QuestionWidget for the given FormEntryPrompt.
     * 
     * @param fep prompt element to be rendered
     * @param context Android context
     */
    public QuestionWidget createWidgetFromPrompt(FormEntryPrompt fep, Context context) {
        QuestionWidget questionWidget;
        String appearance = fep.getAppearanceHint();
        switch (fep.getControlType()) {
            case Constants.CONTROL_INPUT:
                if(appearance != null && appearance.startsWith("intent:")) {
                    String intentId = appearance.substring("intent:".length());
                    IntentCallout ic = form.getExtension(AndroidXFormExtensions.class).getIntent(intentId, form);
                    //Hm, so what do we do if no callout is found? Error? For now, fail fast
                    if(ic == null) {
                        throw new RuntimeException("No intent callout could be found for requested id " + intentId + "!");
                    }
                    //NOTE: No path specific stuff for now
                    Intent i = ic.generate(form.getEvaluationContext());
                    questionWidget = new IntentWidget(context, fep, i, ic);
                    break;
                }
            case Constants.CONTROL_SECRET:
                switch (fep.getDataType()) {
                    case Constants.DATATYPE_DATE_TIME:
                        questionWidget = new DateTimeWidget(context, fep);
                        break;
                    case Constants.DATATYPE_DATE:
                        if(appearance != null && appearance.toLowerCase().equals("ethiopian")){
                            questionWidget = new EthiopianDateWidget(context, fep);
                        } else if (appearance != null && appearance.toLowerCase().equals("nepali")) {
                        	questionWidget = new NepaliDateWidget(context, fep);
                        } else {
                            questionWidget = new DateWidget(context, fep);
                        }
                        break;
                    case Constants.DATATYPE_TIME:
                        questionWidget = new TimeWidget(context, fep);
                        break;
                    case Constants.DATATYPE_LONG:
                        questionWidget = new IntegerWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET, 2);
                        break;
                    case Constants.DATATYPE_DECIMAL:
                        questionWidget = new DecimalWidget(context, fep, fep.getControlType() == Constants.CONTROL_SECRET);
                        break;
                    case Constants.DATATYPE_INTEGER:
                        questionWidget = new IntegerWidget(context, fep,  fep.getControlType() == Constants.CONTROL_SECRET, 1);
                        break;
                    case Constants.DATATYPE_GEOPOINT:
                        questionWidget = new GeoPointWidget(context, fep);
                        break;
                    case Constants.DATATYPE_BARCODE:

                        IntentCallout mIntentCallout = new IntentCallout("com.google.zxing.client.android.SCAN", null, null,
                                null, null , null, StringUtils.getStringRobust(context, R.string.get_barcode), appearance);
                        Intent mIntent = mIntentCallout.generate(form.getEvaluationContext());
                        questionWidget = new BarcodeWidget(context, fep, mIntent, mIntentCallout);
                        break;
                    case Constants.DATATYPE_TEXT:
                        if (appearance != null && (appearance.equalsIgnoreCase("numbers") || appearance.equalsIgnoreCase("numeric"))) {
                            questionWidget = new StringNumberWidget(context, fep,  fep.getControlType() == Constants.CONTROL_SECRET);
                        } else {
                            questionWidget = new StringWidget(context, fep,  fep.getControlType() == Constants.CONTROL_SECRET);
                        }
                        break;
                    default:
                        questionWidget = new StringWidget(context, fep,  fep.getControlType() == Constants.CONTROL_SECRET);
                        break;
                }
                break;
            case Constants.CONTROL_IMAGE_CHOOSE:
                if (appearance != null && appearance.equals("signature")) {
                    questionWidget = new SignatureWidget(context, fep);
                } else {
                questionWidget = new ImageWidget(context, fep);
                }
                break;
            case Constants.CONTROL_AUDIO_CAPTURE:
                questionWidget = new AudioWidget(context, fep);
                break;
            case Constants.CONTROL_VIDEO_CAPTURE:
                questionWidget = new VideoWidget(context, fep);
                break;
            case Constants.CONTROL_SELECT_ONE:
                if (appearance != null && appearance.contains("compact")) {
                    int numColumns = -1;
                    try {
                        numColumns =
                            Integer.parseInt(appearance.substring(appearance.indexOf('-') + 1));
                    } catch (Exception e) {
                        // Do nothing, leave numColumns as -1
                        Log.e("WidgetFactory", "Exception parsing numColumns");
                    }

                    if (appearance.contains("quick")) {
                        questionWidget = new GridWidget(context, fep, numColumns, true);
                    } else {
                        questionWidget = new GridWidget(context, fep, numColumns, false);
                    }
                } else if (appearance != null && appearance.equals("minimal")) {
                    questionWidget = new SpinnerWidget(context, fep);
                }
                // else if (appearance != null && appearance.contains("autocomplete")) {
                // String filterType = null;
                // try {
                // filterType = appearance.substring(appearance.indexOf('-') + 1);
                // } catch (Exception e) {
                // // Do nothing, leave filerType null
                // Log.e("WidgetFactory", "Exception parsing filterType");
                // }
                // questionWidget = new AutoCompleteWidget(context, fep, filterType);
                //
                // }
                else if (appearance != null && appearance.equals("quick")) {
                    questionWidget = new SelectOneAutoAdvanceWidget(context, fep);
                } else if (appearance != null && appearance.equals("list")) {
                    questionWidget = new ListWidget(context, fep, true);
                } else if (appearance != null && appearance.equals("list-nolabel")) {
                    questionWidget = new ListWidget(context, fep, false);
                } else if (appearance != null && appearance.equals("label")) {
                    questionWidget = new LabelWidget(context, fep);
                } else {
                    questionWidget = new SelectOneWidget(context, fep);
                }
                break;
            case Constants.CONTROL_SELECT_MULTI:
                if (appearance != null && appearance.contains("compact")) {
                    int numColumns = -1;
                    try {
                        numColumns =
                            Integer.parseInt(appearance.substring(appearance.indexOf('-') + 1));
                    } catch (Exception e) {
                        // Do nothing, leave numColumns as -1
                        Log.e("WidgetFactory", "Exception parsing numColumns");
                    }

                    questionWidget = new GridMultiWidget(context, fep, numColumns);
                } else if (appearance != null && appearance.equals("minimal")) {
                    questionWidget = new SpinnerMultiWidget(context, fep);
                } else if (appearance != null && appearance.equals("list")) {
                    questionWidget = new ListMultiWidget(context, fep, true);
                } else if (appearance != null && appearance.equals("list-nolabel")) {
                    questionWidget = new ListMultiWidget(context, fep, false);
                } else if (appearance != null && appearance.equals("label")) {
                    questionWidget = new LabelWidget(context, fep);
                } else {
                    questionWidget = new SelectMultiWidget(context, fep);
                }
                break;
            case Constants.CONTROL_TRIGGER:
                questionWidget = new TriggerWidget(context, fep, appearance);
                break;
            default:
                questionWidget = new StringWidget(context, fep, false);
                break;
        }
        return questionWidget;
    }
}
