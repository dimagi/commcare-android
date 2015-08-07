package org.odk.collect.android.logic;

import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.GroupDef;
import org.javarosa.core.model.IFormElement;
import org.javarosa.core.model.SubmissionProfile;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.model.xform.XFormSerializingVisitor;
import org.javarosa.model.xform.XPathReference;
import org.odk.collect.android.views.ODKView;
import org.odk.collect.android.widgets.WidgetFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

/**
 * This class is a wrapper for Javarosa's FormEntryController. In theory, if you wanted to replace
 * javarosa as the form engine, you should only need to replace the methods in this file. Also, we
 * haven't wrapped every method provided by FormEntryController, only the ones we've needed so far.
 * Feel free to add more as necessary.
 * 
 * @author carlhartung
 */
public class FormController {

    private static final String t = "FormController";
    private FormEntryController mFormEntryController;
    
    private boolean mReadOnly;

    public static final boolean STEP_OVER_GROUP = true;
    public static final boolean STEP_INTO_GROUP = false;

    /**
     * OpenRosa metadata tag names.
     */
    private static final String INSTANCE_ID = "instanceID";

    /**
     * OpenRosa metadata of a form instance.
     * 
     * Contains the values for the required metadata
     * fields and nothing else.
     * 
     * @author mitchellsundt@gmail.com
     *
     */
    public static final class InstanceMetadata {
        public final String instanceId;
        
        InstanceMetadata( String instanceId ) {
            this.instanceId = instanceId;
        }
    }


    public FormController(FormEntryController fec) {
        this(fec, false);
    }
    
    public FormController(FormEntryController fec, boolean readOnly) {
        mFormEntryController = fec;
        mReadOnly = readOnly;
    }
    


    /**
     * returns the event for the current FormIndex.
     * 
     * @return
     */
    public int getEvent() {
        return mFormEntryController.getModel().getEvent();
    }


    /**
     * returns the event for the given FormIndex.
     * 
     * @param index
     * @return
     */
    public int getEvent(FormIndex index) {
        return mFormEntryController.getModel().getEvent(index);
    }


    /**
     * @return true if current FormIndex is readonly. false otherwise.
     */
    public boolean isIndexReadonly() {
        return mFormEntryController.getModel().isIndexReadonly();
    }
    
    /**
     * @return true if this form session is in read only mode
     */
    public boolean isFormReadOnly() {
        return mReadOnly;
    }


    /**
     * @return current FormIndex.
     */
    public FormIndex getFormIndex() {
        return mFormEntryController.getModel().getFormIndex();
    }


    /**
     * Return the langauges supported by the currently loaded form.
     * 
     * @return Array of Strings containing the languages embedded in the XForm.
     */
    public String[] getLanguages() {
        return mFormEntryController.getModel().getLanguages();
    }


    /**
     * @return A String containing the title of the current form.
     */
    public String getFormTitle() {
        return mFormEntryController.getModel().getFormTitle();
    }


    /**
     * @return the currently selected language.
     */
    public String getLanguage() {
        return mFormEntryController.getModel().getLanguage();
    }


    /**
     * @return an array of FormEntryCaptions for the current FormIndex. This is how we get group
     *         information Group 1 > Group 2> etc... The element at [size-1] is the current question
     *         text, with group names decreasing in hierarchy until array element at [0] is the root
     */
    public FormEntryCaption[] getCaptionHierarchy() {
        return mFormEntryController.getModel().getCaptionHierarchy();
    }


    /**
     * Returns a caption prompt for the given index. This is used to create a multi-question per
     * screen view.
     * 
     * @param index
     * @return
     */
    public FormEntryCaption getCaptionPrompt(FormIndex index) {
        return mFormEntryController.getModel().getCaptionPrompt(index);
    }


    /**
     * Return the caption for the current FormIndex. This is usually used for a repeat prompt.
     * 
     * @return
     */
    public FormEntryCaption getCaptionPrompt() {
        return mFormEntryController.getModel().getCaptionPrompt();
    }


    /**
     * TODO: We need a good description of what this does, exactly, and why.
     * 
     * @return
     */
    public boolean postProcessInstance() {
        return mFormEntryController.getModel().getForm().postProcessInstance();
    }


    /**
     * TODO: We need a good description of what this does, exactly, and why.
     * 
     * @return
     */
    public FormInstance getInstance() {
        return mFormEntryController.getModel().getForm().getInstance();
    }


    /**
     * A convenience method for determining if the current FormIndex is a group that is/should be
     * displayed as a multi-question view of all of its descendants. This is useful for returning 
     * from the formhierarchy view to a selected index.
     * 
     * @param index
     * @return
     */
    private boolean isFieldListHost(FormIndex index) {
        // if this isn't a group, return right away
        if (!(mFormEntryController.getModel().getForm().getChild(index) instanceof GroupDef)) {
            return false;
        }
        
        //TODO: Is it possible we need to make sure this group isn't inside of another group which 
        //is itself a field list? That would make the top group the field list host, not the 
        //descendant group

        GroupDef gd = (GroupDef) mFormEntryController.getModel().getForm().getChild(index); // exceptions?
        return (ODKView.FIELD_LIST.equalsIgnoreCase(gd.getAppearanceAttr()));
    }


    /**
     * Tests if the FormIndex 'index' is located inside a group that is marked as a "field-list"
     * 
     * @param index
     * @return true if index is in a "field-list". False otherwise.
     */
    public boolean indexIsInFieldList(FormIndex index) {
    	FormIndex fieldListHost = this.getFieldListHost(index);
    	return fieldListHost != null;
    }


    /**
     * Tests if the current FormIndex is located inside a group that is marked as a "field-list"
     * 
     * @return true if index is in a "field-list". False otherwise.
     */
    public boolean indexIsInFieldList() {
        return indexIsInFieldList(mFormEntryController.getModel().getFormIndex());
    }


    /**
     * Attempts to save answer at the current FormIndex into the data model.
     * 
     * @param data
     * @return
     */
    public int answerQuestion(IAnswerData data) {
        return mFormEntryController.answerQuestion(data);
    }


    /**
     * Attempts to save answer into the given FormIndex into the data model.
     * 
     * @param index
     * @param data
     * @return
     */
    public int answerQuestion(FormIndex index, IAnswerData data) {
        return mFormEntryController.answerQuestion(index, data);
    }


    /**
     * saveAnswer attempts to save the current answer into the data model without doing any
     * constraint checking. Only use this if you know what you're doing. For normal form filling you
     * should always use answerQuestion or answerCurrentQuestion.
     * 
     * @param index
     * @param data
     * @return true if saved successfully, false otherwise.
     */
    public boolean saveAnswer(FormIndex index, IAnswerData data) {
        return mFormEntryController.saveAnswer(index, data);
    }

    /**
     * saveAnswer attempts to save the current answer into the data model without doing any
     * constraint checking. Only use this if you know what you're doing. For normal form filling you
     * should always use answerQuestion().
     * 
     * @return true if saved successfully, false otherwise.
     */
    public boolean saveAnswer(IAnswerData data) {
        return mFormEntryController.saveAnswer(data);
    }

    /**
     * Navigates forward in the form, expanding any repeats encountered.
     * 
     * @return the next event that should be handled by a view.
     */
    public int stepToNextEvent(boolean stepOverGroup) {
       return stepToNextEvent(stepOverGroup, true);
    }
    
    
    /**
     * Get the FormIndex after the given one.
     * @param index
     * @param stepOverGroup
     * @return FormIndex
     */
    public FormIndex getNextFormIndex(FormIndex index, boolean stepOverGroup) {
        return getNextFormIndex(index, stepOverGroup, true);
    }
    
    /**
     * Get the FormIndex after the given one.
     * @param index
     * @param stepOverGroup
     * @param expandRepeats
     * @return FormIndex
     */
    public FormIndex getNextFormIndex(FormIndex index, boolean stepOverGroup, boolean expandRepeats) {
        //TODO: this won't actually catch the case where there are nested field lists properly
        if (mFormEntryController.getModel().getEvent(index) == FormEntryController.EVENT_GROUP && indexIsInFieldList(index) && stepOverGroup) {
            return getIndexPastGroup(index);
        } else {
            index = mFormEntryController.getNextIndex(index, expandRepeats);
            if(mFormEntryController.getModel().getEvent(index) == FormEntryController.EVENT_PROMPT_NEW_REPEAT && this.mReadOnly) {
                return getNextFormIndex(index, stepOverGroup, expandRepeats);
            }
            return index;
        }        
    }

    /**
     * Navigates forward in the form.
     * 
     * @return the next event that should be handled by a view.
     */
    public int stepToNextEvent(boolean stepOverGroup, boolean expandRepeats) {
        FormIndex nextIndex = getNextFormIndex(mFormEntryController.getModel().getFormIndex(), stepOverGroup, expandRepeats);
        return jumpToIndex(nextIndex);
    }


    /**
     * From the given FormIndex which must be a group element, 
     * find the next index which is outside of that group.
     * @return FormIndex
     */
    private FormIndex getIndexPastGroup(FormIndex index) {
        // Walk until the next index is outside of this one.
        FormIndex walker = index;
        while(FormIndex.isSubElement(index, walker)) {
            walker = getNextFormIndex(walker, false);
        }
        return walker;
    }


    /**
     * Navigates backward in the form.
     * 
     * @return the event that should be handled by a view.
     */
    public int stepToPreviousEvent() {
        /*
         * Right now this will always skip to the beginning of a group if that group is represented
         * as a 'field-list'. Should a need ever arise to step backwards by only one step in a
         * 'field-list', this method will have to be updated.
         */

        int event = mFormEntryController.stepToPreviousEvent();
        
        if(event == FormEntryController.EVENT_PROMPT_NEW_REPEAT &&
                this.mReadOnly) {
            return stepToPreviousEvent();
        }


        // If after we've stepped, we're in a field-list, jump back to the beginning of the group
        FormIndex host = getFieldListHost(this.getFormIndex());
        if (host != null) {
            return mFormEntryController.jumpToIndex(host);
        }

        return mFormEntryController.getModel().getEvent();

    }
    
    /**
     * Retrieves the index of the Group that is the host of a given field list. 
     * 
     * @param child
     * @return
     */
    private FormIndex getFieldListHost(FormIndex child) {
        int event = mFormEntryController.getModel().getEvent(child);
        
        if (event == FormEntryController.EVENT_QUESTION || event == FormEntryController.EVENT_GROUP || event == FormEntryController.EVENT_REPEAT) {
            // caption[0..len-1]
            // caption[len-1] == the event itself
            // caption[len-2] == the groups containing this group
            FormEntryCaption[] captions = mFormEntryController.getModel().getCaptionHierarchy();
            
            //This starts at the beginning of the heirarchy, so it'll catch the top-level 
            //host index.
            for(FormEntryCaption caption : captions ) {
            	FormIndex parentIndex = caption.getIndex();
            	if(isFieldListHost(parentIndex)) {
            		return parentIndex;
            	}
            }
            
            //none of this node's parents are field lists
            return null;
            
        } else {
            // Non-host elements can't have field list hosts.
            return null;
        }
    }


    /**
     * Jumps to a given FormIndex.
     * 
     * @param index
     * @return EVENT for the specified Index.
     */
    public int jumpToIndex(FormIndex index) {
        return mFormEntryController.jumpToIndex(index);
    }


    /**
     * Creates a new repeated instance of the group referenced by the specified FormIndex.
     * 
     * @param questionIndex
     */
    public void newRepeat(FormIndex questionIndex) {
        mFormEntryController.newRepeat(questionIndex);
    }


    /**
     * Creates a new repeated instance of the group referenced by the current FormIndex.
     */
    public void newRepeat() {
        mFormEntryController.newRepeat();
    }


    /**
     * If the current FormIndex is within a repeated group, will find the innermost repeat, delete
     * it, and jump the FormEntryController to the previous valid index. That is, if you have group1
     * (2) > group2 (3) and you call deleteRepeat, it will delete the 3rd instance of group2.
     */
    public void deleteRepeat() {
        FormIndex fi = mFormEntryController.deleteRepeat();
        mFormEntryController.jumpToIndex(fi);
    }


    /**
     * Sets the current language.
     * 
     * @param language
     */
    public void setLanguage(String language) {
        mFormEntryController.setLanguage(language);
    }

    /**
     * Expand any unexpanded repeats at the given FormIndex
     * @param index
     */
    public void expandRepeats(FormIndex index) {
        mFormEntryController.expandRepeats(index);
    }

    /**
     * getQuestionPrompts for the current index 
     * @return Array of FormEntryPrompt objects
     * @throws RuntimeException
     */
    public FormEntryPrompt[] getQuestionPrompts() throws RuntimeException {
        return getQuestionPrompts(mFormEntryController.getModel().getFormIndex());
    }
    
    /**
     * Returns an array of relevant question prompts that should be displayed as a single screen.
     * If the given form index is a question, it is returned. Otherwise if the 
     * given index is a field list (and _only_ when it is a field list)
     * 
     * @return
     */
    public FormEntryPrompt[] getQuestionPrompts(FormIndex currentIndex) throws RuntimeException {
        
        IFormElement element = mFormEntryController.getModel().getForm().getChild(currentIndex);

        //If we're in a group, we will collect of the questions in this group
        if (element instanceof GroupDef) {
        	//Assert that this is a valid condition (only field lists return prompts)
        	if(!this.isFieldListHost(currentIndex)) { throw new RuntimeException("Cannot get question prompts from a non-field-list group"); }

            // Questions to collect
            ArrayList<FormEntryPrompt> questionList = new ArrayList<FormEntryPrompt>();
                        
            //Step over all events in this field list and collect them
            FormIndex walker = currentIndex;
            
            int event = this.getEvent(currentIndex);
            while(FormIndex.isSubElement(currentIndex, walker)) {
            	if(event == FormEntryController.EVENT_QUESTION) {
                    questionList.add(mFormEntryController.getModel().getQuestionPrompt(walker));
            	}
            	
            	if(event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
            		//TODO: What if there is a non-deterministic repeat up in the field list?
            	}
            	
            	//this handles relevance for us
                walker = this.mFormEntryController.getNextIndex(walker);
                event = this.getEvent(walker);
            }
            
            FormEntryPrompt[] questions = new FormEntryPrompt[questionList.size()];
            //Populate the array with the collected questions
            questionList.toArray(questions);
            return questions;
        } else {
            // We have a question, so just get the one prompt
            return new FormEntryPrompt[] { mFormEntryController.getModel().getQuestionPrompt(currentIndex)};
        }   
    }

    
    public FormEntryPrompt getQuestionPrompt(FormIndex index) {
        return mFormEntryController.getModel().getQuestionPrompt(index);
    }


    public FormEntryPrompt getQuestionPrompt() {
        return mFormEntryController.getModel().getQuestionPrompt();
    }


    /**
     * Returns an array of FormEntryCaptions for current FormIndex.
     * 
     * @return
     */
    public FormEntryCaption[] getGroupsForCurrentIndex() {
        // return an empty array if you ask for something impossible
        if (!(mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_QUESTION
                || mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_PROMPT_NEW_REPEAT || mFormEntryController
                .getModel().getEvent() == FormEntryController.EVENT_GROUP)) {
            return new FormEntryCaption[0];
        }

        // the first caption is the question, so we skip it if it's an EVENT_QUESTION
        // otherwise, the first caption is a group so we start at index 0
        int lastquestion = 1;
        if (mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_PROMPT_NEW_REPEAT
                || mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_GROUP) {
            lastquestion = 0;
        }

        FormEntryCaption[] v = mFormEntryController.getModel().getCaptionHierarchy();
        FormEntryCaption[] groups = new FormEntryCaption[v.length - lastquestion];
        System.arraycopy(v, 0, groups, 0, v.length - lastquestion);
        return groups;
    }


    /**
     * This is used to enable/disable the "Delete Repeat" menu option.
     * 
     * @return
     */
    public boolean indexContainsRepeatableGroup() {
        FormEntryCaption[] groups = mFormEntryController.getModel().getCaptionHierarchy();
        if (groups.length == 0) {
            return false;
        }
        for (int i = 0; i < groups.length; i++) {
            if (groups[i].repeats())
                return true;
        }
        return false;
    }


    /**
     * The count of the closest group that repeats or -1.
     */
    public int getLastRepeatedGroupRepeatCount() {
        FormEntryCaption[] groups = mFormEntryController.getModel().getCaptionHierarchy();
        if (groups.length > 0) {
            for (int i = groups.length - 1; i > -1; i--) {
                if (groups[i].repeats()) {
                    return groups[i].getMultiplicity();

                }
            }
        }
        return -1;
    }


    /**
     * The name of the closest group that repeats or null.
     */
    public String getLastRepeatedGroupName() {
        FormEntryCaption[] groups = mFormEntryController.getModel().getCaptionHierarchy();
        // no change
        if (groups.length > 0) {
            for (int i = groups.length - 1; i > -1; i--) {
                if (groups[i].repeats()) {
                    return groups[i].getLongText();
                }
            }
        }
        return null;
    }


    /**
     * The closest group the prompt belongs to.
     * 
     * @return FormEntryCaption
     */
    private FormEntryCaption getLastGroup() {
        FormEntryCaption[] groups = mFormEntryController.getModel().getCaptionHierarchy();
        if (groups == null || groups.length == 0)
            return null;
        else
            return groups[groups.length - 1];
    }


    /**
     * The repeat count of closest group the prompt belongs to.
     */
    public int getLastRepeatCount() {
        if (getLastGroup() != null) {
            return getLastGroup().getMultiplicity();
        }
        return -1;

    }


    /**
     * The text of closest group the prompt belongs to.
     */
    public String getLastGroupText() {
        if (getLastGroup() != null) {
            return getLastGroup().getLongText();
        }
        return null;
    }
    
    /**
     * Find the portion of the form that is to be submitted
     * 
     * @return
     */
    private XPathReference getSubmissionDataReference() {
        FormDef formDef = mFormEntryController.getModel().getForm();
        // Determine the information about the submission...
        SubmissionProfile p = formDef.getSubmissionProfile();
        if (p == null || p.getRef() == null) {
            return new XPathReference("/");
        } else {
            return p.getRef();
        }
    }
    
    /**
     * Once a submission is marked as complete, it is saved in the 
     * submission format, which might be a fragment of the original
     * form or might be a SMS text string, etc.
     * 
     * @return true if the submission is the entire form.  If it is, 
     *              then the submission can be re-opened for editing
     *              after it was marked-as-complete (provided it has
     *              not been encrypted).
     */
    public boolean isSubmissionEntireForm() {
        XPathReference sub = getSubmissionDataReference();
        return ( getInstance().resolveReference(sub) == null );
    }
    
    /**
     * Extract the portion of the form that should be uploaded to the server.
     * 
     * @return
     * @throws IOException
     */
    public ByteArrayPayload getSubmissionXml() throws IOException {
        FormInstance instance = getInstance();
        XFormSerializingVisitor serializer = new XFormSerializingVisitor();
        ByteArrayPayload payload = 
                (ByteArrayPayload) serializer.createSerializedPayload(instance,
                                                   getSubmissionDataReference());
        return payload;
    }
    
    /**
     * Traverse the submission looking for the first matching tag in depth-first order.
     * 
     * @param parent
     * @param name
     * @return
     */
    private TreeElement findDepthFirst(TreeElement parent, String name) {
        int len = parent.getNumChildren();
        for ( int i = 0; i < len ; ++i ) {
            TreeElement e = parent.getChildAt(i);
            if ( name.equals(e.getName()) ) {
                return e;
            } else if ( e.getNumChildren() != 0 ) {
                TreeElement v = findDepthFirst(e, name);
                if ( v != null ) return v;
            }
        }
        return null;
    }
    
    /**
     * Get the OpenRosa required metadata of the portion of the form beng submitted
     * @return
     */
    public InstanceMetadata getSubmissionMetadata() {
        FormDef formDef = mFormEntryController.getModel().getForm();
        TreeElement rootElement = formDef.getInstance().getRoot();

        TreeElement trueSubmissionElement;
        // Determine the information about the submission...
        SubmissionProfile p = formDef.getSubmissionProfile();
        if ( p == null || p.getRef() == null ) {
            trueSubmissionElement = rootElement;
        } else {
            XPathReference ref = p.getRef();
            trueSubmissionElement = formDef.getInstance().resolveReference(ref);
            // resolveReference returns null if the reference is to the root element...
            if ( trueSubmissionElement == null ) {
                trueSubmissionElement = rootElement;
            }
        }
        
        // and find the depth-first meta block in this...
        TreeElement e = findDepthFirst(trueSubmissionElement, "meta");
        
        String instanceId = null;
        
        if ( e != null ) {
            Vector<TreeElement> v;

            // instance id...
            v = e.getChildrenWithName(INSTANCE_ID);
            if ( v.size() == 1 ) {
                instanceId = v.get(0).getValue().uncast().toString();
            }
        }
        
        return new InstanceMetadata(instanceId);
    }


    //CTS: Added this to protect the JR internal classes, although it's not awesome that
    //this ended up in the "logic" division. 
    public WidgetFactory getWidgetFactory() {
        return new WidgetFactory(mFormEntryController.getModel().getForm());
    }

}
