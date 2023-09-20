package app.grapheneos.hardeningtest;

public class IsolatedTestService extends TestService {

    protected String getExpectedSELinuxContextPrefix() {
        return "u:r:isolated_app:s0:c";
    }
}
