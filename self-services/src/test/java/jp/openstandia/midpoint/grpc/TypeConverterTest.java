package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeConverterTest {

    @Test
    public void toItemName() {
        ItemName name = TypeConverter.toItemName(UserItemPath.F_NAME);
        assertEquals(UserType.F_NAME, name, "Should get ItemName of ObjectType");

        ItemName locality = TypeConverter.toItemName(UserItemPath.F_LOCALITY);
        assertEquals(UserType.F_LOCALITY, locality, "Should get ItemName of FocusType");

        ItemName fullName = TypeConverter.toItemName(UserItemPath.F_FULL_NAME);
        assertEquals(UserType.F_FULL_NAME, fullName, "Should get ItemName of UserType");
    }

    @Test
    public void toValue() {
        Object foo = TypeConverter.toValue(UserItemPath.F_NAME, "foo");
        assertEquals(PolyString.class, foo.getClass());

        Object bar = TypeConverter.toValue(UserItemPath.F_EMPLOYEE_TYPE, "bar");
        assertEquals(String.class, bar.getClass());

        Object hoge = TypeConverter.toValue(UserItemPath.F_ORGANIZATION, "hoge");
        assertEquals(PolyString.class, hoge.getClass());
    }
}