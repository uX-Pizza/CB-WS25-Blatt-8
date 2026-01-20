package cpp.runtime;

public class ReturnSignal extends RuntimeException {
  public final Value value;

  public ReturnSignal(Value value) {
    super(null, null, false, false);
    this.value = value;
  }
}
