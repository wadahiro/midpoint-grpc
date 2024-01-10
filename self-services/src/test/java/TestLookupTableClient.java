import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jp.openstandia.midpoint.grpc.*;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

public class TestLookupTableClient {

    public static void main(String[] args) throws UnsupportedEncodingException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
                .usePlaintext()
                .build();

        SelfServiceResourceGrpc.SelfServiceResourceBlockingStub stub = SelfServiceResourceGrpc.newBlockingStub(channel);

        String token = Base64.getEncoder().encodeToString("Administrator:5ecr3t".getBytes("UTF-8"));

        Metadata headers = new Metadata();
        headers.put(Constant.AuthorizationMetadataKey, "Basic " + token);

        stub = MetadataUtils.attachHeaders(stub, headers);

        // without include and query
        GetLookupTableRequest req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .build();

        GetLookupTableResponse res = stub.getLookupTable(req);

        System.out.println(res);

        // without query
        req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .addInclude("row")
                .addInclude("description")
                .build();

        res = stub.getLookupTable(req);

        System.out.println(res);

        Timestamp lastChangeTimestamp = res.getResult().getRow(0).getLastChangeTimestamp();
        Instant instant = Instant.ofEpochSecond(lastChangeTimestamp.getSeconds(), lastChangeTimestamp.getNanos());
        System.out.println(instant.atZone(ZoneId.systemDefault()));

        // with query by key
        req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .addInclude("description")
                .setRelationalValueSearchQuery(RelationalValueSearchQueryMessage.newBuilder()
                        .setColumn(QNameMessage.newBuilder()
                                .setLocalPart("key"))
                        .setSearchType(RelationalValueSearch.EXACT)
                        .setSearchValue("en")
                )
                .build();

        res = stub.getLookupTable(req);

        System.out.println(res);

        // with query and paging
        req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .addInclude("description")
                .setRelationalValueSearchQuery(RelationalValueSearchQueryMessage.newBuilder()
                        .setPaging(PagingMessage.newBuilder()
                                .addOrdering(ObjectOrderingMessage.newBuilder()
                                        .setOrderBy("key")
                                        .setOrderDirection(OrderDirectionType.ASCENDING)
                                )
                                .setOffset(0)
                                .setMaxSize(2)
                        )
                )
                .build();

        res = stub.getLookupTable(req);

        System.out.println(res);

        // add new row
        ModifyObjectRequest modReq = ModifyObjectRequest.newBuilder()
                .setOid("00000000-0000-0000-0000-000000000200")
                .setObjectType(DefaultObjectType.LOOKUP_TABLE_TYPE)
                .addModifications(ItemDeltaMessage.newBuilder()
                        .setPath("row")
                        .addPrismValuesToAdd(PrismValueMessage.newBuilder()
                                .setContainer(PrismContainerValueMessage.newBuilder()
                                        .putValue("key", ItemMessage.newBuilder()
                                                .setProperty(PrismPropertyMessage.newBuilder()
                                                        .addValues(PrismPropertyValueMessage.newBuilder()
                                                                .setString("newKey")
                                                        )
                                                )
                                                .build()
                                        )
                                        .putValue("label", ItemMessage.newBuilder()
                                                .setProperty(PrismPropertyMessage.newBuilder()
                                                        .addValues(PrismPropertyValueMessage.newBuilder()
                                                                .setPolyString(PolyStringMessage.newBuilder()
                                                                        .setOrig("newLabel")
                                                                )
                                                        )
                                                )
                                                .build()
                                        )
                                )
                        )
                )
                .addOptions("raw")
                .build();

        ModifyObjectResponse modifyObjectResponse = stub.modifyObject(modReq);

        System.out.println(modifyObjectResponse);

        req = GetLookupTableRequest.newBuilder()
                .setName("Languages")
                .setRelationalValueSearchQuery(RelationalValueSearchQueryMessage.newBuilder()
                        .setColumn(QNameMessage.newBuilder()
                                .setLocalPart("key"))
                        .setSearchType(RelationalValueSearch.EXACT)
                        .setSearchValue("newKey")
                )
                .build();

        res = stub.getLookupTable(req);

        System.out.println(res);

        long id = res.getResult().getRow(0).getId();

        // update row
        modReq = ModifyObjectRequest.newBuilder()
                .setOid("00000000-0000-0000-0000-000000000200")
                .setObjectType(DefaultObjectType.LOOKUP_TABLE_TYPE)
                .addModifications(ItemDeltaMessage.newBuilder()
                        .setPath("row/[" + id + "]/label")
                        .addPrismValuesToReplace(PrismValueMessage.newBuilder()
                                .setProperty(PrismPropertyValueMessage.newBuilder()
                                        .setPolyString(PolyStringMessage.newBuilder()
                                                .setOrig("modLabel")
                                        )
                                )
                        )
                )
                .addOptions("raw")
                .build();

        modifyObjectResponse = stub.modifyObject(modReq);

        System.out.println(modifyObjectResponse);

        res = stub.getLookupTable(req);

        System.out.println(res);

        LookupTableRowMessage row = res.getResult().getRow(0);

        // delete row by id
        modReq = ModifyObjectRequest.newBuilder()
                .setOid("00000000-0000-0000-0000-000000000200")
                .setObjectType(DefaultObjectType.LOOKUP_TABLE_TYPE)
                .addModifications(ItemDeltaMessage.newBuilder()
                        .setPath("row")
                        .addPrismValuesToDelete(PrismValueMessage.newBuilder()
                                .setContainer(PrismContainerValueMessage.newBuilder()
                                        .setId(row.getId())
                                )
                        )
                )
                .addOptions("raw")
                .build();

        // delete row by key
//        modReq = ModifyObjectRequest.newBuilder()
//                .setOid("00000000-0000-0000-0000-000000000200")
//                .setType(QNameMessage.newBuilder().setLocalPart("LookupTableType"))
//                .addModifications(ItemDeltaMessage.newBuilder()
//                        .setPath("row")
//                        .addPrismValuesToDelete(PrismValueMessage.newBuilder()
//                                .setContainer(PrismContainerValueMessage.newBuilder()
//                                        .putValue("key", ItemMessage.newBuilder()
//                                                .setProperty(PrismPropertyMessage.newBuilder()
//                                                        .addValues(PrismPropertyValueMessage.newBuilder()
//                                                                .setString(row.getKey())
//                                                        )
//                                                )
//                                                .build()
//                                        )
//                                )
//                        )
//                )
//                .addOptions("raw")
//                .build();

        modifyObjectResponse = stub.modifyObject(modReq);

        System.out.println(modifyObjectResponse);

        res = stub.getLookupTable(req);

        System.out.println(res);
    }
}
