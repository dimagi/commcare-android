package org.odk.collect.android.widgets;

import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;
import org.joda.time.Chronology;
import org.odk.collect.android.jt.extensions.NepaliChronology;

import android.content.Context;
import android.content.res.Resources;

/**
 * Nepali Date Widget.
 * 
 * @author Richard Lu
 */
public class NepaliDateWidget extends AbstractUniversalDateWidget {
        
        public NepaliDateWidget(Context context, FormEntryPrompt prompt) {
			super(context, prompt);
		}

		private static Chronology chron_nep = NepaliChronology.getInstance();
		
		@Override
		protected Chronology getChronology() {
			return chron_nep;
		}
        
        @Override
        protected String[] getMonthsArray() {
            Resources res = getResources();
            // load the months - will automatically get correct strings for current phone locale
            return res.getStringArray(R.array.nepali_months);
        }
}
