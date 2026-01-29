import org.junit.platform.suite.api.*;

@Suite
@SuiteDisplayName("Unit Tests Only")
@SelectPackages("gregochr")
@ExcludeTags("integration")
public class UnitTestSuite {

}
