import java.util.List;
import java.util.StringJoiner;

public class SignatureUtil {
  public static String signature(String name, List<ParamDef> params) {
    StringJoiner joiner = new StringJoiner(",", name + "(", ")");
    for (ParamDef param : params) {
      joiner.add(param.type.toString());
    }
    return joiner.toString();
  }
}
