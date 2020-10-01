package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.web.boot.MidPointSpringApplication;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.util.FileSystemUtils;

import java.io.File;

public class MidPointGrpcTestRunner implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        FileSystemUtils.deleteRecursively(new File("./target/midpoint"));

        setProperites();

        MidPointSpringApplication.main(new String[]{});
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        setProperites();

        // maven-surefire-plugin does not support tests or any referenced libraries calling System.exit() at any time
        // see https://maven.apache.org/surefire/maven-surefire-plugin/faq.html#vm-termination

        // MidPointSpringApplication.main(new String[]{"stop"});
    }

    public void setProperites() {
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("midpoint.home", "./target/midpoint");
        System.setProperty("midpoint.logging.alt.enabled", "true");
    }
}
