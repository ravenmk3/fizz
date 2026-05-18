package ravenworks.fizz.common.runtime;

import lombok.experimental.UtilityClass;
import ravenworks.fizz.common.util.Uuids;


/**
 * @author Raven
 */
@UtilityClass
public final class InstanceId {

    public static final String VALUE = Uuids.uuidHex();

}
