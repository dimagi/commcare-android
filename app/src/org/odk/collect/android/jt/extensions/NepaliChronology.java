package org.odk.collect.android.jt.extensions;

import org.joda.time.Chronology;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationField;
import org.joda.time.ReadablePartial;
import org.joda.time.ReadablePeriod;

public class NepaliChronology extends Chronology {
	
	private static NepaliChronology instance;
	
	public static NepaliChronology getInstance() {
		if (instance == null) {
			instance = new NepaliChronology();
		}
		return instance;
	}
	
	private NepaliChronology() {}

	@Override
	public long add(ReadablePeriod arg0, long arg1, int arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long add(long arg0, long arg1, int arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public DurationField centuries() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField centuryOfEra() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField clockhourOfDay() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField clockhourOfHalfday() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField dayOfMonth() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField dayOfWeek() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField dayOfYear() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField days() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField era() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField eras() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] get(ReadablePartial arg0, long arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] get(ReadablePeriod arg0, long arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] get(ReadablePeriod arg0, long arg1, long arg2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getDateTimeMillis(int arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getDateTimeMillis(long arg0, int arg1, int arg2, int arg3,
			int arg4) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getDateTimeMillis(int arg0, int arg1, int arg2, int arg3,
			int arg4, int arg5, int arg6) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public DateTimeZone getZone() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField halfdayOfDay() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField halfdays() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField hourOfDay() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField hourOfHalfday() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField hours() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField millis() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField millisOfDay() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField millisOfSecond() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField minuteOfDay() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField minuteOfHour() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField minutes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField monthOfYear() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField months() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField secondOfDay() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField secondOfMinute() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField seconds() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long set(ReadablePartial arg0, long arg1) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void validate(ReadablePartial arg0, int[] arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public DateTimeField weekOfWeekyear() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField weeks() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField weekyear() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField weekyearOfCentury() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField weekyears() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Chronology withUTC() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Chronology withZone(DateTimeZone arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField year() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField yearOfCentury() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DateTimeField yearOfEra() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DurationField years() {
		// TODO Auto-generated method stub
		return null;
	}

}
