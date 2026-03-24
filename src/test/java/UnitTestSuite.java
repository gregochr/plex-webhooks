import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Unit Tests Only")
@SelectPackages("gregochr")
@ExcludeTags("integration")
public class UnitTestSuite {

}
