/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.scout.rt.jackson.dataobject.fixture;

import jakarta.annotation.Generated;

import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.dataobject.DoValue;
import org.eclipse.scout.rt.dataobject.TypeName;
import org.eclipse.scout.rt.dataobject.fixture.FixtureStringId;
import org.eclipse.scout.rt.dataobject.id.IId;

@TypeName("scout.TestTypedUntypedOuter")
public class TestTypedUntypedOuterDo extends DoEntity {

  public DoValue<FixtureStringId> stringId() {
    return doValue("stringId");
  }

  public DoValue<IId> iId() {
    return doValue("iId");
  }

  public DoValue<TestTypedUntypedInnerDo> inner() {
    return doValue("inner");
  }

  /* **************************************************************************
   * GENERATED CONVENIENCE METHODS
   * *************************************************************************/

  @Generated("DoConvenienceMethodsGenerator")
  public TestTypedUntypedOuterDo withStringId(FixtureStringId stringId) {
    stringId().set(stringId);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public FixtureStringId getStringId() {
    return stringId().get();
  }

  @Generated("DoConvenienceMethodsGenerator")
  public TestTypedUntypedOuterDo withIId(IId iId) {
    iId().set(iId);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public IId getIId() {
    return iId().get();
  }

  @Generated("DoConvenienceMethodsGenerator")
  public TestTypedUntypedOuterDo withInner(TestTypedUntypedInnerDo inner) {
    inner().set(inner);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public TestTypedUntypedInnerDo getInner() {
    return inner().get();
  }
}
