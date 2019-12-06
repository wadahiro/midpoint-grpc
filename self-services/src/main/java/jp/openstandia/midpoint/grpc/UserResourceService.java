package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.model.api.AuthenticationEvaluator;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.authentication.MidPointUserProfilePrincipal;
import com.evolveum.midpoint.model.api.authentication.UserProfileService;
import com.evolveum.midpoint.model.api.context.PasswordAuthenticationContext;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.security.api.SecurityContextManager;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CredentialsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.evolveum.midpoint.schema.constants.SchemaConstants.NS_MODEL_CHANNEL;

@GRpcService
public class UserResourceService extends UserResourceServiceGrpc.UserResourceServiceImplBase {
    static {
        System.out.println("UserResourceService loaded");
    }

    public static final String CLASS_DOT = UserResourceService.class.getName() + ".";
    public static final String OPERATION_EXECUTE_CREDENTIAL_CHECK = CLASS_DOT + "executeCredentialCheck";
    public static final String OPERATION_EXECUTE_CREDENTIAL_UPDATE = CLASS_DOT + "executeCredentialUpdate";

    public static final QName CHANNEL_GRPC_SERVICE_QNAME = new QName(NS_MODEL_CHANNEL, "grpc");
    public static final String CHANNEL_GRPC_SERVICE_URI = QNameUtil.qNameToUri(CHANNEL_GRPC_SERVICE_QNAME);

    private static final Trace LOGGER = TraceManager.getTrace(UserResourceService.class);

    @Autowired
    protected ModelService modelService;
    @Autowired
    private ModelInteractionService modelInteraction;
    @Autowired
    protected TaskManager taskManager;
    @Autowired
    protected AuditService auditService;
    @Autowired
    protected PrismContext prismContext;
    @Autowired
    private UserProfileService userService;
    @Autowired
    protected SecurityContextManager securityContextManager;
    @Autowired
    protected Protector protector;
    @Autowired
    protected transient AuthenticationEvaluator<PasswordAuthenticationContext> passwordAuthenticationEvaluator;

    @Override
    public void updateCredentialByName(UpdateCredentialByNameRequest request, StreamObserver<UpdateCredentialResponse> responseObserver) {
        String name = request.getName();
        System.out.println("updateCredentialByName name: " + name);

        try {
            updateCredential(request.getName(), request.getOld(), request.getNew(), true);
        } catch (ObjectNotFoundException e) {
            StatusRuntimeException exception = Status.NOT_FOUND
                    .withDescription("Not Found")
                    .asRuntimeException();
            throw exception;
        } catch (Exception e) {
            responseObserver.onError(e);
            return;
        }

        UpdateCredentialResponse res = UpdateCredentialResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();
    }

    @Override
    public void forceUpdateCredentialByName(ForceUpdateCredentialByNameRequest request, StreamObserver<UpdateCredentialResponse> responseObserver) {
        String name = request.getName();
        System.out.println("forceUpdateCredentialByName name: " + name);

        try {
            updateCredential(request.getName(), null, request.getNew(), false);
        } catch (ObjectNotFoundException e) {
            StatusRuntimeException exception = Status.NOT_FOUND
                    .withDescription("Not Found")
                    .asRuntimeException();
            throw exception;
        } catch (Exception e) {
            responseObserver.onError(e);
            return;
        }

        UpdateCredentialResponse res = UpdateCredentialResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();
    }

    @Override
    public void requestRoleByName(RequestRoleByNameRequest request, StreamObserver<RequestRoleResponse> responseObserver) {
        String name = request.getName();
        System.out.println("requestRoleByName name: " + name);

        // Set auth context
        initInitiator(name);


    }

    protected void initInitiator(String name) {
        try {
            MidPointUserProfilePrincipal principal = userService.getPrincipal(name);
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(principal, null);
            SecurityContextHolder.getContext().setAuthentication(token);
        } catch (ObjectNotFoundException e) {
            StatusRuntimeException exception = Status.NOT_FOUND
                    .withDescription("Not Found")
                    .asRuntimeException();
            throw exception;
        } catch (Exception e) {
            StatusRuntimeException exception = Status.INTERNAL
                    .withDescription("internal_error")
                    .asRuntimeException();
            throw exception;
        }
    }

    protected void updateCredential(String name, String oldCred, String newCred, boolean validate) throws SchemaException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException, ObjectNotFoundException, EncryptionException, PolicyViolationException, ObjectAlreadyExistsException {
        final String OPERATION_NAME = "updateCredential";

        initInitiator(name);

        Task task = createTaskInstance(UserResourceService.class.getName() + OPERATION_NAME);
        OperationResult result = task.getResult();

        List<PrismObject<UserType>> users = findUsers(UserType.F_NAME, name,
                PrismConstants.POLY_STRING_NORM_MATCHING_RULE_NAME, task, result);

        if (users.size() != 1) {
            StatusRuntimeException exception = Status.NOT_FOUND
                    .withDescription("Not Found")
                    .asRuntimeException();
            throw exception;
        }

        PrismObject<UserType> user = users.get(0);

        ProtectedStringType oldPassword = null;
        if (validate) {
            OperationResult checkPasswordResult = task.getResult().createSubresult(OPERATION_EXECUTE_CREDENTIAL_CHECK);
            try {
                oldPassword = new ProtectedStringType();
                oldPassword.setClearValue(oldCred);

                boolean isCorrectPassword = modelInteraction.checkPassword(user.getOid(), oldPassword,
                        task, checkPasswordResult);
                if (!isCorrectPassword) {
                    StatusRuntimeException exception = Status.INVALID_ARGUMENT
                            .withDescription("invalid_credential")
                            .asRuntimeException();
                    throw exception;
                }
                checkPasswordResult.computeStatus();
            } finally {
                checkPasswordResult.computeStatusIfUnknown();
            }
        }

        OperationResult updateResult = task.getResult().createSubresult(OPERATION_EXECUTE_CREDENTIAL_UPDATE);
        try {
            ProtectedStringType newProtectedCred = protector.encryptString(newCred);
            final ItemPath valuePath = ItemPath.create(SchemaConstantsGenerated.C_CREDENTIALS,
                    CredentialsType.F_PASSWORD, PasswordType.F_VALUE);
            SchemaRegistry registry = prismContext.getSchemaRegistry();
            Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();

            PrismObjectDefinition objDef = registry.findObjectDefinitionByCompileTimeClass(UserType.class);

            PropertyDelta<ProtectedStringType> delta = prismContext.deltaFactory().property()
                    .createModificationReplaceProperty(valuePath, objDef, newProtectedCred);
            if (oldPassword != null) {
                delta.addEstimatedOldValue(prismContext.itemFactory().createPropertyValue(oldPassword));
            }

            deltas.add(prismContext.deltaFactory().object().createModifyDelta(user.getOid(), delta, UserType.class));

            modelService.executeChanges(deltas, null, task, updateResult);

            updateResult.computeStatus();
        } catch (EncryptionException | ObjectAlreadyExistsException | PolicyViolationException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't save password changes", e);
            throw e;
        } finally {
            updateResult.computeStatusIfUnknown();
        }
    }

    private UsernamePasswordAuthenticationToken authenticateUser(String username, String password, OperationResult result) {
        ConnectionEnvironment connEnv = ConnectionEnvironment.create(CHANNEL_GRPC_SERVICE_URI);
        try {
            UsernamePasswordAuthenticationToken token = passwordAuthenticationEvaluator.authenticate(connEnv, new PasswordAuthenticationContext(username, password));
            return token;
        } catch (AuthenticationException ex) {

            return null;
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Failed to confirm registration", ex);
            return null;
        }
    }

    private <T> List<PrismObject<UserType>> findUsers(QName propertyName, T email, QName matchingRule,
                                                      Task task, OperationResult result) throws SchemaException, ObjectNotFoundException,
            SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {

        ObjectQuery query = createUserEQQuery(propertyName, matchingRule, email);
        List<PrismObject<UserType>> foundObjects = modelService.searchObjects(UserType.class, query, null,
                task, result);
        return foundObjects;
    }

    private <T> ObjectQuery createUserEQQuery(QName property, QName matchingRule, T value)
            throws SchemaException {
        return prismContext.queryFor(UserType.class)
                .item(property)
                .eq(value)
                .matching(matchingRule)
                .build();
    }

    protected Task createTaskInstance(String operationName) {
        // TODO: better task initialization
        Task task = taskManager.createTaskInstance(operationName);
        setTaskOwner(task);
        task.setChannel(CHANNEL_GRPC_SERVICE_URI);
        return task;
    }

    protected void setTaskOwner(Task task) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new SystemException("Failed to get authentication object");
        }
        UserType userType = ((MidPointPrincipal) (SecurityContextHolder.getContext().getAuthentication().getPrincipal())).getUser();
        if (userType == null) {
            throw new SystemException("Failed to get user from authentication object");
        }
        task.setOwner(userType.asPrismObject());
    }
}