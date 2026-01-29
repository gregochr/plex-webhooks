import org.junit.platform.suite.api.*;

@Suite
@SuiteDisplayName("Integration Tests Only")
@SelectPackages("gregochr")
@IncludeTags("integration")
public class IntegrationTestSuite {
}
