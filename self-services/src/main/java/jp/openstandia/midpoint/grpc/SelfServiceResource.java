package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.model.api.AuthenticationEvaluator;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.context.PasswordAuthenticationContext;
import com.evolveum.midpoint.model.impl.ModelCrudService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.delta.builder.S_ItemEntry;
import com.evolveum.midpoint.prism.delta.builder.S_MaybeDelete;
import com.evolveum.midpoint.prism.delta.builder.S_ValuesEntry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@GRpcService(interceptors = BasicAuthenticationInterceptor.class)
public class SelfServiceResource extends SelfServiceResourceGrpc.SelfServiceResourceImplBase implements MidPointGrpcService {

    private static final Trace LOGGER = TraceManager.getTrace(SelfServiceResource.class);

    static {
        LOGGER.info("SelfServiceResource loaded");
        System.out.println("SelfServiceResource loaded");
    }

    public static final String CLASS_DOT = SelfServiceResource.class.getName() + ".";
    public static final String OPERATION_EXECUTE_USER_UPDATE = CLASS_DOT + "executeUserUpdate";
    public static final String OPERATION_EXECUTE_CREDENTIAL_CHECK = CLASS_DOT + "executeCredentialCheck";
    public static final String OPERATION_EXECUTE_CREDENTIAL_UPDATE = CLASS_DOT + "executeCredentialUpdate";

    @Autowired
    protected ModelService modelService;
    @Autowired
    protected ModelCrudService modelCrudService;
    @Autowired
    protected ModelInteractionService modelInteraction;
    @Autowired
    protected PrismContext prismContext;
    @Autowired
    protected Protector protector;
    @Autowired
    protected transient AuthenticationEvaluator<PasswordAuthenticationContext> passwordAuthenticationEvaluator;

    @Override
    public void modifyProfile(ModifyProfileRequest request, StreamObserver<ModifyProfileResponse> responseObserver) {
        LOGGER.debug("Start modifyProfile");

        runTask(ctx -> {
            try {
                Task task = ctx.task;
                UserType user = ctx.principal.getUser();

                OperationResult updateResult = task.getResult().createSubresult(OPERATION_EXECUTE_USER_UPDATE);
                try {
                    Collection<ObjectDelta<? extends ObjectType>> modifications = new ArrayList<>();

                    S_ItemEntry i = prismContext.deltaFor(UserType.class);

                    // https://wiki.evolveum.com/display/midPoint/Using+Prism+Deltas
                    for (UserItemDelta m : request.getModificationsList()) {
                        S_ValuesEntry v = null;
                        boolean isPolyString = false;
                        switch (m.getName()) {
                            case NAME:
                                v = i.item(UserType.F_NAME);
                                isPolyString = true;
                                break;
                            case EMAIL_ADDRESS:
                                v = i.item(UserType.F_EMAIL_ADDRESS);
                                break;
                            case GIVEN_NAME:
                                v = i.item(UserType.F_GIVEN_NAME);
                                isPolyString = true;
                                break;
                            case FAMILY_NAME:
                                v = i.item(UserType.F_FAMILY_NAME);
                                isPolyString = true;
                                break;
                        }

                        if (v == null) {
                            LOGGER.warn("Invalid argument. Unsupported name: {}", m.getName());
                            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
                        }

                        S_ItemEntry entry = null;
                        if (!m.getValuesToAdd().isEmpty()) {
                            S_MaybeDelete av = v.add(asStringOrPolyString(m.getValuesToAdd(), isPolyString));
                            if (!m.getValuesToDelete().isEmpty()) {
                                entry = av.delete(asStringOrPolyString(m.getValuesToDelete(), isPolyString));
                            }
                        } else if (!m.getValuesToReplace().isEmpty()) {
                            entry = v.replace(asStringOrPolyString(m.getValuesToReplace(), isPolyString));
                        } else if (!m.getValuesToDelete().isEmpty()) {
                            entry = v.delete(asStringOrPolyString(m.getValuesToDelete(), isPolyString));
                        }

                        if (entry == null) {
                            LOGGER.warn("Invalid argument. No values for modification.");
                            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
                        }
                        i = entry;
                    }
                    // TODO implement options
                    ModelExecuteOptions options = new ModelExecuteOptions();

                    List<ItemDelta<?, ?>> deltas = i.asItemDeltas();

                    modelCrudService.modifyObject(UserType.class, user.getOid(), deltas, options, task, updateResult);

                    updateResult.computeStatus();
                } catch (ObjectAlreadyExistsException | PolicyViolationException e) {
                    LoggingUtils.logUnexpectedException(LOGGER, "Couldn't update user changes", e);
                    throw e;
                } finally {
                    updateResult.computeStatusIfUnknown();
                }
                return null;
            } catch (ObjectNotFoundException e) {
                StatusRuntimeException exception = Status.NOT_FOUND
                        .withDescription("Not Found")
                        .asRuntimeException();
                throw exception;
            } catch (Exception e) {
                responseObserver.onError(e);
                return null;
            }
        });

        ModifyProfileResponse res = ModifyProfileResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End updateProfile");
    }

    private Object asStringOrPolyString(String s, boolean isPolyString) {
        if (isPolyString) {
            return PolyString.fromOrig(s);
        }
        return s;
    }

    @Override
    public void updateCredential(UpdateCredentialRequest request, StreamObserver<UpdateCredentialResponse> responseObserver) {
        LOGGER.debug("Start updateCredential");

        runTask(ctx -> {
            try {
                updateCredential(ctx, request.getOld(), request.getNew(), true);
                return null;
            } catch (ObjectNotFoundException e) {
                StatusRuntimeException exception = Status.NOT_FOUND
                        .withDescription("Not Found")
                        .asRuntimeException();
                throw exception;
            } catch (Exception e) {
                responseObserver.onError(e);
                return null;
            }
        });

        UpdateCredentialResponse res = UpdateCredentialResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End updateCredential");
    }

    @Override
    public void forceUpdateCredential(ForceUpdateCredentialRequest request, StreamObserver<UpdateCredentialResponse> responseObserver) {
        LOGGER.debug("Start forceUpdateCredential");

        runTask(ctx -> {
            try {
                updateCredential(ctx, null, request.getNew(), false);
                return null;
            } catch (ObjectNotFoundException e) {
                StatusRuntimeException exception = Status.NOT_FOUND
                        .withDescription("Not Found")
                        .asRuntimeException();
                throw exception;
            } catch (Exception e) {
                responseObserver.onError(e);
                return null;
            }
        });

        UpdateCredentialResponse res = UpdateCredentialResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End forceUpdateCredential");
    }

    @Override
    public void requestRole(RequestRoleRequest request, StreamObserver<RequestRoleResponse> responseObserver) {
        LOGGER.debug("Start requestRole");

        

        LOGGER.debug("End requestRole");
    }

    protected void updateCredential(MidPointTaskContext ctx, String oldCred, String newCred, boolean validate) throws SchemaException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException, ObjectNotFoundException, EncryptionException, PolicyViolationException, ObjectAlreadyExistsException {
        Task task = ctx.task;
        UserType user = ctx.principal.getUser();

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
}