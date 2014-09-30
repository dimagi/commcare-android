/**
 * 
 */
package org.commcare.android.models;

import java.util.Enumeration;
import java.util.Hashtable;

import org.commcare.android.database.user.models.User;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;

/**
 * @author ctsims
 *
 */
public class NodeEntityFactory {

    private EvaluationContext ec;
    
    Detail detail;
    FormInstance instance;
    User current; 
    
    public Detail getDetail() {
        return detail;
    }

    
    public NodeEntityFactory(Detail d, EvaluationContext ec) {
        this.detail = d;
        this.ec = ec;
    }

    public Entity<TreeReference> getEntity(TreeReference data) throws SessionUnavailableException {
        EvaluationContext nodeContext = new EvaluationContext(ec, data);
        Hashtable<String, XPathExpression> variables = getDetail().getVariableDeclarations();
        //These are actually in an ordered hashtable, so we can't just get the keyset, since it's
        //in a 1.3 hashtable equivalent
        for(Enumeration<String> en = variables.keys(); en.hasMoreElements();) {
            String key = en.nextElement();
            nodeContext.setVariable(key, XPathFuncExpr.unpack(variables.get(key).eval(nodeContext)));
        }
        
        //return new AsyncEntity<TreeReference>(detail.getFields(), nodeContext, data);
        
        int length = detail.getHeaderForms().length;
        Object[] details = new Object[length];
        String[] sortDetails = new String[length];
		String[] backgroundDetails = new String[length];
		boolean[] relevancyDetails = new boolean[length];
        int count = 0;
        for(DetailField f : this.getDetail().getFields()) {
            try {
                details[count] = f.getTemplate().evaluate(nodeContext);
                Text sortText = f.getSort();
				Text backgroundText = f.getBackground();
                if(sortText == null) {
                    sortDetails[count] = null;
                } else {
                    sortDetails[count] = sortText.evaluate(nodeContext);
				}
				if(backgroundText == null) {
					backgroundDetails[count] = "";
				} else {
					backgroundDetails[count] = backgroundText.evaluate(nodeContext);
                }
                relevancyDetails[count] = f.isRelevant(nodeContext);
            } catch(XPathException xpe) {
                xpe.printStackTrace();
                details[count] = "<invalid xpath: " + xpe.getMessage() + ">";
            } catch (XPathSyntaxException e) {
                e.printStackTrace();
            }
            count++;
        }
        
		return new Entity<TreeReference>(details, sortDetails, backgroundDetails, relevancyDetails, data);
    }
}
