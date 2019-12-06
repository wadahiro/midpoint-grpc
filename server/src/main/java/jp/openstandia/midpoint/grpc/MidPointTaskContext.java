package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.security.api.HttpConnectionInformation;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import org.springframework.security.core.Authentication;

public class MidPointTaskContext {

    public final HttpConnectionInformation connection;
    public final ConnectionEnvironment connEnv;
    public final Task task;
    public final Authentication authentication;
    public final MidPointPrincipal principal;

    public <T> MidPointTaskContext(HttpConnectionInformation connection, ConnectionEnvironment connEnv, Task task, Authentication auth, MidPointPrincipal principal) {
        this.connection = connection;
        this.connEnv = connEnv;
        this.task = task;
        this.authentication = auth;
        this.principal = principal;
    }
}
