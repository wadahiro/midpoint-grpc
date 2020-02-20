package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.impl.ModelCrudService;
import com.evolveum.midpoint.prism.Containerable;
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
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.stream.Collectors;

import static jp.openstandia.midpoint.grpc.TypeConverter.toMessage;
import static jp.openstandia.midpoint.grpc.TypeConverter.toPrismObject;

@GRpcService
public class SelfServiceResource extends SelfServiceResourceGrpc.SelfServiceResourceImplBase implements MidPointGrpcService {

    private static final Trace LOGGER = TraceManager.getTrace(SelfServiceResource.class);

    static {
        LOGGER.info("SelfServiceResource loaded");
        System.out.println("SelfServiceResource loaded");
    }

    public static final String CLASS_DOT = SelfServiceResource.class.getName() + ".";
    public static final String OPERATION_SELF = CLASS_DOT + "self";
    public static final String OPERATION_SELF_ASSIGNMENT = CLASS_DOT + "selfAssignment";
    public static final String OPERATION_ADD_USER = CLASS_DOT + "addUser";
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
//    @Autowired
//    protected SchemaRegistry registry;

    public static final Metadata.Key<PolicyError> PolicyErrorMetadataKey = ProtoUtils.keyForProto(PolicyError.getDefaultInstance());

    @Override
    public Metadata handlePolicyViolationException(PolicyViolationException e) {
        PolicyError error = TypeConverter.toPolicyError(e);

        Metadata metadata = new Metadata();
        metadata.put(PolicyErrorMetadataKey, error);

        return metadata;
    }

    /**
     * Getting self profile API based on
     * {@link com.evolveum.midpoint.model.impl.ModelRestService#getSelf(org.apache.cxf.jaxrs.ext.MessageContext)}.
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void getSelf(GetSelfRequest request, StreamObserver<GetSelfResponse> responseObserver) {
        LOGGER.debug("Start getSelf");

        PrismObject<UserType> self = runTask(ctx -> {
            Task task = ctx.task;
            UserType loggedInUser = ctx.principal.getUser();

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SELF);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> getOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, null, prismContext);

            try {
                PrismObject<UserType> user = modelCrudService.getObject(UserType.class, loggedInUser.getOid(), getOptions, task, parentResult);
                parentResult.recordSuccessIfUnknown();
                return user;
            } finally {
                parentResult.computeStatusIfUnknown();
            }
        });

        GetSelfResponse res = GetSelfResponse.newBuilder()
                .setProfile(toMessage(self))
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End getSelf");
    }

    private class A {

    }

    /**
     * Getting self assignments API.
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void getSelfAssignment(GetSelfAssignmentRequest request, StreamObserver<GetSelfAssignmentResponse> responseObserver) {
        LOGGER.debug("Start getSelf");

        List<AssignmentMessage> assignments = runTask(ctx -> {
            Task task = ctx.task;
            UserType loggedInUser = ctx.principal.getUser();

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SELF);

            try {
                PrismObject<UserType> user = modelCrudService.getObject(UserType.class, loggedInUser.getOid(), null, task, parentResult);
                UserType userType = user.asObjectable();

                List<AssignmentType> assignment = userType.getAssignment();
                List<String> oids = assignment.stream()
                        .map(x -> x.getTargetRef().getOid())
                        .collect(Collectors.toList());

                if (request.getIncludeOrgRefDetail()) {
                    List<String> orgRefOids = assignment.stream()
                            .filter(x -> x.getOrgRef() != null)
                            .map(x -> x.getOrgRef().getOid())
                            .collect(Collectors.toList());
                    oids.addAll(orgRefOids);
                }

                // key: oid, value: Org/Role/Archetype etc.
                Map<String, AbstractRoleType> cache = new HashMap<>();

                // TODO: use search mode for performance?
                // For caching detail of the target objects
                oids.stream().forEach(x -> {
                    try {
                        AbstractRoleType o = modelService.getObject(AbstractRoleType.class, x,
                                SelectorOptions.createCollection(GetOperationOptions.createExecutionPhase().resolveNames(true)), task, parentResult)
                                .asObjectable();
                        cache.put(x, o);

                        // End User doesn't have get permission for Archetype with default.
                        // So currently, we rely on `resolveNames` mode to get name of archetype.
//                        if (request.getIncludeArchetypeRefDetail()) {
//                            o.getArchetypeRef().stream()
//                                    .forEach(ref -> {
//                                        try {
//                                            ArchetypeType a = modelService.getObject(ArchetypeType.class, ref.getOid(),
//                                                    SelectorOptions.createCollection(GetOperationOptions.createExecutionPhase().resolve(true)), task, parentResult)
//                                                    .asObjectable();
//                                            cache.put(ref.getOid(), a);
//
//                                        } catch (CommonException e) {
//                                            LOGGER.warn("Cannot fetch the archetype for collecting assignment detail. oid: {}", ref.getOid(), e);
//                                        }
//                                    });
//                        }
                    } catch (CommonException e) {
                        LOGGER.warn("Cannot fetch the object for collecting assignment detail. oid: {}", x, e);
                    }
                });

                // End User doesn't have search permission with default setting...
                // TODO: use admin permission partially?
                // For caching detail of the target objects
//                ObjectQuery query = createInOidQuery(ObjectType.class, oids);
//                SearchResultList<PrismObject<AbstractRoleType>> foundObjects = modelService.searchObjects(AbstractRoleType.class, query, null,
//                        task, parentResult);
//                foundObjects.stream().forEach(x -> cache.put(x.getOid(), x.asObjectable()));

                List<AssignmentMessage> assignmentMessages = assignment.stream()
                        // The user might not have permission to get the target. So filter them.
                        .filter(x -> cache.containsKey(x.getTargetRef().getOid()))
                        .map(x -> {
                            AbstractRoleType o = cache.get(x.getTargetRef().getOid());

                            ObjectReferenceType orgRef = x.getOrgRef();
                            AbstractRoleType resolvedOrgRef = null;
                            if (orgRef != null) {
                                resolvedOrgRef = cache.get(orgRef.getOid());
                            }

                            QName relation = x.getTargetRef().getRelation();

                            return BuilderWrapper.wrap(AssignmentMessage.newBuilder())
                                    .nullSafe(toMessage(orgRef, resolvedOrgRef), (b, v) -> b.setOrgRef(v))
                                    .unwrap()
                                    .setTargetRef(
                                            BuilderWrapper.wrap(ReferenceMessage.newBuilder())
                                                    .nullSafe(o.getOid(), (b, v) -> b.setOid(v))
                                                    .nullSafe(toMessage(o.getName()), (b, v) -> b.setName(v))
                                                    .nullSafe(toMessage(o.getDescription()), (b, v) -> b.setDescription(v))
                                                    .nullSafe(toMessage(o.getDisplayName()), (b, v) -> b.setDisplayName(v))
                                                    .nullSafe(toMessage(o.getArchetypeRef(), cache), (b, v) -> b.addAllArchetypeRef(v))
                                                    .unwrap()
                                                    .setRelation(
                                                            RelationMessage.newBuilder()
                                                                    .setNamespaceURI(relation.getNamespaceURI())
                                                                    .setLocalPart(relation.getLocalPart())
                                                                    .setPrefix(relation.getPrefix())
                                                    )
                                                    .build()
                                    ).build();
                        })
                        .collect(Collectors.toList());

                parentResult.recordSuccessIfUnknown();
                return assignmentMessages;
            } finally {
                parentResult.computeStatusIfUnknown();
            }
        });

        GetSelfAssignmentResponse res = GetSelfAssignmentResponse.newBuilder()
                .addAllAssignment(assignments)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End getSelfAssignment");
    }

    @Override
    public void modifyProfile(ModifyProfileRequest request, StreamObserver<ModifyProfileResponse> responseObserver) {
        LOGGER.debug("Start modifyProfile");

        runTask(ctx -> {
            Task task = ctx.task;
            UserType user = ctx.principal.getUser();

            OperationResult updateResult = task.getResult().createSubresult(OPERATION_EXECUTE_USER_UPDATE);
            try {
                Collection<ObjectDelta<? extends ObjectType>> modifications = new ArrayList<>();

                S_ItemEntry i = prismContext.deltaFor(UserType.class);

                // https://wiki.evolveum.com/display/midPoint/Using+Prism+Deltas
                for (UserItemDelta m : request.getModificationsList()) {
                    UserItemPath path = m.getName();

                    ItemName itemName = TypeConverter.toItemName(path);
                    S_ValuesEntry v = i.item(itemName);

                    S_ItemEntry entry = null;
                    if (!m.getValuesToAdd().isEmpty()) {
                        S_MaybeDelete av = v.add(TypeConverter.toValue(path, m.getValuesToAdd()));

                        if (!m.getValuesToDelete().isEmpty()) {
                            entry = av.delete(TypeConverter.toValue(path, m.getValuesToDelete()));
                        }

                    } else if (!m.getValuesToReplace().isEmpty()) {
                        entry = v.replace(TypeConverter.toValue(path, m.getValuesToReplace()));

                    } else if (!m.getValuesToDelete().isEmpty()) {
                        entry = v.delete(TypeConverter.toValue(path, m.getValuesToDelete()));
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
            } finally {
                updateResult.computeStatusIfUnknown();
            }
            return null;
        });

        ModifyProfileResponse res = ModifyProfileResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End updateProfile");
    }

    @Override
    public void updateCredential(UpdateCredentialRequest request, StreamObserver<UpdateCredentialResponse> responseObserver) {
        LOGGER.debug("Start updateCredential");

        runTask(ctx -> {
            updateCredential(ctx, request.getOld(), request.getNew(), true);
            return null;
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
            updateCredential(ctx, null, request.getNew(), false);
            return null;
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

    @Override
    public void addUser(AddUserRequest request, StreamObserver<AddUserResponse> responseObserver) {
        LOGGER.debug("Start addUser");

        String resultOid = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_ADD_USER);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            UserTypeMessage message = request.getProfile();
            PrismObject<UserType> user = toPrismObject(prismContext, message);

            String oid = modelCrudService.addObject(user, modelExecuteOptions, task, parentResult);
            LOGGER.debug("returned oid :  {}", oid);

            return oid;
        });

        AddUserResponse res = AddUserResponse.newBuilder()
                .setOid(resultOid)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End addUser");
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
        } catch (PolicyViolationException e) {
            LoggingUtils.logExceptionAsWarning(LOGGER, "Couldn't save password changes because of policy violation: {}", e, e.getMessage());
            throw e;
        } catch (EncryptionException | ObjectAlreadyExistsException e) {
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

    private <T> ObjectQuery createInOidQuery(Class<? extends Containerable> queryClass, List<String> oids) throws SchemaException {
        return prismContext.queryFor(queryClass)
                .id(oids.toArray(new String[]{}))
                .build();
    }
}