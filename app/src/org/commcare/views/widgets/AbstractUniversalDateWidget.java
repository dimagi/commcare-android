package org.commcare.views.widgets;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xform.util.UniversalDate;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.javarosa.xform.util.UniversalDate.MILLIS_IN_DAY;


/**
 * Universal Date Widget, extended to work with any calendar system.
 *
 * @author Alex Little (alex@alexlittle.net), Richard Lu
 */
public abstract class AbstractUniversalDateWidget extends QuestionWidget {

    protected long millisOfDayOffset;

    private TextView txtMonth;
    private TextView txtDay;
    private TextView txtYear;
    private TextView txtGregorian;

    protected final String[] monthsArray;
    protected int monthArrayPointer;

    private final Button btnDayUp;
    private final Button btnMonthUp;
    private final Button btnYearUp;
    private final Button btnDayDown;
    private final Button btnMonthDown;
    private final Button btnYearDown;

    private ScheduledExecutorService mUpdater;
    private final Handler mDayHandler;
    private final Handler mMonthHandler;
    private final Handler mYearHandler;
    private static final int MSG_INC = 0;
    private static final int MSG_DEC = 1;

    // Alter this to make the button more/less sensitive to an initial long press 
    private static final int INITIAL_DELAY = 500;
    // Alter this to vary how rapidly the date increases/decreases on long press 
    private static final int PERIOD = 200;

    private class UpdateTask implements Runnable {
        private final boolean mInc;
        private final Handler mHandler;

        public UpdateTask(boolean inc, Handler h) {
            mInc = inc;
            mHandler = h;
        }

        public void run() {
            if (mInc) {
                mHandler.sendEmptyMessage(MSG_INC);
            } else {
                mHandler.sendEmptyMessage(MSG_DEC);
            }
        }
    }

    public AbstractUniversalDateWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        monthsArray = getMonthsArray();

        inflateView(context);

        /*
         * Initialise handlers for incrementing/decrementing dates
         */
        mDayHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INC:
                        incrementDay();
                        return;
                    case MSG_DEC:
                        decrementDay();
                        return;
                }
                super.handleMessage(msg);
            }
        };

        mMonthHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INC:
                        incrementMonth();
                        return;
                    case MSG_DEC:
                        decrementMonth();
                        return;
                }
                super.handleMessage(msg);
            }
        };

        mYearHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INC:
                        incrementYear();
                        return;
                    case MSG_DEC:
                        decrementYear();
                        return;
                }
                super.handleMessage(msg);
            }
        };

        initText();


        // action buttons
        btnDayUp = (Button)findViewById(R.id.dayupbtn);
        btnMonthUp = (Button)findViewById(R.id.monthupbtn);
        btnYearUp = (Button)findViewById(R.id.yearupbtn);
        btnDayDown = (Button)findViewById(R.id.daydownbtn);
        btnMonthDown = (Button)findViewById(R.id.monthdownbtn);
        btnYearDown = (Button)findViewById(R.id.yeardownbtn);

        // button click listeners
        btnDayUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUpdater == null) {
                    incrementDay();
                    setFocus(getContext());
                }
            }
        });

        btnMonthUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUpdater == null) {
                    incrementMonth();
                    setFocus(getContext());
                }
            }
        });

        btnYearUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUpdater == null) {
                    setFocus(getContext());
                    incrementYear();
                }
            }
        });

        btnDayDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUpdater == null) {
                    decrementDay();
                    setFocus(getContext());
                }
            }
        });

        btnMonthDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUpdater == null) {
                    decrementMonth();
                    setFocus(getContext());
                }
            }
        });

        btnYearDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mUpdater == null) {
                    decrementYear();
                    setFocus(getContext());
                }
            }
        });

        setupTouchListeners();
        setupKeyListeners();

        // If there's an answer, use it.
        setAnswer();
    }

    protected void setupKeyListeners() {
        // button key listeners
        btnDayUp.setOnKeyListener(new EDWKeyListener(btnDayUp, mDayHandler));
        btnDayDown.setOnKeyListener(new EDWKeyListener(btnDayUp, mDayHandler));
        btnMonthUp.setOnKeyListener(new EDWKeyListener(btnMonthUp, mMonthHandler));
        btnMonthDown.setOnKeyListener(new EDWKeyListener(btnMonthUp, mMonthHandler));
        btnYearUp.setOnKeyListener(new EDWKeyListener(btnYearUp, mYearHandler));
        btnYearDown.setOnKeyListener(new EDWKeyListener(btnYearUp, mYearHandler));
    }

    protected void setupTouchListeners() {
        // button touch listeners
        btnDayUp.setOnTouchListener(new EDWTouchListener(btnDayUp, mDayHandler));
        btnDayDown.setOnTouchListener(new EDWTouchListener(btnDayUp, mDayHandler));
        btnMonthUp.setOnTouchListener(new EDWTouchListener(btnMonthUp, mMonthHandler));
        btnMonthDown.setOnTouchListener(new EDWTouchListener(btnMonthUp, mMonthHandler));
        btnYearUp.setOnTouchListener(new EDWTouchListener(btnYearUp, mYearHandler));
        btnYearDown.setOnTouchListener(new EDWTouchListener(btnYearUp, mYearHandler));
    }

    protected void initText() {
        // Date fields
        txtDay = (TextView)findViewById(R.id.daytxt);
        txtMonth = (TextView)findViewById(R.id.monthtxt);
        txtYear = (TextView)findViewById(R.id.yeartxt);
        txtGregorian = (TextView)findViewById(R.id.dateGregorian);
    }

    protected void inflateView(Context context) {
        LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View vv = vi.inflate(R.layout.universal_date_widget, null);
        addView(vv);
    }

    /**
     * Decrement 1 month in custom calendar system from the given millisecond instant.
     *
     * @param millisFromJavaEpoch Instant to decrement month from
     * @return UniversalDate representing the minus-1-month instant
     */
    protected abstract UniversalDate decrementMonth(long millisFromJavaEpoch);

    /**
     * Decrement 1 year in custom calendar system from the given millisecond instant.
     *
     * @param millisFromJavaEpoch Instant to decrement year from
     * @return UniversalDate representing the minus-1-year instant
     */
    protected abstract UniversalDate decrementYear(long millisFromJavaEpoch);

    /**
     * Get date in custom calendar system from the given millisecond instant.
     *
     * @param millisFromJavaEpoch Instant to get date with
     * @return UniversalDate representing the given instant
     */
    protected abstract UniversalDate fromMillis(long millisFromJavaEpoch);

    /**
     * Fetch the array of Strings representing month names in custom calendar system.
     *
     * @return Array of strings, length must match number of months in calendar system.
     */
    protected abstract String[] getMonthsArray();

    /**
     * Increment 1 month in custom calendar system from the given millisecond instant.
     *
     * @param millisFromJavaEpoch Instant to increment month from
     * @return UniversalDate representing the plus-1-month instant
     */
    protected abstract UniversalDate incrementMonth(long millisFromJavaEpoch);

    /**
     * Increment 1 year in custom calendar system from the given millisecond instant.
     *
     * @param millisFromJavaEpoch Instant to increment year from
     * @return UniversalDate representing the plus-1-year instant
     */
    protected abstract UniversalDate incrementYear(long millisFromJavaEpoch);

    /**
     * Translate the given date in the custom calendar system to
     * standard milliseconds from Java epoch.
     *
     * @return Milliseconds since Java epoch
     */
    protected abstract long toMillisFromJavaEpoch(int year, int month, int day, long millisOffset);

    /**
     * Resets date to today
     */
    @Override
    public void clearAnswer() {
        Date date = new Date();
        millisOfDayOffset = date.getTime() % MILLIS_IN_DAY;
        updateDateDisplay(date.getTime());
        updateGregorianDateHelperDisplay();
    }

    /**
     * @return the date for storing in ODK
     */
    @Override
    public IAnswerData getAnswer() {
        Date date = getDateAsGregorian();
        return new DateData(date);
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        //super.setOnLongClickListener(l);
    }

    /**
     * Start Updater, for when using long press to increment/decrement date without repeated pressing on the buttons
     */
    private void startUpdating(boolean inc, Handler mHandler) {
        if (mUpdater != null) {
            Log.e(getClass().getSimpleName(), "Another executor is still active");
            return;
        }
        mUpdater = Executors.newSingleThreadScheduledExecutor();
        mUpdater.scheduleAtFixedRate(new UpdateTask(inc, mHandler), INITIAL_DELAY, PERIOD,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Stop incrementing/decrementing
     */
    private void stopUpdating() {
        mUpdater.shutdownNow();
        mUpdater = null;
    }

    /**
     * Increase by 1 day
     */
    private void incrementDay() {
        // get the current date into gregorian, add one and redisplay
        updateDateDisplay(getDateAsGregorian().getTime() + MILLIS_IN_DAY);
        updateGregorianDateHelperDisplay();
    }

    /**
     * Increase by 1 month
     */
    private void incrementMonth() {
        UniversalDate dt = incrementMonth(getCurrentMillis());
        updateDateDisplay(dt.millisFromJavaEpoch);
        updateGregorianDateHelperDisplay();
    }

    /**
     * Increase by 1 year
     */
    private void incrementYear() {
        UniversalDate dt = incrementYear(getCurrentMillis());
        updateDateDisplay(dt.millisFromJavaEpoch);
        updateGregorianDateHelperDisplay();
    }

    /**
     * Decrease by 1 day
     */
    private void decrementDay() {
        updateDateDisplay(getDateAsGregorian().getTime() - MILLIS_IN_DAY);
        updateGregorianDateHelperDisplay();
    }

    /**
     * Decrease by 1 month
     */
    private void decrementMonth() {
        UniversalDate dt = decrementMonth(getCurrentMillis());
        updateDateDisplay(dt.millisFromJavaEpoch);
        updateGregorianDateHelperDisplay();
    }

    /**
     * Decrease by 1 year
     */
    private void decrementYear() {
        UniversalDate dt = decrementYear(getCurrentMillis());
        updateDateDisplay(dt.millisFromJavaEpoch);
        updateGregorianDateHelperDisplay();
    }

    /**
     * Initial date display
     */
    protected void setAnswer() {
        if (mPrompt.getAnswerValue() != null) {
            Date date = (Date)mPrompt.getAnswerValue().getValue();

            updateDateDisplay(date.getTime());
            updateGregorianDateHelperDisplay();
        } else {
            // create date widget with current date
            clearAnswer();
        }
    }

    /**
     * Get the current widget date in Gregorian chronology
     */
    protected Date getDateAsGregorian() {
        return new Date(getCurrentMillis());
    }

    /**
     * Get the current widget date in milliseconds since Java epoch
     */
    protected long getCurrentMillis() {
        int day = Integer.parseInt(txtDay.getText().toString());
        int month = monthArrayPointer + 1;
        int year = Integer.parseInt(txtYear.getText().toString());
        return toMillisFromJavaEpoch(year, month, day, millisOfDayOffset);
    }

    /**
     * Update the widget date to display the amended date
     */
    protected void updateDateDisplay(long millisFromJavaEpoch) {
        UniversalDate dateUniv = fromMillis(millisFromJavaEpoch);
        txtDay.setText(String.format("%02d", dateUniv.day));
        txtMonth.setText(monthsArray[dateUniv.month - 1]);
        monthArrayPointer = dateUniv.month - 1;
        txtYear.setText(String.format("%04d", dateUniv.year));
    }

    /**
     * Update the widget helper date text (useful for those who don't know the other calendar)
     */
    protected void updateGregorianDateHelperDisplay() {
        DateTime dtLMDGreg = new DateTime(getCurrentMillis());
        DateTimeFormatter fmt = DateTimeFormat.forPattern("d MMMM yyyy");
        String str = fmt.print(dtLMDGreg);
        txtGregorian.setText("(" + str + ")");
    }

    /**
     * Listens for button being pressed by touchscreen
     *
     * @author alex
     */
    private class EDWTouchListener implements OnTouchListener {
        private final View mView;
        private final Handler mHandler;

        public EDWTouchListener(View mV, Handler mH) {
            mView = mV;
            mHandler = mH;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            boolean isReleased = event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL;
            boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;

            if (isReleased) {
                stopUpdating();
            } else if (isPressed) {
                startUpdating(v == mView, mHandler);
            }
            return false;
        }
    }

    /**
     * Listens for button being pressed by keypad/trackball
     *
     * @author alex
     */
    private class EDWKeyListener implements OnKeyListener {
        private final View mView;
        private final Handler mHandler;

        public EDWKeyListener(View mV, Handler mH) {
            mView = mV;
            mHandler = mH;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            boolean isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
            boolean isReleased = event.getAction() == KeyEvent.ACTION_UP;
            boolean isPressed = event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getAction() != KeyEvent.ACTION_MULTIPLE;

            if (isKeyOfInterest && isReleased) {
                stopUpdating();
            } else if (isKeyOfInterest && isPressed) {
                startUpdating(v == mView, mHandler);
            }
            return false;
        }
    }

}
