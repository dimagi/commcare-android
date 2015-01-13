package org.odk.collect.android.widgets;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.Chronology;
import org.joda.time.chrono.EthiopicChronology;

import android.content.Context;
import android.content.res.Resources;

/**
 * Ethiopian Date Widget.
 * 
 * @author Alex Little (alex@alexlittle.net), Richard Lu
 */
public class EthiopianDateWidget extends AbstractUniversalDateWidget {
        
        public EthiopianDateWidget(Context context, FormEntryPrompt prompt) {
			super(context, prompt);
		}

		private static Chronology chron_eth = EthiopicChronology.getInstance();
		
		@Override
		protected Chronology getChronology() {
			return chron_eth;
		}
        
        @Override
        protected String[] getMonthsArray() {
            Resources res = getResources();
            // load the months - will automatically get correct strings for current phone locale
            return res.getStringArray(R.array.ethiopian_months);
        }
}
