/**
 * 
 */
package org.commcare.android.models;

import java.util.Enumeration;
import java.util.Hashtable;

import org.commcare.android.database.user.models.EntityStorageCache;
import org.commcare.android.util.StringUtils;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;



/**
 * An AsyncEntity is an entity reference which is capable of building its
 * values (evaluating all Text elements/background data elements) lazily
 * rather than upfront when the entity is constructed.
 * 
 * It is threadsafe.
 * 
 * It will attempt to Cache its values persistently by a derived entity key rather
 * than evaluating them each time when possible. This can be slow to perform across
 * all entities internally due to the overhead of establishing the db connection, it
 * is recommended that the entities be primed externally with a bulk query.
 * 
 * @author ctsims
 *
 */
public class AsyncEntity extends Entity<TreeReference>{
    
    boolean caching = true;
    
    DetailField[] fields;
    Object[] data;
    private String[] sortData;
    private String[][] sortDataPieces;
    EvaluationContext context;
    Hashtable<String, XPathExpression> mVariableDeclarations;
    boolean mVariableContextLoaded = false;
    String mCacheIndex;
    String mDetailId;

    private EntityStorageCache mEntityStorageCache;
    
    private Object mAsyncLock = new Object();
    
    public AsyncEntity(DetailField[] fields, EvaluationContext ec, TreeReference t, Hashtable<String, XPathExpression> variables, EntityStorageCache cache, String cacheIndex, String detailId) {
        super(t);
        this.fields = fields;
        this.data = new Object[fields.length];
        this.sortData = new String[fields.length];
        this.sortDataPieces = new String[fields.length][];
        this.context = ec;
        this.mVariableDeclarations = variables;
        this.mEntityStorageCache = cache;
        
        //TODO: It's weird that we pass this in, kind of, but the thing is that we don't want to figure out
        //if this ref is _cachable_ every time, since it's a pretty big lift
        this.mCacheIndex = cacheIndex;
        
        this.mDetailId = detailId;
    }
    
    public String getEntityCacheIndex() {
        return mCacheIndex;
    }
    
    private void loadVariableContext() {
        synchronized(mAsyncLock) {
            if(!mVariableContextLoaded) {
                //These are actually in an ordered hashtable, so we can't just get the keyset, since it's
                //in a 1.3 hashtable equivalent
                for(Enumeration<String> en = mVariableDeclarations.keys(); en.hasMoreElements();) {
                    String key = en.nextElement();
                    context.setVariable(key, XPathFuncExpr.unpack(mVariableDeclarations.get(key).eval(context)));
                }
                mVariableContextLoaded = true;
            }
        }
    }
    
    public Object getField(int i) {
        synchronized(mAsyncLock) {
            loadVariableContext();
            if(data[i] == null) {
                try {
                    data[i] = fields[i].getTemplate().evaluate(context);
                } catch(XPathException xpe) {
                    xpe.printStackTrace();
                    data[i] = "<invalid xpath: " + xpe.getMessage() + ">";
                }
            }
            return data[i];
        }
    }
    
    /**
     * Gets the indexed field used for searching and sorting these entities 
     * 
     * @return either the sort or the string field at the provided index, normalized
     * (IE: lowercase, etc) for sorting and searching.
     */
    public String getNormalizedField(int i) {
        String normalized = this.getSortField(i);
        if(normalized == null) { return ""; }
        return normalized;
    }
    
    public String getSortField(int i) {
        synchronized(mAsyncLock) {
            if(sortData[i] == null) {
                Text sortText = fields[i].getSort();
                if(sortText == null) {
                    return null;
                }
                    
                String cacheKey = EntityStorageCache.getCacheKey(mDetailId,String.valueOf(i)); 
                
                if(mCacheIndex != null) {
                    //Check the cache!
                    String value = mEntityStorageCache.retrieveCacheValue(mCacheIndex, cacheKey);
                    if(value != null) {
                        this.setSortData(i, value);
                        return sortData[i];
                    }
                }
                
                
                loadVariableContext();
                try {
                    sortText = fields[i].getSort();
                    if(sortText == null) {
                        this.setSortData(i, getFieldString(i));
                    } else {
                        this.setSortData(i, StringUtils.normalize(sortText.evaluate(context)));
                    }
                    
                    mEntityStorageCache.cache(mCacheIndex, cacheKey, sortData[i]);
                } catch(XPathException xpe) {
                    xpe.printStackTrace();
                    sortData[i] = "<invalid xpath: " + xpe.getMessage() + ">";
                }
            }
            return sortData[i];
        }
    }

    public int getNumFields() {
        return fields.length;
    }
    
    /**
     * @param i index of field
     * @return True iff the given field is relevant and has a non-blank value.
     */
    public boolean isValidField(int i) {
        //FIX THIS
        return true;
    }
    
    public TreeReference getElement() {
        return t;
    }
    
    public Object[] getData(){
        for(int i = 0; i < this.getNumFields() ; ++i){
            this.getField(i);
        }
        return data;
    }
    
    public String [] getBackgroundData(){
        String[] backgroundData = new String[this.getNumFields()];
        for(int i = 0; i < this.getNumFields() ; ++i){ 
            backgroundData[i] = "";
        }
        return backgroundData;
    }
    
    public String[] getSortFieldPieces(int i) {
        if(getSortField(i) == null ) {return new String[0];}
        return sortDataPieces[i];
    }

    public void setSortData(int i, String val) {
        synchronized(mAsyncLock) {
            this.sortData[i] = val;
            this.sortDataPieces[i] = breakUpField(val);
        }
    }
    
    public void setSortData(String cacheKey, String val) {
        int sortIndex = EntityStorageCache.getSortFieldIdFromCacheKey(mDetailId, cacheKey);
        if(sortIndex != -1) {
            setSortData(sortIndex, val);
        }
    }
    
    private String[] breakUpField(String input) {
        if(input == null ) {return new String[0];}
        else {
            //We always fuzzy match on the sort field and only if it is available
            //(as a way to restrict possible matching)
            return input.split(" ");
        }
    }
}
