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
import org.javarosa.core.util.OrderedHashtable;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;

/**
 * @author ctsims
 *
 */
public class AsyncNodeEntityFactory extends NodeEntityFactory {

    User current; 
    
    OrderedHashtable<String, XPathExpression> mVariableDeclarations;
    
    public Detail getDetail() {
        return detail;
    }

    
    public AsyncNodeEntityFactory(Detail d, EvaluationContext ec) {
        super(d, ec);
        
        mVariableDeclarations = getDetail().getVariableDeclarations();
    }

    public Entity<TreeReference> getEntity(TreeReference data) throws SessionUnavailableException {
        EvaluationContext nodeContext = new EvaluationContext(ec, data);
        return new AsyncEntity<TreeReference>(detail.getFields(), nodeContext, data, mVariableDeclarations);
    }
}
