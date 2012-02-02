/**
 * 
 */
package org.commcare.android.models;

import java.util.Hashtable;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;

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
		for(String key : variables.keySet()) {
			nodeContext.setVariable(key, XPathFuncExpr.unpack(variables.get(key).eval(nodeContext)));
		}
		
		String[] details = new String[detail.getHeaderForms().length];
		int count = 0;
		for(Text t : this.getDetail().getTemplates()) {
			details[count] = t.evaluate(nodeContext);
			count++;
		}
		
		return new Entity<TreeReference>(details, data);
	}

}
