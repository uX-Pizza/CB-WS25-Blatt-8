public class EvalResult {
  public final Value value;
  public final Type type;
  public final boolean isLValue;
  public final VarSlot slot;
  public final boolean isRefBinding;

  public EvalResult(Value value, Type type, boolean isLValue, VarSlot slot, boolean isRefBinding) {
    this.value = value;
    this.type = type;
    this.isLValue = isLValue;
    this.slot = slot;
    this.isRefBinding = isRefBinding;
  }
}
