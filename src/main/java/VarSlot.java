public class VarSlot {
  private final Type declaredType;
  private VarSlot refTarget;
  private Value value;

  public VarSlot(Type declaredType, Value value) {
    this.declaredType = declaredType;
    this.value = value;
  }

  public static VarSlot refSlot(Type declaredType, VarSlot target) {
    VarSlot slot = new VarSlot(declaredType, null);
    slot.refTarget = target;
    return slot;
  }

  public boolean isRef() {
    return refTarget != null;
  }

  public Type getDeclaredType() {
    return declaredType;
  }

  public VarSlot getRefTarget() {
    return refTarget;
  }

  public Value get() {
    if (refTarget != null) {
      return refTarget.get();
    }
    return value;
  }

  public void set(Value newValue) {
    if (refTarget != null) {
      refTarget.set(newValue);
      return;
    }
    value = newValue;
  }
}
