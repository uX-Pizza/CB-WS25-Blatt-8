package cpp.model;

import java.util.Objects;

public class Type {
  public enum Kind {
    BOOL,
    INT,
    CHAR,
    STRING,
    VOID,
    CLASS
  }

  public final Kind kind;
  public final String className;
  public final boolean isRef;

  private Type(Kind kind, String className, boolean isRef) {
    this.kind = kind;
    this.className = className;
    this.isRef = isRef;
  }

  public static Type boolType(boolean isRef) {
    return new Type(Kind.BOOL, null, isRef);
  }

  public static Type intType(boolean isRef) {
    return new Type(Kind.INT, null, isRef);
  }

  public static Type charType(boolean isRef) {
    return new Type(Kind.CHAR, null, isRef);
  }

  public static Type stringType(boolean isRef) {
    return new Type(Kind.STRING, null, isRef);
  }

  public static Type voidType() {
    return new Type(Kind.VOID, null, false);
  }

  public static Type classType(String name, boolean isRef) {
    return new Type(Kind.CLASS, name, isRef);
  }

  public Type withoutRef() {
    if (!isRef) {
      return this;
    }
    return new Type(kind, className, false);
  }

  public Type withRef() {
    if (isRef) {
      return this;
    }
    return new Type(kind, className, true);
  }

  public boolean isClass() {
    return kind == Kind.CLASS;
  }

  public boolean isVoid() {
    return kind == Kind.VOID;
  }

  @Override
  public String toString() {
    String base;
    if (kind == Kind.CLASS) {
      base = className;
    } else {
      base = kind.name().toLowerCase();
    }
    return isRef ? base + "&" : base;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Type)) {
      return false;
    }
    Type other = (Type) obj;
    return kind == other.kind
        && Objects.equals(className, other.className)
        && isRef == other.isRef;
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, className, isRef);
  }
}
