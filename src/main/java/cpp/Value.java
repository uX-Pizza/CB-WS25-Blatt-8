package cpp;

public class Value {
  public final Type type;
  public final Object data;

  public Value(Type type, Object data) {
    this.type = type;
    this.data = data;
  }

  public static Value boolValue(boolean value) {
    return new Value(Type.boolType(false), value);
  }

  public static Value intValue(int value) {
    return new Value(Type.intType(false), value);
  }

  public static Value charValue(char value) {
    return new Value(Type.charType(false), value);
  }

  public static Value stringValue(String value) {
    return new Value(Type.stringType(false), value);
  }

  public static Value voidValue() {
    return new Value(Type.voidType(), null);
  }
}
