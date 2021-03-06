// Copyright 2019 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.python;

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.BuiltinProvider;
import com.google.devtools.build.lib.packages.Info;
import com.google.devtools.build.lib.skylarkbuildapi.python.PyInfoApi;
import com.google.devtools.build.lib.syntax.Depset;
import com.google.devtools.build.lib.syntax.Depset.TypeException;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.SkylarkType;
import com.google.devtools.build.lib.syntax.Starlark;
import java.util.Objects;
import javax.annotation.Nullable;

/** Instance of the provider type for the Python rules. */
public final class PyInfo implements Info, PyInfoApi<Artifact> {

  public static final String STARLARK_NAME = "PyInfo";

  public static final PyInfoProvider PROVIDER = new PyInfoProvider();

  /**
   * Returns true if the given depset has the given content type and order compatible with the given
   * order.
   */
  private static boolean depsetHasTypeAndCompatibleOrder(
      Depset depset, SkylarkType type, Order order) {
    // Work around #7266 by special-casing the empty set in the type check.
    boolean typeOk = depset.isEmpty() || depset.getContentType().equals(type);
    boolean orderOk = depset.getOrder().isCompatible(order);
    return typeOk && orderOk;
  }

  /**
   * Returns the type name of a value and possibly additional description.
   *
   * <p>For depsets, this includes its content type and order.
   */
  private static String describeType(Object value) {
    String typeName = EvalUtils.getDataTypeName(value, /*fullDetails=*/ true);
    if (value instanceof Depset) {
      return ((Depset) value).getOrder().getSkylarkName() + "-ordered " + typeName;
    } else {
      return typeName;
    }
  }

  private final Location location;
  // Verified on initialization to contain Artifact.
  private final Depset transitiveSources;
  private final boolean usesSharedLibraries;
  // Verified on initialization to contain String.
  private final Depset imports;
  private final boolean hasPy2OnlySources;
  private final boolean hasPy3OnlySources;

  private PyInfo(
      @Nullable Location location,
      Depset transitiveSources,
      boolean usesSharedLibraries,
      Depset imports,
      boolean hasPy2OnlySources,
      boolean hasPy3OnlySources) {
    Preconditions.checkArgument(
        depsetHasTypeAndCompatibleOrder(transitiveSources, Artifact.TYPE, Order.COMPILE_ORDER));
    // TODO(brandjon): PyCommon currently requires COMPILE_ORDER, but we'll probably want to change
    // that to NAIVE_LINK (preorder). In the meantime, order isn't an invariant of the provider
    // itself, so we use STABLE here to accept any order.
    Preconditions.checkArgument(
        depsetHasTypeAndCompatibleOrder(imports, SkylarkType.STRING, Order.STABLE_ORDER));
    this.location = location != null ? location : Location.BUILTIN;
    this.transitiveSources = transitiveSources;
    this.usesSharedLibraries = usesSharedLibraries;
    this.imports = imports;
    this.hasPy2OnlySources = hasPy2OnlySources;
    this.hasPy3OnlySources = hasPy3OnlySources;
  }

  @Override
  public PyInfoProvider getProvider() {
    return PROVIDER;
  }

  @Override
  public Location getCreationLoc() {
    return location;
  }

  @Override
  public boolean equals(Object other) {
    // PyInfo implements value equality, but note that it contains identity-equality fields
    // (depsets), so you generally shouldn't rely on equality comparisons.
    if (!(other instanceof PyInfo)) {
      return false;
    }
    PyInfo otherInfo = (PyInfo) other;
    return (this.transitiveSources.equals(otherInfo.transitiveSources)
        && this.usesSharedLibraries == otherInfo.usesSharedLibraries
        && this.imports.equals(otherInfo.imports)
        && this.hasPy2OnlySources == otherInfo.hasPy2OnlySources
        && this.hasPy3OnlySources == otherInfo.hasPy3OnlySources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        PyInfo.class,
        transitiveSources,
        usesSharedLibraries,
        imports,
        hasPy2OnlySources,
        hasPy3OnlySources);
  }

  @Override
  public Depset getTransitiveSources() {
    return transitiveSources;
  }

  public NestedSet<Artifact> getTransitiveSourcesSet() {
    try {
      return transitiveSources.getSet(Artifact.class);
    } catch (TypeException e) {
      throw new IllegalStateException(
          "'transitiveSources' depset was found to be invalid type " + imports.getContentType(), e);
    }
  }

  @Override
  public boolean getUsesSharedLibraries() {
    return usesSharedLibraries;
  }

  @Override
  public Depset getImports() {
    return imports;
  }

  public NestedSet<String> getImportsSet() {
    try {
      return imports.getSet(String.class);
    } catch (TypeException e) {
      throw new IllegalStateException(
          "'imports' depset was found to be invalid type " + imports.getContentType(), e);
    }
  }

  @Override
  public boolean getHasPy2OnlySources() {
    return hasPy2OnlySources;
  }

  @Override
  public boolean getHasPy3OnlySources() {
    return hasPy3OnlySources;
  }

  /** The singular PyInfo provider type object. */
  public static class PyInfoProvider extends BuiltinProvider<PyInfo>
      implements PyInfoApi.PyInfoProviderApi {

    private PyInfoProvider() {
      super(STARLARK_NAME, PyInfo.class);
    }

    @Override
    public PyInfo constructor(
        Depset transitiveSources,
        boolean usesSharedLibraries,
        Object importsUncast,
        boolean hasPy2OnlySources,
        boolean hasPy3OnlySources,
        Location loc)
        throws EvalException {
      Depset imports =
          importsUncast.equals(Starlark.UNBOUND)
              ? Depset.of(SkylarkType.STRING, NestedSetBuilder.emptySet(Order.COMPILE_ORDER))
              : (Depset) importsUncast;

      if (!depsetHasTypeAndCompatibleOrder(transitiveSources, Artifact.TYPE, Order.COMPILE_ORDER)) {
        throw new EvalException(
            loc,
            String.format(
                "'transitive_sources' field should be a postorder-compatible depset of Files (got "
                    + "a '%s')",
                describeType(transitiveSources)));
      }
      if (!depsetHasTypeAndCompatibleOrder(imports, SkylarkType.STRING, Order.STABLE_ORDER)) {
        throw new EvalException(
            loc,
            String.format(
                "'imports' field should be a depset of strings (got a '%s')",
                describeType(imports)));
      }
      // Validate depset parameters
      transitiveSources.getSetFromParam(Artifact.class, "transitive_sources");
      imports.getSetFromParam(String.class, "imports");

      return new PyInfo(
          loc,
          transitiveSources,
          usesSharedLibraries,
          imports,
          hasPy2OnlySources,
          hasPy3OnlySources);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link PyInfo}. */
  public static class Builder {
    Location location = null;
    NestedSet<Artifact> transitiveSources = NestedSetBuilder.emptySet(Order.COMPILE_ORDER);
    boolean usesSharedLibraries = false;
    NestedSet<String> imports = NestedSetBuilder.emptySet(Order.COMPILE_ORDER);
    boolean hasPy2OnlySources = false;
    boolean hasPy3OnlySources = false;

    // Use the static builder() method instead.
    private Builder() {}

    public Builder setLocation(Location location) {
      this.location = location;
      return this;
    }

    public Builder setTransitiveSources(NestedSet<Artifact> transitiveSources) {
      this.transitiveSources = transitiveSources;
      return this;
    }

    public Builder setUsesSharedLibraries(boolean usesSharedLibraries) {
      this.usesSharedLibraries = usesSharedLibraries;
      return this;
    }

    public Builder setImports(NestedSet<String> imports) {
      this.imports = imports;
      return this;
    }

    public Builder setHasPy2OnlySources(boolean hasPy2OnlySources) {
      this.hasPy2OnlySources = hasPy2OnlySources;
      return this;
    }

    public Builder setHasPy3OnlySources(boolean hasPy3OnlySources) {
      this.hasPy3OnlySources = hasPy3OnlySources;
      return this;
    }

    public PyInfo build() {
      Preconditions.checkNotNull(transitiveSources);
      return new PyInfo(
          location,
          Depset.of(Artifact.TYPE, transitiveSources),
          usesSharedLibraries,
          Depset.of(SkylarkType.STRING, imports),
          hasPy2OnlySources,
          hasPy3OnlySources);
    }
  }
}
