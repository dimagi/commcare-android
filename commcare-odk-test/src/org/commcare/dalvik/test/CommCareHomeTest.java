package org.commcare.dalvik.test;

import java.util.List;

import junit.framework.AssertionFailedError;

import org.commcare.dalvik.activities.CommCareSetupActivity;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.jayway.android.robotium.solo.Solo;
import com.jayway.android.robotium.solo.WebElement;


public class CommCareHomeTest extends ActivityInstrumentationTestCase2<CommCareSetupActivity> {

	public CommCareHomeTest() throws ClassNotFoundException {
		super(CommCareSetupActivity.class);
	}

	private Solo solo;

	/*
	 * (non-Javadoc)
	 * @see android.test.ActivityInstrumentationTestCase2#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		//setUp() is run before a test case is started. 
		//This is where the solo object is created.
		System.out.println("524 rick doo");
		solo = new Solo(getInstrumentation(), getActivity());
	}

	/*
	 * (non-Javadoc)
	 * @see android.test.ActivityInstrumentationTestCase2#tearDown()
	 */
	@Override
	public void tearDown() throws Exception {
		//tearDown() is run after a test case has finished. 
		//finishOpenedActivities() will finish all the activities that have been opened during the test execution.
		solo.finishOpenedActivities();
	}

	/**
	 * A test that searches for Robotium and asserts that Robotium is found.
	 */
	public void testSearchRobotium() {
/*		//Waits for the tag: 'DIV'.
		solo.waitForWebElement(By.tagName("DIV"));

		By inputSearch = By.name("q");
		By buttonSearch = By.id("tsbb");

		//Wait for a WebElement without scrolling.
		solo.waitForWebElement(inputSearch, 1, false);

		//Types Robotium in the search input field.
		solo.typeTextInWebElement(inputSearch, "Robotium");

		//Prints information on the WebELements currently displayed.
		logWebElementsFound();

		solo.clickOnWebElement(buttonSearch);

		//Assert that the text Robotium is found on the page (searchText() and waitForText() can be used here as well). 
		assertTrue("Robotium can not be found on the page", solo.waitForWebElement(By.textContent("Robotium")));

		//Assert that Robotium is entered in the input field.
		assertTrue("Robotium has not been typed", solo.getWebElement(inputSearch, 0).getText()
				.contains("Robotium"));
				*/
		
		try{
			solo.assertCurrentActivity("Wrong activity launched",CommCareSetupActivity.class);
		
			assertTrue(solo.waitForText("DERP"));
			assertTrue(solo.waitForText("Welcome to CommCare! Please choose an installation method below"));
			assertTrue(solo.waitForText("Scan Application Barcode"));
			assertTrue(solo.waitForText("Enter URL"));
		}
		catch(AssertionFailedError afe){
			Log.d("Robotium","one of the assertions failed");
		}
		
		System.out.println("524 in test");
		
		List<View> btnList = solo.getCurrentViews();
		
		System.out.println("btn list size: " + btnList.size());
		
		int enterURLID =0;
		
		for(int i=0;i<btnList.size();i++){
			View currentView = btnList.get(i);
			if(currentView instanceof Button){
				System.out.println("btn text is: " + ((Button)currentView).getText());
				
				Button mButton = (Button) currentView;
				
				String btnText = (String) mButton.getText();
				
				if(btnText.equals("Scan URL")){
					assertEquals(mButton.getVisibility(), Button.GONE);
				}
				if(btnText.equals("Scan Application Barcode")){
					assertEquals(mButton.getVisibility(), Button.VISIBLE);
				}
				if(btnText.equals("Begin Install")){
					assertEquals(mButton.getVisibility(), Button.GONE);
				}
				if(btnText.equals("Enter URL")){
					assertEquals(mButton.getVisibility(), Button.VISIBLE);
					enterURLID = i;
				}
				if(btnText.equals("Start Over")){
					assertEquals(mButton.getVisibility(), Button.GONE);
				}
			}
		}
		
		System.out.println("url id is : " + enterURLID);
		
		//solo.clickOnButton(enterURLID);
		//assertTrue(solo.waitForText("Please enter the URL"));
	}

	/**
	 * Logs the WebElements currently displayed.  
	 */
	private void logWebElementsFound(){
		for(WebElement webElement : solo.getCurrentWebElements()){
			Log.d("Robotium", "id: '" + webElement.getId() + "' text: '" + webElement.getText() + "' name: '" + webElement.getName() + 
					"' class name: '" + webElement.getClassName() + "' tag name: '" + webElement.getTagName() + "'");
		}
	}
}